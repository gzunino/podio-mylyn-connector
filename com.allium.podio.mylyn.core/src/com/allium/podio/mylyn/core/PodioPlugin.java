package com.allium.podio.mylyn.core;

import java.net.MalformedURLException;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.mylyn.tasks.core.RepositoryStatus;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import com.podio.APIApplicationException;
import com.podio.APITransportException;

public class PodioPlugin implements BundleActivator {

	// The plug-in ID
	public static final String PLUGIN_ID = "com.allium.podio.mylyn.core"; //$NON-NLS-1$
	public static final String CONNECTOR_KIND = "com.allium.podio.mylyn"; //$NON-NLS-1$

	
	private static BundleContext context;
	private static PodioPlugin plugin;
	private PodioRepositoryConnector connector;

	public PodioRepositoryConnector getConnector() {
		return connector;
	}

	public void setConnector(PodioRepositoryConnector connector) {
		this.connector = connector;
	}

	static BundleContext getContext() {
		return context;
	}
	
	public static PodioPlugin getDefault() {
		return plugin;
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext bundleContext) throws Exception {
		PodioPlugin.context = bundleContext;
		plugin = this;
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext bundleContext) throws Exception {
		if (connector != null) {
			connector.stop();
			connector = null;
		}
		PodioPlugin.context = null;
	}

	public static IStatus toStatus(Throwable e, TaskRepository repository) {
		return toStatus(e, repository.getRepositoryUrl());
	}
	
	public static IStatus toStatus(Throwable e, String url) {
//		if (e instanceof TracLoginException) {
//			return RepositoryStatus.createLoginError(repository.getRepositoryUrl(), PLUGIN_ID);
//		} else if (e instanceof TracPermissionDeniedException) {
//			return TracUtil.createPermissionDeniedError(repository.getRepositoryUrl(), PLUGIN_ID);
//		} else if (e instanceof InvalidTicketException) {
//			return new RepositoryStatus(repository.getRepositoryUrl(), IStatus.ERROR, PLUGIN_ID,
//					RepositoryStatus.ERROR_IO, Messages.TracCorePlugin_the_SERVER_RETURNED_an_UNEXPECTED_RESOPNSE, e);
//		} else if (e instanceof TracMidAirCollisionException) {
//			return RepositoryStatus.createCollisionError(repository.getUrl(), TracCorePlugin.PLUGIN_ID);
		if (e instanceof APITransportException) {
			return new RepositoryStatus(url, IStatus.ERROR, PLUGIN_ID,
					RepositoryStatus.ERROR_IO, "the server returned an unexpected response", e);
		} else if (e instanceof APIApplicationException) {
			String message = e.getMessage();
			if (message == null) {
				message = "I/O error has occured";
			}
			return new RepositoryStatus(url, IStatus.ERROR, PLUGIN_ID,
					RepositoryStatus.ERROR_IO, message, e);
		} else if (e instanceof ClassCastException) {
			return new RepositoryStatus(IStatus.ERROR, PLUGIN_ID, RepositoryStatus.ERROR_IO,
					"Unexpected server response "+ e.getMessage(), e);
		} else if (e instanceof MalformedURLException) {
			return new RepositoryStatus(IStatus.ERROR, PLUGIN_ID, RepositoryStatus.ERROR_IO,
					"Repository URL is invalid", e);
		} else {
			return new RepositoryStatus(IStatus.ERROR, PLUGIN_ID, RepositoryStatus.ERROR_INTERNAL,
					"Unexpected error", e);
		}
	}

}
