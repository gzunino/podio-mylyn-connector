package com.allium.podio.mylyn.ui.wizard;

import java.net.MalformedURLException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.mylyn.internal.tasks.core.IRepositoryConstants;
import org.eclipse.mylyn.tasks.core.RepositoryStatus;
import org.eclipse.mylyn.tasks.core.RepositoryTemplate;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.ui.TasksUi;
import org.eclipse.mylyn.tasks.ui.wizards.AbstractRepositorySettingsPage;
import org.eclipse.swt.widgets.Composite;

import com.allium.podio.mylyn.core.PodioClient;
import com.allium.podio.mylyn.core.PodioPlugin;
import com.allium.podio.mylyn.core.PodioRepositoryConnector;
import com.allium.podio.mylyn.ui.PodioUIPlugin;
import com.podio.APIApplicationException;

@SuppressWarnings("restriction")
public class PodioTasksSettingsPage extends AbstractRepositorySettingsPage {

	public PodioTasksSettingsPage(final TaskRepository taskRepository) {
		super("Podio Repository Settings", "Example: http://www.podio.com", taskRepository);
		setNeedsAdvanced(false);
		setNeedsAnonymousLogin(false);
		setNeedsEncoding(false);
		setNeedsHttpAuth(false);
		setNeedsValidation(true);
		setNeedsValidateOnFinish(true);
	}

	@Override
	public void createControl(final Composite parent) {
		super.createControl(parent);
		addRepositoryTemplatesToServerUrlCombo();
	}

	@Override
	public void applyTo(final TaskRepository repository) {
		repository.setProperty(IRepositoryConstants.PROPERTY_CATEGORY,
				IRepositoryConstants.CATEGORY_TASKS);
		super.applyTo(repository);
	}

	@Override
	protected void repositoryTemplateSelected(final RepositoryTemplate template) {
		super.repositoryTemplateSelected(template);

		repositoryLabelEditor.setStringValue(template.label);
		setUrl(template.repositoryUrl);

		getContainer().updateButtons();
	}

	@Override
	public String getConnectorKind() {
		return PodioPlugin.CONNECTOR_KIND;
	}

	@Override
	protected void createAdditionalControls(final Composite parent) {
		// TODO Auto-generated method stub

	}

	@Override
	protected Validator getValidator(final TaskRepository repository) {
		return new PodioValidator(repository);
	}

	// public for testing
	public class PodioValidator extends Validator {

		private final String repositoryUrl;

		private final TaskRepository taskRepository;


		public PodioValidator(final TaskRepository taskRepository) {
			this.repositoryUrl = taskRepository.getRepositoryUrl();
			this.taskRepository = taskRepository;
		}

		@Override
		public void run(final IProgressMonitor monitor) throws CoreException {
			try {
				//validate(Provider.of(monitor));
				validate(monitor);
			} catch (MalformedURLException e) {
				throw new CoreException(RepositoryStatus.createStatus(repositoryUrl, IStatus.ERROR,
						PodioUIPlugin.PLUGIN_ID, INVALID_REPOSITORY_URL));
				//			} catch (TracLoginException e) {
				//				if (e.isNtlmAuthRequested()) {
				//					AuthenticationCredentials credentials = taskRepository.getCredentials(AuthenticationType.REPOSITORY);
				//					if (!credentials.getUserName().contains("\\")) { //$NON-NLS-1$
				//						throw new CoreException(RepositoryStatus.createStatus(repositoryUrl, IStatus.ERROR,
				//								TracUiPlugin.ID_PLUGIN,
				//								Messages.TracRepositorySettingsPage_NTLM_authentication_requested_Error));
				//					}
				//				}
				//				throw new CoreException(RepositoryStatus.createStatus(repositoryUrl, IStatus.ERROR,
				//						TracUiPlugin.ID_PLUGIN, INVALID_LOGIN));
				//			} catch (TracPermissionDeniedException e) {
				//				throw new CoreException(RepositoryStatus.createStatus(repositoryUrl, IStatus.ERROR,
				//						TracUiPlugin.ID_PLUGIN, "Insufficient permissions for selected access type.")); //$NON-NLS-1$
				//			} catch (TracException e) {
				//				String message = Messages.TracRepositorySettingsPage_No_Trac_repository_found_at_url;
				//				if (e.getMessage() != null) {
				//					message += ": " + e.getMessage(); //$NON-NLS-1$
				//				}
				//				throw new CoreException(RepositoryStatus.createStatus(repositoryUrl, IStatus.ERROR,
				//						TracUiPlugin.ID_PLUGIN, message));
			}
		}

		public void validate(final IProgressMonitor monitor) throws MalformedURLException {
			try {
				PodioRepositoryConnector connector = (PodioRepositoryConnector) TasksUi.getRepositoryManager()
						.getRepositoryConnector(PodioPlugin.CONNECTOR_KIND);
				
				PodioClient client = connector.getClientManager().createClient(taskRepository, null);
				
				String status = client.connect();
				setStatus(RepositoryStatus.createStatus(repositoryUrl, IStatus.INFO,
						PodioUIPlugin.PLUGIN_ID,
						"Authentication credentials are valid. User status: " + status));
			} catch (APIApplicationException e) {
				setStatus(RepositoryStatus.createStatus(repositoryUrl, IStatus.ERROR,
						PodioUIPlugin.PLUGIN_ID,
						INVALID_LOGIN + e.getDescription()));
			} catch (Exception e) {
				setStatus(RepositoryStatus.createStatus(repositoryUrl, IStatus.ERROR,
						PodioUIPlugin.PLUGIN_ID,
						INVALID_LOGIN + e.getMessage()));
			}

			//			AbstractWebLocation location = new TaskRepositoryLocationFactory().createWebLocation(taskRepository);

			//			TracRepositoryInfo info;
			//			if (version != null) {
			//				ITracClient client = TracClientFactory.createClient(location, version);
			//				info = client.validate(monitor);
			//			} else {
			// probe version: XML-RPC access first, then web
			// access
			//				try {
			//					version = Version.XML_RPC;
			//					ITracClient client = TracClientFactory.createClient(location, version);
			//					info = client.validate(monitor);
			//				} catch (TracException e) {
			//					try {
			//						version = Version.TRAC_0_9;
			//						ITracClient client = TracClientFactory.createClient(location, version);
			//						info = client.validate(monitor);
			//
			//						if (e instanceof TracPermissionDeniedException) {
			//							setStatus(RepositoryStatus.createStatus(repositoryUrl, IStatus.INFO,
			//									TracUiPlugin.ID_PLUGIN,
			//									Messages.TracRepositorySettingsPage_Authentication_credentials_are_valid));
			//						}
			//					} catch (TracLoginException e2) {
			//						throw e;
			//					} catch (TracException e2) {
			//						throw new TracException();
			//					}
			//				}
			//				result = version;
			//			}

			//			if (version == Version.XML_RPC //
			//					&& (info.isApiVersion(1, 0, 0) //
			//					|| (info.isApiVersionOrHigher(1, 0, 3) && info.isApiVersionOrSmaller(1, 0, 5)))) {
			//				setStatus(RepositoryStatus.createStatus(
			//						repositoryUrl,
			//						IStatus.INFO,
			//						TracUiPlugin.ID_PLUGIN,
			//						Messages.TracRepositorySettingsPage_Authentication_credentials_valid_Update_to_latest_XmlRpcPlugin_Warning));
			//			}

			//			MultiStatus status = new MultiStatus(TracUiPlugin.ID_PLUGIN, 0, NLS.bind("Validation results for {0}", //$NON-NLS-1$
			//					taskRepository.getRepositoryLabel()), null);
			//			status.add(new Status(IStatus.INFO, TracUiPlugin.ID_PLUGIN, NLS.bind("Version: {0}", info.toString()))); //$NON-NLS-1$
			//			status.add(new Status(IStatus.INFO, TracUiPlugin.ID_PLUGIN,
			//					NLS.bind("Access Type: {0}", version.toString()))); //$NON-NLS-1$
			//			StatusHandler.log(status);
		}

	}
}
