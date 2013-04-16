package com.allium.podio.mylyn.core;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.mylyn.commons.net.Policy;
import org.eclipse.mylyn.tasks.core.AbstractRepositoryConnector;
import org.eclipse.mylyn.tasks.core.IRepositoryQuery;
import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.TaskRepositoryLocationFactory;
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
import com.podio.filter.MemberFieldFilterBy;
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
		fieldTypeMap.put(ApplicationFieldType.MEMBER, MemberFieldFilterBy.class);
		fieldTypeMap.put(ApplicationFieldType.CONTACT, MemberFieldFilterBy.class);
	}

	public static final String TASK_KEY_UPDATE_DATE = "UpdateDate"; //$NON-NLS-1$

	private final PodioTaskDataHandler taskDataHandler = new PodioTaskDataHandler(this);
	private final PodioAttachmentHandler attachmentHandler = new PodioAttachmentHandler(this);

	private TaskRepositoryLocationFactory taskRepositoryLocationFactory;

	private PodioClientManager clientManager;

	/**
	 * 
	 */
	public PodioRepositoryConnector() {
		PodioPlugin.getDefault().setConnector(this);
//		setTaskRepositoryLocationFactory(new TaskRepositoryLocationFactory());
	}

	public synchronized PodioClientManager getClientManager() {
		if (clientManager == null) {
			IPath stateLocation = Platform.getStateLocation(PodioPlugin.getContext().getBundle());
			IPath cacheFile = stateLocation.append("repositoryConfigurations"); //$NON-NLS-1$
			clientManager = new PodioClientManager(cacheFile.toFile(),
					taskRepositoryLocationFactory);
		}
		return clientManager;
	}

	public synchronized void setTaskRepositoryLocationFactory(
			final TaskRepositoryLocationFactory taskRepositoryLocationFactory) {
		this.taskRepositoryLocationFactory = taskRepositoryLocationFactory;
		if (this.clientManager != null) {
			clientManager.setLocationFactory(taskRepositoryLocationFactory);
		}
	}

	@Override
	public boolean canCreateNewTask(final TaskRepository repository) {
		return true;
	}

	@Override
	public boolean canCreateTaskFromKey(final TaskRepository repository) {
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
	public String getRepositoryUrlFromTaskUrl(final String taskFullUrl) {
		if (taskFullUrl != null) {
			int index = taskFullUrl.indexOf("com/");
			return (index != -1) ? taskFullUrl.substring(0, index + 3) : null;
		}
		return null;
	}

	@Override
	public TaskData getTaskData(final TaskRepository taskRepository, final String taskId,
			final IProgressMonitor monitor) throws CoreException {
		return taskDataHandler.getTaskData(taskRepository, taskId, monitor);
	}

	@Override
	public AbstractTaskDataHandler getTaskDataHandler() {
		return taskDataHandler;
	}

	@Override
	public String getTaskIdFromTaskUrl(final String taskFullUrl) {
		if (taskFullUrl == null) {
			return null;
		}
		int index = taskFullUrl.lastIndexOf("/");
		return index == -1 ? null : taskFullUrl.substring(index + 1);
	}

	@Override
	public String getTaskUrl(final String repositoryUrl, final String taskId) {
		return repositoryUrl + "/items/" + taskId;
	}

	@Override
	public boolean hasTaskChanged(final TaskRepository taskRepository, final ITask task,
			final TaskData taskData) {
		TaskMapper mapper = (TaskMapper) getTaskMapping(taskData);
		// if (taskData.isPartial()) {
		return mapper.hasChanges(task);
		// } else {
		// Date repositoryDate = mapper.getModificationDate();
		// Date localDate;
		// String localDateStr = task.getAttribute(TASK_KEY_UPDATE_DATE);
		// if (localDateStr != null) {
		// localDate = new Date(Long.parseLong(localDateStr));
		// if (repositoryDate != null && repositoryDate.equals(localDate)) {
		// return false;
		// }
		// }
		// return true;
		// }
	}

	@Override
	public IStatus performQuery(final TaskRepository repository,
			final IRepositoryQuery query, final TaskDataCollector collector,
			final ISynchronizationSession session, final IProgressMonitor monitor) {
		monitor.beginTask("Querying repository", IProgressMonitor.UNKNOWN);

		PodioClient client = getClientManager().getClient(repository);

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
	private FilterByValue<?>[] createFiltersForQuery(final IRepositoryQuery query,
			final PodioClient client, final int appId) {
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
	private void collectTasksData(final TaskRepository repository,
			final TaskDataCollector collector, final IProgressMonitor monitor,
			final PodioClient client, final int appId, final List<ItemBadge> items)
					throws CoreException {
		for (ItemBadge itemBadge : items) {
			Item item = client.getItem(itemBadge.getId(), false);
			TaskData taskData;
			taskData = taskDataHandler.createTaskDataFromItem(client, repository, appId, item,
					monitor);
			PodioTaskDataHandler.createDefaultAttribute(taskData, client, PodioAttribute.LINK);
			PodioTaskDataHandler.setAttrValue(taskData, null, PodioAttribute.LINK, itemBadge.getLink());
			collector.accept(taskData);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.mylyn.tasks.core.AbstractRepositoryConnector#updateRepositoryConfiguration(org.eclipse.mylyn.tasks.core.TaskRepository, org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public void updateRepositoryConfiguration(final TaskRepository taskRepository,
			final IProgressMonitor monitor) throws CoreException {
		// TODO Auto-generated method stub
	}

	@Override
	public void updateTaskFromTaskData(final TaskRepository taskRepository,
			final ITask task, final TaskData taskData) {
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
		Date date = task.getModificationDate();
		task.setAttribute(TASK_KEY_UPDATE_DATE,
				(date != null) ? PodioAttributeMapper.getStringFromDate(date) : null);
		//		}
	}

	@Override
	public PodioAttachmentHandler getTaskAttachmentHandler() {
		return attachmentHandler;
	}

	public static int getPodioId(final String taskId) {
		return Integer.parseInt(taskId);
	}

	@Override
	public void preSynchronization(final ISynchronizationSession session, IProgressMonitor monitor)
			throws CoreException {
		monitor = Policy.monitorFor(monitor);
		try {
			monitor.beginTask("Getting changed tasks", IProgressMonitor.UNKNOWN);

			if (!session.isFullSynchronization()) {
				return;
			}

			// there are no tasks in the task list, skip contacting the
			// repository
			if (session.getTasks().isEmpty()) {
				return;
			}

			TaskRepository repository = session.getTaskRepository();

			if (repository.getSynchronizationTimeStamp() == null
					|| repository.getSynchronizationTimeStamp().length() == 0) {
				for (ITask task : session.getTasks()) {
					session.markStale(task);
				}
				return;
			}

			Date since = new Date(0);
			try {
				since = PodioAttributeMapper.getDateFromString(repository
						.getSynchronizationTimeStamp());
			} catch (NumberFormatException e) {
			}

			try {
				PodioClient client = getClientManager().getClient(repository);

				Set<Integer> ids = client.getChangedItems(since);
				if (ids.isEmpty()) {
					// repository is unchanged
					session.setNeedsPerformQueries(false);
					return;
				}

				if (ids.size() == 1) {
					// getChangedTickets() is expected to always return at least
					// one ticket because
					// the repository synchronization timestamp is set to the
					// most recent modification date
					// Integer id = ids.iterator().next();
					// Date lastChanged = client.getTicketLastChanged(id,
					// monitor);
					// if (CoreUtil.TEST_MODE) {
					//						System.err.println(" preSynchronization(): since=" + since.getTime() + ", lastChanged=" + lastChanged.getTime()); //$NON-NLS-1$ //$NON-NLS-2$
					// }
					// if (since.equals(lastChanged)) {
					// repository didn't actually change
					session.setNeedsPerformQueries(false);
					return;
					// }
				}

				for (ITask task : session.getTasks()) {
					Integer id = getPodioId(task.getTaskId());
					if (ids.contains(id)) {
						session.markStale(task);
					}
				}
			} catch (OperationCanceledException e) {
				throw e;
			} catch (Exception e) {
				throw new CoreException(PodioPlugin.toStatus(e, repository));
			}
		} finally {
			monitor.done();
		}
	}

	@Override
	public void postSynchronization(final ISynchronizationSession event,
			final IProgressMonitor monitor) throws CoreException {
		try {
			monitor.beginTask("", 1); //$NON-NLS-1$
			if (event.isFullSynchronization() && event.getStatus() == null) {
				Date date = getSynchronizationTimestamp(event);
				if (date != null) {
					event.getTaskRepository().setSynchronizationTimeStamp(
							PodioAttributeMapper.getStringFromDate(date));
				}
			}
		} finally {
			monitor.done();
		}
	}

	private Date getSynchronizationTimestamp(final ISynchronizationSession event) {
		Date mostRecent = new Date(0);
		Date mostRecentTimeStamp = PodioAttributeMapper.getDateFromString(event.getTaskRepository()
				.getSynchronizationTimeStamp());
		for (ITask task : event.getChangedTasks()) {
			Date taskModifiedDate = task.getModificationDate();
			if (taskModifiedDate != null && taskModifiedDate.after(mostRecent)) {
				mostRecent = taskModifiedDate;
				mostRecentTimeStamp = task.getModificationDate();
			}
		}
		return mostRecentTimeStamp;
	}

	public void stop() {
		if (clientManager != null) {
			clientManager.writeCache();
		}
	}
}
