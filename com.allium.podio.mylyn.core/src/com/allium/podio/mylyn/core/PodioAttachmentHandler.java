/*******************************************************************************
 * Copyright (c) 2006, 2010 Steffen Pingel and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Steffen Pingel - initial API and implementation
 *******************************************************************************/

package com.allium.podio.mylyn.core;

import java.io.InputStream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.mylyn.commons.net.Policy;
import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.core.RepositoryStatus;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.data.AbstractTaskAttachmentHandler;
import org.eclipse.mylyn.tasks.core.data.AbstractTaskAttachmentSource;
import org.eclipse.mylyn.tasks.core.data.TaskAttachmentMapper;
import org.eclipse.mylyn.tasks.core.data.TaskAttribute;
import org.eclipse.mylyn.tasks.core.data.UnsubmittedTaskAttachment;

/**
 * @author Guillermo Zunino
 */
public class PodioAttachmentHandler extends AbstractTaskAttachmentHandler {

	private final PodioRepositoryConnector connector;

	public PodioAttachmentHandler(final PodioRepositoryConnector connector) {
		this.connector = connector;
	}

	@Override
	public InputStream getContent(final TaskRepository repository, final ITask task, final TaskAttribute attachmentAttribute,
			IProgressMonitor monitor) throws CoreException {
		TaskAttachmentMapper mapper = TaskAttachmentMapper.createFrom(attachmentAttribute);
		String filename = mapper.getFileName();
		if (filename == null || filename.length() == 0) {
			throw new CoreException(new RepositoryStatus(repository.getRepositoryUrl(), IStatus.ERROR,
					PodioPlugin.PLUGIN_ID, RepositoryStatus.ERROR_REPOSITORY, "Attachment download from "
							+ repository.getRepositoryUrl() + " failed, missing attachment filename."));
		}

		monitor = Policy.monitorFor(monitor);
		try {
			monitor.beginTask("Downloading attachment", IProgressMonitor.UNKNOWN);
			PodioClient client = connector.getClientManager().getClient(repository);
			int fileId = Integer.parseInt(mapper.getAttachmentId());
			return client.getFile(fileId, filename);
		} catch (OperationCanceledException e) {
			throw e;
		} catch (Exception e) {
			throw new CoreException(PodioPlugin.toStatus(e, repository));
		} finally {
			monitor.done();
		}
	}

	@Override
	public void postContent(final TaskRepository repository, final ITask task, final AbstractTaskAttachmentSource source, final String comment,
			final TaskAttribute attachmentAttribute, IProgressMonitor monitor) throws CoreException {
		//		if (!TracRepositoryConnector.hasAttachmentSupport(repository, task)) {
		//			throw new CoreException(new RepositoryStatus(repository.getRepositoryUrl(), IStatus.INFO,
		//					TracCorePlugin.ID_PLUGIN, RepositoryStatus.ERROR_REPOSITORY,
		//					"Attachments are not supported by this repository access type")); //$NON-NLS-1$
		//		}

		UnsubmittedTaskAttachment attachment = new UnsubmittedTaskAttachment(source, attachmentAttribute);
		monitor = Policy.monitorFor(monitor);
		try {
			monitor.beginTask("Uploading attachment", IProgressMonitor.UNKNOWN);
			try {
				PodioClient client = connector.getClientManager().getClient(repository);
				int itemId = Integer.parseInt(task.getTaskId());

				int fileId = client.uploadFile(attachment.getFileName(),
						attachment.getDescription(), attachment.createInputStream(monitor));

				client.attachFile(itemId, fileId);

				//				client.putAttachmentData(itemId, attachment.getFileName(), attachment.getDescription(),
				//						attachment.createInputStream(monitor), monitor, attachment.getReplaceExisting());
				//				if (comment != null && comment.length() > 0) {
				//					TracTicket ticket = new TracTicket(itemId);
				//					client.updateTicket(ticket, comment, monitor);
				//				}
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
	public boolean canGetContent(final TaskRepository repository, final ITask task) {
		return true;
	}

	@Override
	public boolean canPostContent(final TaskRepository repository, final ITask task) {
		return true;
	}

}
