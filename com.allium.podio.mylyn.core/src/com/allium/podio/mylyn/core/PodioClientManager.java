package com.allium.podio.mylyn.core;

import java.io.File;

import org.eclipse.mylyn.commons.net.AbstractWebLocation;
import org.eclipse.mylyn.tasks.core.RepositoryClientManager;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.TaskRepositoryLocationFactory;

public class PodioClientManager extends RepositoryClientManager<PodioClient, PodioClientData> {

	public PodioClientManager(final File cacheFile, final TaskRepositoryLocationFactory location) {
		super(cacheFile, PodioClientData.class);
		super.setLocationFactory(location);
	}

	@Override
	protected PodioClient createClient(final TaskRepository taskRepository,
			final PodioClientData data) {
		AbstractWebLocation location = getLocationFactory().createWebLocation(taskRepository);
		return new PodioClient(location);
	}

}
