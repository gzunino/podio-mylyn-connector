package com.allium.podio.mylyn.ui.editor;

import org.eclipse.mylyn.commons.ui.CommonImages;
import org.eclipse.mylyn.tasks.ui.TasksUiImages;
import org.eclipse.mylyn.tasks.ui.TasksUiUtil;
import org.eclipse.mylyn.tasks.ui.editors.AbstractTaskEditorPageFactory;
import org.eclipse.mylyn.tasks.ui.editors.TaskEditor;
import org.eclipse.mylyn.tasks.ui.editors.TaskEditorInput;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.forms.editor.IFormPage;

import com.allium.podio.mylyn.core.PodioPlugin;

public class PodioTaskEditorPageFactory extends AbstractTaskEditorPageFactory {

	@Override
	public boolean canCreatePageFor(final TaskEditorInput input) {
		if (input.getTask().getConnectorKind().equals(PodioPlugin.CONNECTOR_KIND)) {
			return true;
		} else if (TasksUiUtil.isOutgoingNewTask(input.getTask(), PodioPlugin.CONNECTOR_KIND)) {
			return true;
		}
		return false;
	}

	@Override
	public Image getPageImage() {
		return CommonImages.getImage(TasksUiImages.REPOSITORY_SMALL);
	}

	@Override
	public String getPageText() {
		return "Podio";
	}

	@Override
	public IFormPage createPage(final TaskEditor parentEditor) {
		return new PodioTaskEditorPage(parentEditor);
	}

	@Override
	public int getPriority() {
		return PRIORITY_TASK;
	}

}
