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
import org.eclipse.mylyn.tasks.ui.TasksUi;
import org.eclipse.mylyn.tasks.ui.wizards.AbstractRepositoryQueryPage2;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

import com.allium.podio.mylyn.core.PodioClient;
import com.allium.podio.mylyn.core.PodioPlugin;
import com.allium.podio.mylyn.core.PodioRepositoryConnector;
import com.podio.app.ApplicationField;
import com.podio.app.ApplicationFieldType;
import com.podio.app.ApplicationMini;
import com.podio.filter.AppFieldFilterBy;
import com.podio.filter.MemberFieldFilterBy;
import com.podio.filter.StateFieldFilterBy;
import com.podio.item.ItemBadge;
import com.podio.org.OrganizationMini;
import com.podio.org.OrganizationWithSpaces;
import com.podio.space.SpaceMember;
import com.podio.space.SpaceMini;

public class PodioQueryPage extends AbstractRepositoryQueryPage2 {

	private class ComboField<T> {

		public ComboViewer viewer;

		public ComboField() {
		}

		public void createControls(final Composite parent) {
			viewer = new ComboViewer(parent, SWT.SINGLE | SWT.BORDER | SWT.READ_ONLY);
			viewer.setContentProvider(ArrayContentProvider.getInstance());
			GridDataFactory.fillDefaults().align(SWT.FILL, SWT.BEGINNING).grab(true, false).applyTo(viewer.getControl());
		}

		public void setLabelProvider(final ILabelProvider provider) {
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

		protected PodioClient client;
		protected Composite parent;
		protected ApplicationField appField;
		protected OrganizationWithSpaces org;
		protected SpaceMini space;
		protected ApplicationMini app;

		public final Control createEditor(Composite parent, OrganizationWithSpaces org, SpaceMini space, ApplicationMini app, ApplicationField appField, PodioClient client) {
			this.parent = parent;
			this.org = org;
			this.space = space;
			this.app = app;
			this.appField = appField;
			this.client = client;
			return this.createEditor(parent);
		}

		protected abstract Control createEditor(Composite parent);

		public String getFieldId() {
			assert appField != null;
			return appField.getId() + "";
		}

		public abstract String getValue();
	}

	public abstract static class ListFieldEditor extends FieldEditor {
		protected org.eclipse.swt.widgets.List list;

		@Override
		public Control createEditor(final Composite parent) {
			final Composite group = new Composite(parent, SWT.NONE);
			GridDataFactory.fillDefaults().grab(false, true).align(SWT.FILL, SWT.FILL).applyTo(group);
			GridLayout layout = new GridLayout();
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			layout.numColumns = 1;
			group.setLayout(layout);

			Label label = new Label(group, SWT.LEFT);
			label.setText(appField.getConfiguration().getLabel());

			list = new org.eclipse.swt.widgets.List(group, SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
			GridData gd = new GridData(SWT.BEGINNING, SWT.FILL, false, true);
			//			gd.heightHint = height;
			list.setLayoutData(gd);

			setInput(list);

			return list;
		}

		protected abstract void setInput(org.eclipse.swt.widgets.List list);
	}

	public static class FieldStateEditor extends ListFieldEditor {

		@Override
		public String getValue() {
			if (list.getSelectionCount() == 0) {
				return null;
			}
			return new StateFieldFilterBy(0).format(Arrays.asList(list.getSelection()));
		}

		@Override
		protected void setInput(final org.eclipse.swt.widgets.List list) {
			list.setItems(appField.getConfiguration().getSettings().getAllowedValues().toArray(new String[0]));
		}
	}

	public static class FieldAppEditor extends ListFieldEditor {
		private final ArrayList<ItemBadge> refItems = new ArrayList<ItemBadge>();

		@Override
		public String getValue() {
			if (list.getSelectionCount() == 0) {
				return null;
			}
			int[] selected = list.getSelectionIndices();
			List<Integer> selectedIds = new ArrayList<Integer>();
			for (int index : selected) {
				selectedIds.add(refItems.get(index).getId());
			}

			return new AppFieldFilterBy(0).format(selectedIds).replaceAll("; ", ";");
		}

		@Override
		protected void setInput(final org.eclipse.swt.widgets.List list) {
			List<Integer> refApps = appField.getConfiguration().getSettings().getReferenceableTypes();
			for (Integer refApp : refApps) {
				refItems.addAll(super.client.queryItems(refApp));
			}
			for (ItemBadge itemBadge : refItems) {
				list.add(itemBadge.getTitle());
			}
		}
	}
	
	public static class FieldMemberEditor extends ListFieldEditor {
		private List<SpaceMember> members;

		@Override
		public String getValue() {
			if (list.getSelectionCount() == 0) {
				return null;
			}
			int[] selected = list.getSelectionIndices();
			List<Integer> selectedIds = new ArrayList<Integer>();
			for (int index : selected) {
				selectedIds.add(members.get(index).getUser().getProfileId());
			}
			return new MemberFieldFilterBy(0).format(selectedIds).replaceAll("; ", ";");
		}

		@Override
		protected void setInput(final org.eclipse.swt.widgets.List list) {
			members = client.getMembers(space.getId());
			
			for (SpaceMember member : members) {
				list.add(member.getUser().getName());
			}
		}
	}

	private static Map<ApplicationFieldType, Class<? extends FieldEditor>> fieldEditorMap = new HashMap<ApplicationFieldType, Class<? extends FieldEditor>>();

	static {
		fieldEditorMap.put(ApplicationFieldType.STATE, FieldStateEditor.class);
		fieldEditorMap.put(ApplicationFieldType.APP, FieldAppEditor.class);
		fieldEditorMap.put(ApplicationFieldType.MEMBER, FieldMemberEditor.class);
		fieldEditorMap.put(ApplicationFieldType.CONTACT, FieldMemberEditor.class);
	}

	private ComboField<OrganizationWithSpaces> orgField;
	private ComboField<SpaceMini> spaceField;
	private ComboField<ApplicationMini> appField;
	private final PodioClient client;
	private final List<FieldEditor> filters = new ArrayList<FieldEditor>();
	private Composite fieldsComposite;

	public PodioQueryPage(final TaskRepository repository,
			final IRepositoryQuery query) {
		super("Podio Query Page", repository, query);
		PodioRepositoryConnector connector = (PodioRepositoryConnector) TasksUi.getRepositoryManager()
				.getRepositoryConnector(PodioPlugin.CONNECTOR_KIND);
		client = connector.getClientManager().getClient(repository);
	}

	@Override
	protected boolean hasRepositoryConfiguration() {
		return true;
	}

	@Override
	protected boolean restoreState(final IRepositoryQuery query) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void applyTo(final IRepositoryQuery query) {
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
	protected void createPageContent(final SectionComposite parent) {
		Composite control = parent.getContent();

		GridLayout layout = new GridLayout(3, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		control.setLayout(layout);

		createAppControls(control);
	}

	private void createAppControls(final Composite parent) {
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
			public String getText(final Object element) {
				return element != null ? ((OrganizationMini) element).getName() : "";
			}
		});
		orgField.viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(final SelectionChangedEvent event) {
				clearCombo(spaceField);
				clearCombo(appField);
				clearFields();
				if (!event.getSelection().isEmpty()) {
					OrganizationWithSpaces org = orgField.getSelection();
					spaceField.viewer.setInput(org.getSpaces());
				}
			}
		});

		spaceField = new ComboField<SpaceMini>();
		spaceField.createControls(group);
		spaceField.viewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(final Object element) {
				return element != null ? ((SpaceMini) element).getName() : "";
			}
		});
		spaceField.viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(final SelectionChangedEvent event) {
				clearCombo(appField);
				clearFields();
				if (!event.getSelection().isEmpty()) {
					SpaceMini space = spaceField.getSelection();
					appField.viewer.setInput(client.getApplications(space.getId()));
				}
			}
		});

		appField = new ComboField<ApplicationMini>();
		appField.createControls(group);
		appField.viewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(final Object element) {
				return element != null ? ((ApplicationMini) element).getConfiguration().getName() : "";
			}
		});
		appField.viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(final SelectionChangedEvent event) {
				clearFields();
				if (!event.getSelection().isEmpty()) {
					OrganizationWithSpaces org = orgField.getSelection();
					SpaceMini space = spaceField.getSelection();
					ApplicationMini app = appField.getSelection();
					createFields(group, org, space, app);
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

	protected void createFields(final Composite parent, final OrganizationWithSpaces org, final SpaceMini space, final ApplicationMini app) {
		fieldsComposite = new Composite(parent, SWT.NONE);
		GridDataFactory.fillDefaults().grab(false, true).align(SWT.FILL, SWT.FILL).span(3, 1).applyTo(fieldsComposite);
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
					editor.createEditor(fieldsComposite, org, space, app, applicationField, client);
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
	private void clearCombo(final ComboField<?> field) {
		field.viewer.setInput(Collections.EMPTY_LIST);
		field.viewer.setSelection(StructuredSelection.EMPTY);
	}

}
