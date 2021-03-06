package com.allium.podio.mylyn.ui;

import org.eclipse.mylyn.tasks.ui.TaskRepositoryLocationUiFactory;
import org.eclipse.mylyn.tasks.ui.TasksUi;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.allium.podio.mylyn.core.PodioPlugin;
import com.allium.podio.mylyn.core.PodioRepositoryConnector;

/**
 * The activator class controls the plug-in life cycle
 */
public class PodioUIPlugin extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "com.allium.podio.mylyn.ui"; //$NON-NLS-1$

	// The shared instance
	private static PodioUIPlugin plugin;
	
	/**
	 * The constructor
	 */
	public PodioUIPlugin() {
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		
		PodioRepositoryConnector connector = (PodioRepositoryConnector) TasksUi.getRepositoryManager()
				.getRepositoryConnector(PodioPlugin.CONNECTOR_KIND);
		
		connector.setTaskRepositoryLocationFactory(new TaskRepositoryLocationUiFactory());
		TasksUi.getRepositoryManager().addListener(connector.getClientManager());
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static PodioUIPlugin getDefault() {
		return plugin;
	}

}
