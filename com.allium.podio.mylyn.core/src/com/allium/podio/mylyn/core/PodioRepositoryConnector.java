package com.allium.podio.mylyn.core;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.mylyn.tasks.core.AbstractRepositoryConnector;
import org.eclipse.mylyn.tasks.core.IRepositoryQuery;
import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.data.AbstractTaskDataHandler;
import org.eclipse.mylyn.tasks.core.data.TaskData;
import org.eclipse.mylyn.tasks.core.data.TaskDataCollector;
import org.eclipse.mylyn.tasks.core.data.TaskMapper;
import org.eclipse.mylyn.tasks.core.sync.ISynchronizationSession;

import com.podio.app.ApplicationField;
import com.podio.app.ApplicationFieldType;
import com.podio.filter.AppFieldFilterBy;
import com.podio.filter.FieldFilterBy;
import com.podio.filter.FilterByValue;
import com.podio.filter.StateFieldFilterBy;
import com.podio.item.Item;
import com.podio.item.ItemBadge;

/**
 * @author Guillermo Zunino
 *
 */
public class PodioRepositoryConnector extends AbstractRepositoryConnector {

	private static Map<ApplicationFieldType, Class<? extends FieldFilterBy<?>>> fieldTypeMap = new HashMap<ApplicationFieldType, Class<? extends FieldFilterBy<?>>>();

	static {
		fieldTypeMap.put(ApplicationFieldType.STATE, StateFieldFilterBy.class);
		fieldTypeMap.put(ApplicationFieldType.APP, AppFieldFilterBy.class);
	}
	
	private final PodioTaskDataHandler taskDataHandler = new PodioTaskDataHandler(this);
	private final PodioAttachmentHandler attachmentHandler = new PodioAttachmentHandler(this);
	
	/**
	 * 
	 */
	public PodioRepositoryConnector() {
	}

	@Override
	public boolean canCreateNewTask(TaskRepository repository) {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public boolean canCreateTaskFromKey(TaskRepository repository) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getConnectorKind() {
		return PodioPlugin.CONNECTOR_KIND;
	}

	@Override
	public String getLabel() {
		return "Podio Repository Connector";
	}

	@Override
	public String getRepositoryUrlFromTaskUrl(String taskFullUrl) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public TaskData getTaskData(TaskRepository taskRepository, String taskId,
			IProgressMonitor monitor) throws CoreException {
		return taskDataHandler.getTaskData(taskRepository, taskId, monitor);
	}

	@Override
	public AbstractTaskDataHandler getTaskDataHandler() {
		return taskDataHandler;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.mylyn.tasks.core.AbstractRepositoryConnector#getTaskIdFromTaskUrl(java.lang.String)
	 */
	@Override
	public String getTaskIdFromTaskUrl(String taskFullUrl) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.mylyn.tasks.core.AbstractRepositoryConnector#getTaskUrl(java.lang.String, java.lang.String)
	 */
	@Override
	public String getTaskUrl(String repositoryUrl, String taskId) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.mylyn.tasks.core.AbstractRepositoryConnector#hasTaskChanged(org.eclipse.mylyn.tasks.core.TaskRepository, org.eclipse.mylyn.tasks.core.ITask, org.eclipse.mylyn.tasks.core.data.TaskData)
	 */
	@Override
	public boolean hasTaskChanged(TaskRepository taskRepository, ITask task,
			TaskData taskData) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public IStatus performQuery(TaskRepository repository,
			IRepositoryQuery query, TaskDataCollector collector,
			ISynchronizationSession session, IProgressMonitor monitor) {
		monitor.beginTask("Querying repository", IProgressMonitor.UNKNOWN);

		PodioClient client = PodioClient.getClient(repository);
		
		int appId = Integer.valueOf(query.getAttribute("appId"));
		
		FilterByValue<?>[] allFilters = createFiltersForQuery(query, client,
				appId);
		List<ItemBadge> items = client.queryItems(Integer.valueOf(appId), allFilters);
		
		try {
			collectTasksData(repository, collector, monitor, client, appId, items);
		} catch (Throwable e) {
			return PodioPlugin.toStatus(e, repository);
		}
		monitor.done();
		return Status.OK_STATUS;
	}

	/**
	 * @param query
	 * @param client
	 * @param appId
	 * @return
	 */
	private FilterByValue<?>[] createFiltersForQuery(IRepositoryQuery query,
			PodioClient client, int appId) {
		List<ApplicationField> fields = client.getFields(appId);
		List<FilterByValue<?>> filters = new ArrayList<FilterByValue<?>>(fields.size());
		for (ApplicationField applicationField : fields) {
			String filterValue = query.getAttribute(applicationField.getId()+"");
			if (filterValue == null || "".equalsIgnoreCase(filterValue.trim()) ) {
				continue;
			}
			
			Class<? extends FieldFilterBy<?>> byClazz = fieldTypeMap.get(applicationField.getType());
			try {
				FieldFilterBy<?> by = byClazz.getConstructor(int.class).newInstance(applicationField.getId());
//				StateFieldFilterBy by = new StateFieldFilterBy(applicationField.getId());
				Object valueList = by.parse(filterValue);
				@SuppressWarnings({ "rawtypes", "unchecked" })
				FilterByValue filter = new FilterByValue(by, valueList);
				filters.add(filter);
			} catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
			}
		}
		
		FilterByValue<?>[] allFilters = filters.toArray(new FilterByValue<?>[filters.size()]);
		return allFilters;
	}

	/**
	 * @param repository
	 * @param collector
	 * @param monitor
	 * @param client
	 * @param appId
	 * @param items
	 * @throws CoreException
	 */
	private void collectTasksData(TaskRepository repository,
			TaskDataCollector collector, IProgressMonitor monitor,
			PodioClient client, int appId, List<ItemBadge> items)
			throws CoreException {
		for (ItemBadge itemBadge : items) {
			Item item = client.getItem(itemBadge.getId());
			TaskData taskData;
				taskData = taskDataHandler.createTaskDataFromTicket(client, repository, appId, item,
						monitor);
				PodioTaskDataHandler.createAttribute(taskData, client, PodioAttribute.LINK);
				PodioTaskDataHandler.setAttrValue(taskData, null, PodioAttribute.LINK, itemBadge.getLink());
				collector.accept(taskData);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.mylyn.tasks.core.AbstractRepositoryConnector#updateRepositoryConfiguration(org.eclipse.mylyn.tasks.core.TaskRepository, org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public void updateRepositoryConfiguration(TaskRepository taskRepository,
			IProgressMonitor monitor) throws CoreException {
		// TODO Auto-generated method stub

	}
	
	@Override
	public void updateTaskFromTaskData(TaskRepository taskRepository,
			ITask task, TaskData taskData) {
		TaskMapper mapper = (TaskMapper) getTaskMapping(taskData);
		mapper.applyTo(task);
//		String status = mapper.getStatus();
//		if (status != null) {
//			if (isCompleted(mapper.getStatus())) {
//				Date modificationDate = mapper.getModificationDate();
//				if (modificationDate == null) {
//					// web mode does not set a date
//					modificationDate = DEFAULT_COMPLETION_DATE;
//				}
//				task.setCompletionDate(modificationDate);
//			} else {
//				task.setCompletionDate(null);
//			}
//		}
		task.setUrl(taskData.getRoot().getAttribute(PodioAttribute.LINK.getPodioKey()).getValue());
//		if (!taskData.isPartial()) {
//			task.setAttribute(TASK_KEY_SUPPORTS_SUBTASKS, Boolean.toString(taskDataHandler.supportsSubtasks(taskData)));
//			Date date = task.getModificationDate();
//			task.setAttribute(TASK_KEY_UPDATE_DATE, (date != null) ? TracUtil.toTracTime(date) + "" : null); //$NON-NLS-1$
//		}
	}

	@Override
	public PodioAttachmentHandler getTaskAttachmentHandler() {
		return attachmentHandler;
	}

	public static int getPodioId(String taskId) {
		return Integer.parseInt(taskId);
	}
}
