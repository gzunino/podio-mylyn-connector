package com.allium.podio.mylyn.ui;

import org.eclipse.jface.wizard.IWizard;
import org.eclipse.mylyn.tasks.core.IRepositoryQuery;
import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.core.ITaskMapping;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.ui.AbstractRepositoryConnectorUi;
import org.eclipse.mylyn.tasks.ui.wizards.ITaskRepositoryPage;
import org.eclipse.mylyn.tasks.ui.wizards.RepositoryQueryWizard;

import com.allium.podio.mylyn.core.PodioPlugin;
import com.allium.podio.mylyn.ui.wizard.PodioQueryPage;
import com.allium.podio.mylyn.ui.wizard.PodioTasksSettingsPage;

public class PodioRepositoryConnectorUi extends AbstractRepositoryConnectorUi {

	public PodioRepositoryConnectorUi() {
	}

	@Override
	public String getConnectorKind() {
		return PodioPlugin.CONNECTOR_KIND;
	}
	
	@Override
	public String getTaskKindLabel(ITask task) {
		// TODO Auto-generated method stub
		return super.getTaskKindLabel(task);
	}

	@Override
	public ITaskRepositoryPage getSettingsPage(TaskRepository taskRepository) {
		return new PodioTasksSettingsPage(taskRepository);
	}

	@Override
	public IWizard getQueryWizard(TaskRepository taskRepository,
			IRepositoryQuery queryToEdit) {
		RepositoryQueryWizard wizard = new RepositoryQueryWizard(taskRepository);
		wizard.addPage(new PodioQueryPage(taskRepository, queryToEdit));
		return wizard;
	}

	@Override
	public IWizard getNewTaskWizard(TaskRepository taskRepository,
			ITaskMapping selection) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasSearchPage() {
		// TODO Auto-generated method stub
		return false;
	}

}
