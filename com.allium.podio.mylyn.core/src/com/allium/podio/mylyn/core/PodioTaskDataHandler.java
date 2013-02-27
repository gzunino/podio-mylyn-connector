package com.allium.podio.mylyn.core;

import java.util.ArrayList;
import java.util.Collection;
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

import com.podio.app.ApplicationField;
import com.podio.app.ApplicationFieldType;
import com.podio.app.TextFieldSize;
import com.podio.comment.Comment;
import com.podio.file.File;
import com.podio.item.FieldValuesView;
import com.podio.item.Item;

public class PodioTaskDataHandler extends AbstractTaskDataHandler {

	private static final String PODIO_KEY = "podio_key";

	public PodioTaskDataHandler(PodioRepositoryConnector repository) {
	}

	public TaskData getTaskData(TaskRepository repository, String taskId, IProgressMonitor monitor)
			throws CoreException {
		monitor = Policy.monitorFor(monitor);
		try {
			monitor.beginTask("Task Download", IProgressMonitor.UNKNOWN); //$NON-NLS-1$
			return downloadTaskData(repository, Integer.parseInt(taskId), monitor);
		} finally {
			monitor.done();
		}
	}

	public TaskData downloadTaskData(TaskRepository repository, int taskId, IProgressMonitor monitor)
			throws CoreException {
		PodioClient client = PodioClient.getClient(repository);
		Item item;
		try {
			item = client.getItem(taskId);
		} catch (OperationCanceledException e) {
			throw e;
		} catch (Exception e) {
			throw new CoreException(PodioPlugin.toStatus(e, repository));
		}
		return createTaskDataFromTicket(client, repository, item.getApplication().getId(), item, monitor);
	}
	
	public TaskData createTaskDataFromTicket(PodioClient client, TaskRepository repository, int appId, Item item,
			IProgressMonitor monitor) throws CoreException {
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
	
	public static Set<TaskAttribute> updateTaskData(TaskRepository repository, TaskData data, PodioClient client, Item item) {
		Set<TaskAttribute> changedAttributes = new HashSet<TaskAttribute>();
		
		setAttrValue(data, changedAttributes, PodioAttribute.CREATED_BY, item.getInitialRevision().getCreatedBy().getName());
		setAttrValue(data, changedAttributes, PodioAttribute.CREATED_ON, item.getInitialRevision().getCreatedOn());
		setAttrValue(data, changedAttributes, PodioAttribute.CHANGED_BY, item.getCurrentRevision().getCreatedBy().getName());
		setAttrValue(data, changedAttributes, PodioAttribute.CHANGED_ON, item.getCurrentRevision().getCreatedOn());
//		setAttrValue(data, changedAttributes, PodioAttribute.DESCRIPTION, item.getTitle());
		setAttrValue(data, changedAttributes, PodioAttribute.SUMMARY, item.getTitle());
		if (item.getUserRatings() != null && !item.getUserRatings().isEmpty()) {
			setAttrValue(data, changedAttributes, PodioAttribute.RATING, item.getUserRatings().values().iterator().next().toString());
		}

		List<FieldValuesView> fields = item.getFields();
		for (FieldValuesView field : fields) {
			TaskAttribute taskAttribute = data.getRoot().getAttribute(field.getLabel());
			if (taskAttribute != null) {
				List<Map<String, ?>> values = field.getValues();
				System.out.println("Setting values for att: "+ field.getLabel());
				for (Map<String, ?> map : values) {
					System.out.println("Keys: "+ map.keySet());
					System.out.println("Vals: "+ map.values());
					Object value = map.get("value");
//					if (value instanceof String) {
					System.out.println("Value is of type" + value.getClass());
					taskAttribute.setValue(value.toString());
//					} else {
//					}
				}
				changedAttributes.add(taskAttribute);
			} else {
				StatusHandler.log(new Status(IStatus.WARNING, PodioPlugin.PLUGIN_ID, "TaskAttibute not found with key '"+field.getLabel()+"'"));
			}
			
//			taskAttribute = data.getRoot().getAttribute(PodioAttribute.DESCRIPTION.getPodioKey());
//			if (!taskAttribute.hasValue() && !field.getValues().isEmpty()) {
//				taskAttribute.setValue("DESCR: "+field.getValues().get(0).get("value").toString());
//			}
		}
		
		List<Comment> comments = item.getComments();
		if (comments != null) {
			int count = 1;
			for (Comment comment : comments) {
				TaskCommentMapper mapper = new TaskCommentMapper();
				mapper.setAuthor(repository.createPerson(comment.getCreatedBy().getId()+""));
				mapper.setCreationDate(comment.getCreatedOn().toDate());
				mapper.setText(comment.getValue());
				mapper.setCommentId(comment.getId()+"");
//				mapper.setUrl(comment.get);
				mapper.setNumber(count);
				
				TaskAttribute attribute = data.getRoot().createAttribute(TaskAttribute.PREFIX_COMMENT + count);
				mapper.applyTo(attribute);
				count++;
			}
		}
	
		List<File> files = item.getFiles();
		if (files != null) {
			int count = 0;
			for (File file : files) {
				TaskAttachmentMapper mapper = new TaskAttachmentMapper();
				mapper.setAuthor(repository.createPerson(file.getCreatedBy().getId()+""));
				mapper.setDescription(file.getDescription());
				mapper.setFileName(file.getName());
				mapper.setAttachmentId(file.getId()+"");
				mapper.setLength(file.getSize());
				mapper.setReplaceExisting(file.getReplaces() != null && !file.getReplaces().isEmpty());
				mapper.setContentType(file.getMimetype().getPrimaryType());
				mapper.setCreationDate(file.getCreatedOn().toDate());
				mapper.setUrl(file.getContext().toURLFragment());
				mapper.setComment(file.getContext().getTitle());

				TaskAttribute attribute = data.getRoot().createAttribute(TaskAttribute.PREFIX_ATTACHMENT + ++count);
				mapper.applyTo(attribute);
			}
		}
		
//		TracAction[] actions = item.getActions();
//		if (actions != null) {
//			 add actions and set first as default
//			for (TracAction action : actions) {
//				addOperation(repository, data, item, action, action == actions[0]);
//			}
//		}
		return changedAttributes;
	}

	/**
	 * @param data
	 * @param item
	 * @param changedAttributes
	 * @param podioAttr 
	 * @param attrValue 
	 */
	public static void setAttrValue(TaskData data, Set<TaskAttribute> changedAttributes, 
			PodioAttribute podioAttr, Object attrValue) {
		TaskAttribute taskAttribute = data.getRoot().getAttribute(podioAttr.getPodioKey());
		if (taskAttribute != null) {
			taskAttribute.setValue(attrValue != null ? attrValue.toString() : "");
			if (changedAttributes != null) {
				changedAttributes.add(taskAttribute);
			}
		} else {
			StatusHandler.log(new Status(IStatus.ERROR, PodioPlugin.PLUGIN_ID, "TaskAttibute not found with key '"+podioAttr.getPodioKey()+"'"));
		}
	}

	public static void createDefaultAttributes(TaskData data, PodioClient client, int appId, boolean existingTask) {
		createAttribute(data, client, PodioAttribute.SUMMARY);
		createAttribute(data, client, PodioAttribute.DESCRIPTION);
		if (existingTask) {
			createAttribute(data, client, PodioAttribute.CREATED_BY);
			createAttribute(data, client, PodioAttribute.CREATED_ON);
			createAttribute(data, client, PodioAttribute.CHANGED_BY);
			createAttribute(data, client, PodioAttribute.CHANGED_ON);
		}
		createAttribute(data, client, PodioAttribute.RATING);
		createAttribute(data, client, PodioAttribute.TAGS);
		createAttribute(data, client, PodioAttribute.TYPE);
		// custom fields
		List<ApplicationField> fields = client.getFields(appId);
		for (ApplicationField field : fields) {
			createAttribute(data, field);
		}
		// operations
		data.getRoot().createAttribute(TaskAttribute.OPERATION).getMetaData().setType(TaskAttribute.TYPE_OPERATION);
	}
	
	public static TaskAttribute createAttribute(TaskData data, PodioClient client, PodioAttribute podioAttribute) {
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
	
	private static TaskAttribute createAttribute(TaskData data, ApplicationField field) {
		TaskAttribute attr = data.getRoot().createAttribute(field.getConfiguration().getLabel());
		TaskAttributeMetaData metaData = attr.getMetaData();
		metaData.defaults();
		metaData.setLabel(field.getConfiguration().getLabel() + ":"); //$NON-NLS-1$
		metaData.setKind(TaskAttribute.KIND_DEFAULT);
		metaData.setReadOnly(false);
		metaData.putValue(PODIO_KEY, field.getId()+"");
		if (field.getType() == ApplicationFieldType.CATEGORY || field.getType() == ApplicationFieldType.STATE) {
			metaData.setType(TaskAttribute.TYPE_MULTI_SELECT);
			List<String> allowValues = field.getConfiguration().getSettings().getAllowedValues();
			for (String val : allowValues) {
				attr.putOption(val, val);
			}
		} else if (field.getType() == ApplicationFieldType.APP) {
			metaData.setType(TaskAttribute.TYPE_TASK_DEPENDENCY);
			List<Integer> allowValues = field.getConfiguration().getSettings().getReferenceableTypes();
			for (Integer val : allowValues) {
				attr.putOption(val.toString(), val.toString());
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
	
	private void removeEmptySingleSelectAttributes(TaskData taskData) {
		List<TaskAttribute> attributes = new ArrayList<TaskAttribute>(taskData.getRoot().getAttributes().values());
		for (TaskAttribute attribute : attributes) {
			if (TaskAttribute.TYPE_SINGLE_SELECT.equals(attribute.getMetaData().getType())
					&& attribute.getValue().length() == 0 && attribute.getOptions().isEmpty()) {
				taskData.getRoot().removeAttribute(attribute.getId());
			}
		}
	}
	
	@Override
	public RepositoryResponse postTaskData(TaskRepository repository, TaskData taskData,
			Set<TaskAttribute> oldAttributes, IProgressMonitor monitor) throws CoreException {
		try {
			Item item = PodioTaskDataHandler.getPodioItem(repository, taskData);
			PodioClient server = PodioClient.getClient(repository);
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
			throw new CoreException(PodioPlugin.toStatus(e, repository));
		}
	}

	public static Item  getPodioItem(TaskRepository repository, TaskData data) throws CoreException {
		Item ticket = new Item();
		if (!data.isNew()) {
			ticket.setId(PodioRepositoryConnector.getPodioId(data.getTaskId()));
		}
		
		Collection<TaskAttribute> attributes = data.getRoot().getAttributes().values();
		for (TaskAttribute attribute : attributes) {
//			if (TracAttributeMapper.isInternalAttribute(attribute)
//					|| TracAttribute.RESOLUTION.getTracKey().equals(attribute.getId())) {
				// ignore internal attributes, resolution is set through operations
			/*} else */if (!attribute.getMetaData().isReadOnly() /*|| Key.TOKEN.getKey().equals(attribute.getId())*/) {
//				ticket.putValue(attribute.getId(), attribute.getValue());
			}
		}
		
//		ticket.setLastChanged(lastChanged);
		
		return ticket;
	}

	@Override
	public boolean initializeTaskData(TaskRepository repository, TaskData data,
			ITaskMapping initializationData, IProgressMonitor monitor)
			throws CoreException {
		monitor = Policy.monitorFor(monitor);
		try {
			PodioClient client = PodioClient.getClient(repository);			
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
	public TaskAttributeMapper getAttributeMapper(TaskRepository repository) {
		PodioClient client = PodioClient.getClient(repository);
		return new PodioAttributeMapper(repository, client);
	}

}
