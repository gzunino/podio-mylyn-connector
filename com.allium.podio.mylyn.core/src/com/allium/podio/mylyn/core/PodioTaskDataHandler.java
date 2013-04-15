package com.allium.podio.mylyn.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.mylyn.commons.core.StatusHandler;
import org.eclipse.mylyn.commons.net.Policy;
import org.eclipse.mylyn.tasks.core.ITaskMapping;
import org.eclipse.mylyn.tasks.core.RepositoryResponse;
import org.eclipse.mylyn.tasks.core.RepositoryResponse.ResponseKind;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.data.AbstractTaskDataHandler;
import org.eclipse.mylyn.tasks.core.data.TaskAttachmentMapper;
import org.eclipse.mylyn.tasks.core.data.TaskAttribute;
import org.eclipse.mylyn.tasks.core.data.TaskAttributeMapper;
import org.eclipse.mylyn.tasks.core.data.TaskAttributeMetaData;
import org.eclipse.mylyn.tasks.core.data.TaskCommentMapper;
import org.eclipse.mylyn.tasks.core.data.TaskData;
import org.joda.time.DateTime;

import com.podio.app.ApplicationField;
import com.podio.app.ApplicationFieldType;
import com.podio.app.TextFieldSize;
import com.podio.comment.Comment;
import com.podio.file.File;
import com.podio.item.FieldValuesView;
import com.podio.item.Item;

public class PodioTaskDataHandler extends AbstractTaskDataHandler {

	private static final String PODIO_KEY = "podio.key";
	private static final String PODIO_TYPE = "podio.type";
	private static final String ATTR_PREFIX = "podio.field.";
	private PodioRepositoryConnector connector;

	public PodioTaskDataHandler(final PodioRepositoryConnector repository) {
		this.connector = repository;
	}

	public TaskData getTaskData(final TaskRepository repository, final String taskId, IProgressMonitor monitor)
			throws CoreException {
		monitor = Policy.monitorFor(monitor);
		try {
			monitor.beginTask("Task Download", IProgressMonitor.UNKNOWN); //$NON-NLS-1$
			return downloadTaskData(repository, Integer.parseInt(taskId), monitor);
		} finally {
			monitor.done();
		}
	}

	public TaskData downloadTaskData(final TaskRepository repository, final int taskId, final IProgressMonitor monitor)
			throws CoreException {
		PodioClient client = connector.getClientManager().getClient(repository);
		try {
			Item item = client.getItem(taskId, true);
			return createTaskDataFromItem(client, repository, item.getApplication().getId(), item,
					monitor);
		} catch (OperationCanceledException e) {
			throw e;
		} catch (Exception e) {
			throw new CoreException(PodioPlugin.toStatus(e, repository));
		}
	}

	public TaskData createTaskDataFromItem(final PodioClient client, final TaskRepository repository, final int appId, final Item item,
			final IProgressMonitor monitor) throws CoreException {
		TaskData taskData = new TaskData(getAttributeMapper(repository), PodioPlugin.CONNECTOR_KIND,
				repository.getRepositoryUrl(), item.getId() + ""); //$NON-NLS-1$
		try {
			createDefaultAttributes(taskData, client, appId, true);
			updateTaskData(repository, taskData, client, item);
			removeEmptySingleSelectAttributes(taskData);
			return taskData;
		} catch (OperationCanceledException e) {
			throw e;
		} catch (Exception e) {
			throw new CoreException(PodioPlugin.toStatus(e, repository));
		}
	}

	public static Set<TaskAttribute> updateTaskData(final TaskRepository repository, final TaskData data, final PodioClient client, final Item item) {
		Set<TaskAttribute> changedAttrs = new HashSet<TaskAttribute>();

		setAttrValue(data, changedAttrs, PodioAttribute.CREATED_BY, item.getInitialRevision().getCreatedBy().getName());
		setAttrValue(data, changedAttrs, PodioAttribute.CREATED_ON, item.getInitialRevision().getCreatedOn());
		setAttrValue(data, changedAttrs, PodioAttribute.CHANGED_BY, item.getCurrentRevision().getCreatedBy().getName());
		setAttrValue(data, changedAttrs, PodioAttribute.CHANGED_ON, item.getCurrentRevision().getCreatedOn());
		//		setAttrValue(data, changedAttributes, PodioAttribute.DESCRIPTION, item.getTitle());
		setAttrValue(data, changedAttrs, PodioAttribute.TITLE, item.getTitle());
		if (item.getUserRatings() != null && !item.getUserRatings().isEmpty()) {
			// TODO
			setAttrValue(data, changedAttrs, PodioAttribute.RATING, item.getUserRatings().values().iterator().next().toString());
		}

		List<FieldValuesView> fields = item.getFields();
		for (FieldValuesView field : fields) {
			TaskAttribute taskAttribute = data.getRoot().getAttribute(
					ATTR_PREFIX + field.getLabel());
			if (taskAttribute != null) {
				List<Map<String, ?>> values = field.getValues();
				System.out.println("Setting values for ATT: " + field.getLabel());
				for (Map<String, ?> map : values) {
					System.out.println("Keys: " + map.keySet() + " - Vals: " + map.values());
					Object value = map.get("value");
					if (value instanceof String) {
						taskAttribute.addValue(value.toString());
					} else if (value instanceof Map<?, ?>) {
						Map<?, ?> mapValue = (Map<?, ?>) value;
						System.out.println("Value of type " + value.getClass() + ": Keys: "
								+ mapValue.keySet() + " - Vals: " + mapValue.values());
						if (field.getType() == ApplicationFieldType.APP) {
							taskAttribute.addValue(mapValue.get("item_id").toString());
						}
						if (field.getType() == ApplicationFieldType.CATEGORY) {
							taskAttribute.addValue(mapValue.get("text").toString());
						}
					} else {
						throw new IllegalArgumentException("Unexpected value of type "
								+ value.getClass() + " for attribute " + field.getLabel());
					}
				}
				changedAttrs.add(taskAttribute);
			} else {
				StatusHandler.log(new Status(IStatus.WARNING, PodioPlugin.PLUGIN_ID, "TaskAttibute not found with key '"+field.getLabel()+"'"));
			}

			//			taskAttribute = data.getRoot().getAttribute(PodioAttribute.DESCRIPTION.getPodioKey());
			//			if (!taskAttribute.hasValue() && !field.getValues().isEmpty()) {
			//				taskAttribute.setValue("DESCR: "+field.getValues().get(0).get("value").toString());
			//			}
		}

		List<File> files = new ArrayList<File>();

		List<Comment> comments = item.getComments();
		if (comments != null) {
			int count = 1;
			for (Comment comment : comments) {
				TaskCommentMapper mapper = new TaskCommentMapper();
				mapper.setAuthor(repository.createPerson(comment.getCreatedBy().getId()+""));
				mapper.setCreationDate(comment.getCreatedOn().toDate());
				mapper.setText(comment.getValue());
				mapper.setCommentId(comment.getId()+"");
				mapper.setNumber(count);

				files.addAll(comment.getFiles());

				TaskAttribute attribute = data.getRoot().createAttribute(TaskAttribute.PREFIX_COMMENT + count);
				mapper.applyTo(attribute);
				count++;
			}
		}

		files.addAll(item.getFiles());
		int count = 0;
		for (File file : files) {
			TaskAttachmentMapper mapper = new TaskAttachmentMapper();
			mapper.setAuthor(repository.createPerson(file.getCreatedBy().getId() + ""));
			mapper.setDescription(file.getDescription());
			mapper.setFileName(file.getName());
			mapper.setAttachmentId(file.getId() + "");
			mapper.setLength(file.getSize());
			mapper.setReplaceExisting(file.getReplaces() != null && !file.getReplaces().isEmpty());
			mapper.setContentType(file.getMimetype().getPrimaryType());
			mapper.setCreationDate(file.getCreatedOn().toDate());
			if (file.getContext() != null) {
				mapper.setUrl(file.getContext().toURLFragment());
				mapper.setComment(file.getContext().getTitle() + " " + file.getContext().toString());
			}

			TaskAttribute attribute = data.getRoot().createAttribute(
					TaskAttribute.PREFIX_ATTACHMENT + ++count);
			mapper.applyTo(attribute);
		}

		//		TracAction[] actions = item.getActions();
		//		if (actions != null) {
		//			 add actions and set first as default
		//			for (TracAction action : actions) {
		//				addOperation(repository, data, item, action, action == actions[0]);
		//			}
		//		}
		return changedAttrs;
	}

	/**
	 * @param data
	 * @param item
	 * @param changedAttributes
	 * @param podioAttr
	 * @param attrValue
	 */
	public static void setAttrValue(final TaskData data, final Set<TaskAttribute> changedAttributes,
			final PodioAttribute podioAttr, final Object attrValue) {
		TaskAttribute taskAttribute = data.getRoot().getAttribute(podioAttr.getPodioKey());
		if (taskAttribute != null) {
			if (attrValue instanceof Date) {
				taskAttribute.setValue(attrValue != null ? PodioAttributeMapper
						.getStringFromDate((Date) attrValue) : "");
			} else if (attrValue instanceof DateTime) {
				taskAttribute.setValue(attrValue != null ? PodioAttributeMapper
						.getStringFromDate(((DateTime) attrValue).toDate()) : "");
			} else {
				taskAttribute.setValue(attrValue != null ? attrValue.toString() : "");
			}
			if (changedAttributes != null) {
				changedAttributes.add(taskAttribute);
			}
		} else {
			StatusHandler.log(new Status(IStatus.ERROR, PodioPlugin.PLUGIN_ID, "TaskAttibute not found with key '"+podioAttr.getPodioKey()+"'"));
		}
	}

	public static void createDefaultAttributes(final TaskData data, final PodioClient client, final int appId, final boolean existingTask) {
		createDefaultAttribute(data, client, PodioAttribute.TITLE);
		createDefaultAttribute(data, client, PodioAttribute.DESCRIPTION);
		if (existingTask) {
			createDefaultAttribute(data, client, PodioAttribute.CREATED_BY);
			createDefaultAttribute(data, client, PodioAttribute.CREATED_ON);
			createDefaultAttribute(data, client, PodioAttribute.CHANGED_BY);
			createDefaultAttribute(data, client, PodioAttribute.CHANGED_ON);
		}
		createDefaultAttribute(data, client, PodioAttribute.RATING);
		createDefaultAttribute(data, client, PodioAttribute.TAGS);
		createDefaultAttribute(data, client, PodioAttribute.TYPE);
		createDefaultAttribute(data, client, PodioAttribute.LINK);
		// custom fields for app
		List<ApplicationField> fields = client.getFields(appId);
		for (ApplicationField field : fields) {
			createAppAttribute(data, field);
		}
		// operations
		data.getRoot().createAttribute(TaskAttribute.OPERATION).getMetaData().setType(TaskAttribute.TYPE_OPERATION);
	}

	public static TaskAttribute createDefaultAttribute(final TaskData data,
			final PodioClient client, final PodioAttribute podioAttribute) {
		TaskAttribute attr = data.getRoot().createAttribute(podioAttribute.getPodioKey());
		TaskAttributeMetaData metaData = attr.getMetaData();
		metaData.setType(podioAttribute.getType());
		metaData.setKind(podioAttribute.getKind());
		metaData.setLabel(podioAttribute.toString());
		metaData.setReadOnly(podioAttribute.isReadOnly());
		metaData.putValue(PODIO_KEY, podioAttribute.getPodioKey());
		if (client != null) {
			//			TracTicketField field = client.getTicketFieldByName(podioAttribute.getTracKey());
			//			Map<String, String> values = PodioAttributeMapper.getRepositoryOptions(client, attr.getId());
			//			if (values != null && values.size() > 0) {
			//				boolean setDefault = field == null || !field.isOptional();
			//				for (Entry<String, String> value : values.entrySet()) {
			//					attr.putOption(value.getKey(), value.getValue());
			// set first value as default, may get overwritten below
			//					if (setDefault) {
			//						attr.setValue(value.getKey());
			//					}
			//					setDefault = false;
			//				}
			if (TaskAttribute.TYPE_SINGLE_SELECT.equals(podioAttribute.getType())) {
				attr.getMetaData().setReadOnly(true);
			}
		}
		return attr;
	}

	private static TaskAttribute createAppAttribute(final TaskData data,
			final ApplicationField field) {
		TaskAttribute attr = data.getRoot().createAttribute(
				ATTR_PREFIX + field.getConfiguration().getLabel());
		TaskAttributeMetaData metaData = attr.getMetaData();
		metaData.defaults();
		metaData.setLabel(field.getConfiguration().getLabel() + ":"); //$NON-NLS-1$
		metaData.setKind(TaskAttribute.KIND_DEFAULT);
		metaData.setReadOnly(false);
		metaData.putValue(PODIO_KEY, field.getId()+"");
		metaData.putValue(PODIO_TYPE, field.getType().name());
		if (field.getType() == ApplicationFieldType.CATEGORY || field.getType() == ApplicationFieldType.STATE) {
			metaData.setType(TaskAttribute.TYPE_MULTI_SELECT);
			List<String> allowValues = field.getConfiguration().getSettings().getAllowedValues();
			if (allowValues != null) {
				for (String val : allowValues) {
					attr.putOption(val, val);
				}
			}
		} else if (field.getType() == ApplicationFieldType.APP) {
			metaData.setType(TaskAttribute.TYPE_TASK_DEPENDENCY);
			List<Integer> allowValues = field.getConfiguration().getSettings().getReferenceableTypes();
			for (Integer val : allowValues) {
				attr.putOption("APP REF" + val.toString(), val.toString());
			}
		} else if (field.getType() == ApplicationFieldType.CALCULATION) {
			metaData.setType(TaskAttribute.TYPE_DOUBLE);
			metaData.setReadOnly(true);
		} else if (field.getType() == ApplicationFieldType.CONTACT || field.getType() == ApplicationFieldType.MEMBER) {
			metaData.setType(TaskAttribute.TYPE_PERSON);
		} else if (field.getType() == ApplicationFieldType.DATE) {
			metaData.setType(TaskAttribute.TYPE_DATE);
		} else if (field.getType() == ApplicationFieldType.DURATION) {
			metaData.setType(TaskAttribute.TYPE_DATETIME);
		} else if (field.getType() == ApplicationFieldType.FILE) {
			metaData.setType(TaskAttribute.TYPE_ATTACHMENT);
		} else if (field.getType() == ApplicationFieldType.MONEY || field.getType() == ApplicationFieldType.NUMBER) {
			metaData.setType(TaskAttribute.TYPE_DOUBLE);
		} else if (field.getType() == ApplicationFieldType.PROGRESS) {
			metaData.setType(TaskAttribute.TYPE_INTEGER);
		} else if (field.getType() == ApplicationFieldType.TEXT
				&& field.getConfiguration().getSettings().getSize() == TextFieldSize.LARGE) {
			metaData.setType(TaskAttribute.TYPE_LONG_TEXT);
		} else {
			metaData.setType(TaskAttribute.TYPE_SHORT_TEXT);
		}
		return attr;
	}

	private void removeEmptySingleSelectAttributes(final TaskData taskData) {
		List<TaskAttribute> attributes = new ArrayList<TaskAttribute>(taskData.getRoot().getAttributes().values());
		for (TaskAttribute attribute : attributes) {
			if (TaskAttribute.TYPE_SINGLE_SELECT.equals(attribute.getMetaData().getType())
					&& attribute.getValue().length() == 0 && attribute.getOptions().isEmpty()) {
				// taskData.getRoot().removeAttribute(attribute.getId());
			}
		}
	}

	@Override
	public RepositoryResponse postTaskData(final TaskRepository repository, final TaskData taskData,
			final Set<TaskAttribute> oldAttributes, final IProgressMonitor monitor) throws CoreException {
		try {
			Item item = PodioTaskDataHandler.getPodioItem(repository, taskData);
			PodioClient server = connector.getClientManager().getClient(repository);
			if (taskData.isNew()) {
				int id = server.createItem(item);
				return new RepositoryResponse(ResponseKind.TASK_CREATED, id + ""); //$NON-NLS-1$
			} else {
				//				String newComment = ""; //$NON-NLS-1$
				//				TaskAttribute newCommentAttribute = taskData.getRoot().getMappedAttribute(TaskAttribute.COMMENT_NEW);
				//				if (newCommentAttribute != null) {
				//					newComment = newCommentAttribute.getValue();
				//				}
				server.updateItem(item);
				return new RepositoryResponse(ResponseKind.TASK_UPDATED, item.getId() + ""); //$NON-NLS-1$
			}
		} catch (OperationCanceledException e) {
			throw e;
		} catch (Exception e) {
			// TODO catch TracException
			e.printStackTrace();
			throw new CoreException(PodioPlugin.toStatus(e, repository));
		}
	}

	public static Item getPodioItem(final TaskRepository repository, final TaskData data)
			throws CoreException {
		Item ticket = new Item();
		if (!data.isNew()) {
			ticket.setId(PodioRepositoryConnector.getPodioId(data.getTaskId()));
		}

		Collection<TaskAttribute> attributes = data.getRoot().getAttributes().values();
		for (TaskAttribute attribute : attributes) {
			//			if (TracAttributeMapper.isInternalAttribute(attribute)
			//					|| TracAttribute.RESOLUTION.getTracKey().equals(attribute.getId())) {
			// ignore internal attributes, resolution is set through operations
			/* } else */
			if (!attribute.getMetaData().isReadOnly() /*
			 * ||
			 * Key.TOKEN.getKey().equals
			 * (attribute.getId())
			 */) {
				//				ticket.putValue(attribute.getId(), attribute.getValue());
			}
		}

		//		ticket.setLastChanged(lastChanged);

		return ticket;
	}

	@Override
	public boolean initializeTaskData(final TaskRepository repository, final TaskData data,
			final ITaskMapping initializationData, IProgressMonitor monitor)
					throws CoreException {
		monitor = Policy.monitorFor(monitor);
		try {
			PodioClient client = connector.getClientManager().getClient(repository);
			createDefaultAttributes(data, client, 0, false);
			removeEmptySingleSelectAttributes(data);
			return true;
		} catch (OperationCanceledException e) {
			throw e;
		} catch (Exception e) {
			// TODO catch TracException
			throw new CoreException(PodioPlugin.toStatus(e, repository));
		}
	}

	@Override
	public boolean initializeSubTaskData(final TaskRepository repository, final TaskData taskData,
			final TaskData parentTaskData, final IProgressMonitor monitor) throws CoreException {
		// TODO Auto-generated method stub
		return super.initializeSubTaskData(repository, taskData, parentTaskData, monitor);
	}

	@Override
	public TaskAttributeMapper getAttributeMapper(final TaskRepository repository) {
		PodioClient client = connector.getClientManager().getClient(repository);
		return new PodioAttributeMapper(repository, client);
	}

}
