/**
 * 
 */
package com.allium.podio.mylyn.ui;

import org.eclipse.mylyn.tasks.ui.TaskRepositoryLocationUiFactory;

/**
 * @author Guillermo Zunino
 *
 */
public class PodioRepositoryConnector extends com.allium.podio.mylyn.core.PodioRepositoryConnector {

	public PodioRepositoryConnector() {
		setTaskRepositoryLocationFactory(new TaskRepositoryLocationUiFactory());
	}


}
