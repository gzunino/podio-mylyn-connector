package com.allium.podio.mylyn.ui.editor;

import java.util.Set;

import org.eclipse.mylyn.tasks.core.data.TaskAttribute;
import org.eclipse.mylyn.tasks.ui.editors.AbstractAttributeEditor;
import org.eclipse.mylyn.tasks.ui.editors.AbstractTaskEditorPage;
import org.eclipse.mylyn.tasks.ui.editors.AttributeEditorFactory;
import org.eclipse.mylyn.tasks.ui.editors.TaskEditor;
import org.eclipse.mylyn.tasks.ui.editors.TaskEditorPartDescriptor;

import com.allium.podio.mylyn.core.PodioPlugin;

/**
 * @author Guillermo Zunino
 *
 */
public class PodioTaskEditorPage extends AbstractTaskEditorPage {

	/**
	 * @param editor
	 * @param connectorKind
	 */
	public PodioTaskEditorPage(final TaskEditor editor) {
		super(editor, PodioPlugin.CONNECTOR_KIND);
		setNeedsAddToCategory(true);
		setNeedsPrivateSection(true);
		// setNeedsSubmitButton(true);
		setNeedsSubmit(true);
	}

	@Override
	protected Set<TaskEditorPartDescriptor> createPartDescriptors() {
		Set<TaskEditorPartDescriptor> descriptors = super.createPartDescriptors();
		// remove unnecessary default editor parts
		for (TaskEditorPartDescriptor taskEditorPartDescriptor : descriptors) {
			if (taskEditorPartDescriptor.getId().equals(ID_PART_PEOPLE)) {
				// it.remove();
			}
		}
		// descriptors.add(new TaskEditorPartDescriptor(ID_PART_PEOPLE) {
		// @Override
		// // public AbstractTaskEditorPart createPart() {
		// return new TracPeoplePart();
		// }
		// }.setPath(PATH_PEOPLE));
		return descriptors;
	}

	@Override
	protected void createParts() {
		// if (renderingEngine == null) {
		// renderingEngine = new TracRenderingEngine();
		// }
		// getAttributeEditorToolkit().setRenderingEngine(renderingEngine);
		super.createParts();
	}

	@Override
	protected AttributeEditorFactory createAttributeEditorFactory() {
		AttributeEditorFactory factory = new AttributeEditorFactory(getModel(),
				getTaskRepository(), getEditorSite()) {
			@Override
			public AbstractAttributeEditor createEditor(final String type,
					final TaskAttribute taskAttribute) {
				//				if (TracAttribute.CC.getTracKey().equals(taskAttribute.getId())) {
				//					return new TracCcAttributeEditor(getModel(), taskAttribute);
				//				}
				return super.createEditor(type, taskAttribute);
			};
		};
		return factory;
	}

}
