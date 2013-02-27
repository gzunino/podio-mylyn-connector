package com.allium.podio.mylyn.ui.wizard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.mylyn.commons.workbench.forms.SectionComposite;
import org.eclipse.mylyn.tasks.core.IRepositoryQuery;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.ui.wizards.AbstractRepositoryQueryPage2;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

import com.allium.podio.mylyn.core.PodioClient;
import com.podio.app.ApplicationField;
import com.podio.app.ApplicationFieldType;
import com.podio.app.ApplicationMini;
import com.podio.filter.AppFieldFilterBy;
import com.podio.filter.StateFieldFilterBy;
import com.podio.item.ItemBadge;
import com.podio.org.OrganizationMini;
import com.podio.org.OrganizationWithSpaces;
import com.podio.space.SpaceMini;

public class PodioQueryPage extends AbstractRepositoryQueryPage2 {

	private class ComboField<T> {
	
		public ComboViewer viewer;
	
		public ComboField() {
		}
	
		public void createControls(Composite parent) {
			viewer = new ComboViewer(parent, SWT.SINGLE | SWT.BORDER | SWT.READ_ONLY);
			viewer.setContentProvider(ArrayContentProvider.getInstance());
			GridDataFactory.fillDefaults().align(SWT.FILL, SWT.BEGINNING).grab(true, false).applyTo(viewer.getControl());
		}
		
		public void setLabelProvider(ILabelProvider provider) {
			viewer.setLabelProvider(provider);
		}
		
		@SuppressWarnings("unchecked")
		public T getSelection() {
			if (viewer.getSelection().isEmpty()) {
				return null;
			} else {
				return (T) ((IStructuredSelection) viewer.getSelection()).getFirstElement();
			}
		}
	
	}

	public static abstract class FieldEditor {

		private ApplicationField appField;
		protected PodioClient client;

		public abstract Control createEditor(Composite parent, ApplicationField applicationField, PodioClient client);

		public String getFieldId() {
			assert appField != null;
			return appField.getId() + "";
		}

		public abstract String getValue();
	}
	
	public abstract static class ListFieldEditor extends FieldEditor {
		protected org.eclipse.swt.widgets.List list;
		
		public Control createEditor(Composite parent, ApplicationField applicationField, PodioClient client) {
			super.appField = applicationField;
			super.client = client;
			
			final Composite group = new Composite(parent, SWT.NONE);
			GridDataFactory.fillDefaults().grab(false, true).align(SWT.FILL, SWT.FILL).applyTo(group);
			GridLayout layout = new GridLayout();
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			layout.numColumns = 1;
			group.setLayout(layout);
			
			Label label = new Label(group, SWT.LEFT);
			label.setText(applicationField.getConfiguration().getLabel());
			
			list = new org.eclipse.swt.widgets.List(group, SWT.MULTI | SWT.V_SCROLL | SWT.BORDER);
			GridData gd = new GridData(SWT.FILL, SWT.FILL, false, true);
//			gd.heightHint = height;
			list.setLayoutData(gd);
			
			setInput(list, applicationField);
			
			return list;
		}

		protected abstract void setInput(org.eclipse.swt.widgets.List list,
				ApplicationField applicationField);
	}
	
	public static class FieldStateEditor extends ListFieldEditor {
		
		@Override
		public String getValue() {
			return new StateFieldFilterBy(0).format(Arrays.asList(list.getSelection()));
		}

		@Override
		protected void setInput(org.eclipse.swt.widgets.List list,
				ApplicationField applicationField) {
			list.setItems(applicationField.getConfiguration().getSettings().getAllowedValues().toArray(new String[0]));
		}
	}
	
	public static class FieldAppEditor extends ListFieldEditor {
		private ArrayList<ItemBadge> refItems = new ArrayList<ItemBadge>();

		@Override
		public String getValue() {
			int[] selected = list.getSelectionIndices();
			List<Integer> selectedIds = new ArrayList<Integer>();
			for (int index : selected) {
				selectedIds.add(refItems.get(index).getId());
			}
			return new AppFieldFilterBy(0).format(selectedIds);
		}

		@Override
		protected void setInput(org.eclipse.swt.widgets.List list,
				ApplicationField applicationField) {
			List<Integer> refApps = applicationField.getConfiguration().getSettings().getReferenceableTypes();
			for (Integer refApp : refApps) {
				refItems.addAll(super.client.queryItems(refApp));
			}
			for (ItemBadge itemBadge : refItems) {
				list.add(itemBadge.getTitle());
			}
		}
	}

	private static Map<ApplicationFieldType, Class<? extends FieldEditor>> fieldEditorMap = new HashMap<ApplicationFieldType, Class<? extends FieldEditor>>();

	static {
		fieldEditorMap.put(ApplicationFieldType.STATE, FieldStateEditor.class);
		fieldEditorMap.put(ApplicationFieldType.APP, FieldAppEditor.class);
	}

	private ComboField<OrganizationWithSpaces> orgField;
	private ComboField<SpaceMini> spaceField;
	private ComboField<ApplicationMini> appField;
	private PodioClient client;
	private List<FieldEditor> filters = new ArrayList<FieldEditor>();
	private Composite fieldsComposite;
	
	public PodioQueryPage(TaskRepository repository,
			IRepositoryQuery query) {
		super("Podio Query Page", repository, query);
		client = PodioClient.getClient(getTaskRepository());
	}

	@Override
	protected boolean hasRepositoryConfiguration() {
		return true;
	}

	@Override
	protected boolean restoreState(IRepositoryQuery query) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void applyTo(IRepositoryQuery query) {
		query.setSummary(getQueryTitle());
		query.setAttribute("appId", appField.getSelection().getId()+"");
		for (FieldEditor filter : filters) {
			String filterValue = filter.getValue();
			if (filterValue != null) {
				query.setAttribute(filter.getFieldId(), filter.getValue());
			}
		}
	}

	@Override
	protected void doRefreshControls() {
		List<OrganizationWithSpaces> orgs = client.getOrgs();
		orgField.viewer.setInput(orgs);
		clearCombo(spaceField);
		clearCombo(appField);
	}

	@Override
	protected void createPageContent(SectionComposite parent) {
		Composite control = parent.getContent();

		GridLayout layout = new GridLayout(3, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		control.setLayout(layout);

		createAppControls(control);
	}
	
	private void createAppControls(Composite parent) {
		final Composite group = new Composite(parent, SWT.NONE);
		GridDataFactory.fillDefaults().grab(true, true).align(SWT.FILL, SWT.FILL).span(3, 1).applyTo(group);
		GridLayout layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.numColumns = 3;
		group.setLayout(layout);

		Label label = new Label(group, SWT.LEFT);
		label.setText("Organization");

		label = new Label(group, SWT.LEFT);
		label.setText("Workpsace");

		label = new Label(group, SWT.LEFT);
		label.setText("Application");

		orgField = new ComboField<OrganizationWithSpaces>();
		orgField.createControls(group);
		orgField.viewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				return element != null ? ((OrganizationMini) element).getName() : "";
			}
		});
		orgField.viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				if (!event.getSelection().isEmpty()) {
					OrganizationWithSpaces org = orgField.getSelection();
					spaceField.viewer.setInput(org.getSpaces());
				} else {
					clearCombo(spaceField);
					clearCombo(appField);
				}
			}
		});

		spaceField = new ComboField<SpaceMini>();
		spaceField.createControls(group);
		spaceField.viewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				return element != null ? ((SpaceMini) element).getName() : "";
			}
		});
		spaceField.viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				if (!event.getSelection().isEmpty()) {
					SpaceMini space = spaceField.getSelection();
					appField.viewer.setInput(client.getApplications(space.getId()));
				} else {
					clearCombo(appField);
				}
			}
		});
		
		appField = new ComboField<ApplicationMini>();
		appField.createControls(group);
		appField.viewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				return element != null ? ((ApplicationMini) element).getConfiguration().getName() : "";
			}
		});
		appField.viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				clearFields();
				if (!event.getSelection().isEmpty()) {
					ApplicationMini app = appField.getSelection();
					createFields(group, app);
//					appField.viewer.setInput(client.getApplications(space.getId()));
				} else {
				}
			}
		});
		
		Label sep = new Label(group, SWT.SEPARATOR | SWT.HORIZONTAL);
		GridDataFactory.fillDefaults().span(3, 1).applyTo(sep);
	}

	protected void clearFields() {
		filters.clear();
		if (fieldsComposite != null && !fieldsComposite.isDisposed()) {
			Composite parent = fieldsComposite.getParent();
			fieldsComposite.dispose();
			parent.layout();
		}
	}

	protected void createFields(Composite parent, ApplicationMini app) {
		fieldsComposite = new Composite(parent, SWT.NONE);
		GridDataFactory.fillDefaults().grab(true, true).align(SWT.FILL, SWT.FILL).span(3, 1).applyTo(fieldsComposite);
		GridLayout layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.numColumns = 4;
		fieldsComposite.setLayout(layout);
		
		Label label = new Label(fieldsComposite, SWT.LEFT);
		GridDataFactory.fillDefaults().span(4, 1).applyTo(label);
		label.setText("Filter '"+app.getConfiguration().getItemName()+"' items by field on application '"+ app.getConfiguration().getName()+"':");
		
		List<ApplicationField> fields = client.getFields(app.getId());
		for (ApplicationField applicationField : fields) {
			Class<?> editorFactory = fieldEditorMap.get(applicationField.getType());
			if (editorFactory != null) {
				try {
					FieldEditor editor = (FieldEditor) editorFactory.newInstance();
					editor.createEditor(fieldsComposite, applicationField, client);
					filters.add(editor);
				} catch (InstantiationException | IllegalAccessException e) {
					e.printStackTrace();
				}
			}
		}
		fieldsComposite.getParent().layout();
	}

	/**
	 * @param field 
	 * 
	 */
	private void clearCombo(ComboField<?> field) {
		field.viewer.setInput(Collections.EMPTY_LIST);
		field.viewer.setSelection(StructuredSelection.EMPTY);
	}

}
