package com.allium.podio.mylyn.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.mylyn.commons.net.AuthenticationCredentials;
import org.eclipse.mylyn.commons.net.AuthenticationType;
import org.eclipse.mylyn.tasks.core.TaskRepository;

import com.podio.APIFactory;
import com.podio.ResourceFactory;
import com.podio.app.AppAPI;
import com.podio.app.Application;
import com.podio.app.ApplicationField;
import com.podio.app.ApplicationMini;
import com.podio.file.FileAPI;
import com.podio.filter.FilterByValue;
import com.podio.filter.StandardSortBy;
import com.podio.item.FieldValuesUpdate;
import com.podio.item.FieldValuesView;
import com.podio.item.Item;
import com.podio.item.ItemAPI;
import com.podio.item.ItemBadge;
import com.podio.item.ItemCreate;
import com.podio.item.ItemsResponse;
import com.podio.oauth.OAuthClientCredentials;
import com.podio.oauth.OAuthUsernameCredentials;
import com.podio.org.OrgAPI;
import com.podio.org.OrganizationWithSpaces;
import com.podio.root.RootAPI;
import com.podio.root.SystemStatus;

public class PodioClient {

	private static final int LIMIT = 500;
	private static final String PODIO_KEY = "39IkNfAli80bZoDY614aVdYzz8LEW5RKBYY6sQa1kNV9u7ZQfQZ3KPJPsWaO7asj";
	private static final String PODIO_CLIENTID = "mylyn-podio-connector";
	
	private static PodioClient instance = null;

	private APIFactory apiFactory;
	private List<OrganizationWithSpaces> cacheOrgs;
	private Map<Integer, List<ApplicationField>> cacheFields = new HashMap<Integer, List<ApplicationField>>();
	private Map<Integer, List<ApplicationMini>> cacheApps = new HashMap<Integer, List<ApplicationMini>>();
	private Map<Integer, Item> cacheItems = new HashMap<Integer, Item>();
	
	public PodioClient(String username, String password) {
		ResourceFactory resourceFactory = new ResourceFactory(
		        new OAuthClientCredentials(PODIO_CLIENTID, PODIO_KEY),
		        new OAuthUsernameCredentials(username, password));
		apiFactory = new APIFactory(resourceFactory);
	
	}
	
	public static PodioClient getClient(String username, String password) {
		if (instance  == null) {
			instance = new PodioClient(username, password);
		}
		return instance;
	}

	public static PodioClient getClient(TaskRepository repository) {
		AuthenticationCredentials creds = repository.getCredentials(AuthenticationType.REPOSITORY);
		return getClient(creds.getUserName(), creds.getPassword());
	}

	public String connect() {
		RootAPI root = apiFactory.getAPI(RootAPI.class);
		SystemStatus status = root.getStatus();
		System.out.println(status);
		return status.toString();
	}
	
	public List<OrganizationWithSpaces> getOrgs() {
		if (cacheOrgs == null) {
			OrgAPI api = apiFactory.getAPI(OrgAPI.class);
			cacheOrgs = api.getOrganizations();
		}
		return cacheOrgs;
	}
	
	public List<ApplicationMini> getApplications(int spaceId) {
		List<ApplicationMini> apps = cacheApps.get(spaceId);
		if (apps == null) {
			AppAPI api = apiFactory.getAPI(AppAPI.class);
			apps = api.getAppsOnSpace(spaceId);
			cacheApps.put(spaceId, apps);
		}
		return apps;
	}
	
	public List<ItemBadge> queryItems(int appId, FilterByValue<?>... filters) {
		ItemAPI api = apiFactory.getAPI(ItemAPI.class);
		ItemsResponse items = api.getItems(appId, LIMIT, 0, StandardSortBy.LAST_EDIT_ON, true, filters);
		return items.getItems();
	}
	
	public List<ApplicationField> getFields(int appId) {
		List<ApplicationField> fields = cacheFields.get(appId);
		if (fields == null) {
			AppAPI api = apiFactory.getAPI(AppAPI.class);
			Application app = api.getApp(appId);
			fields = app.getFields();
			cacheFields.put(appId, fields);
		}
		return fields;
	}

	public Item getItem(int id) {
		Item item = cacheItems.get(id);
		if (item == null) {
			ItemAPI api = apiFactory.getAPI(ItemAPI.class);
			item = api.getItem(id);
			cacheItems.put(id, item);
		}
		return item;
	}

	public InputStream getFile(int fileId, String filename) throws IOException {
		FileAPI api = apiFactory.getAPI(FileAPI.class);
		IPath path = InstanceScope.INSTANCE.getLocation();
		
		File target = path.append(filename).toFile();
		api.downloadFile(fileId, target, null);
		return new FileInputStream(target);
	}
	
	public int uploadFile(String filename, InputStream is) throws IOException {
		FileAPI api = apiFactory.getAPI(FileAPI.class);

		IPath path = InstanceScope.INSTANCE.getLocation();
		File source = path.append(filename).toFile();

		inputStreamAsFile(is, source);
		return api.uploadFile(filename, source);
	}
	
	public void attachFile(final int itemId, final int fileId) {
		ItemAPI api = apiFactory.getAPI(ItemAPI.class);
		ItemCreate update = new ItemCreate();
		update.setFileIds(Collections.singletonList(fileId));
		api.updateItem(itemId, update, false);

	}
	
	public int createItem(Item item) {
		ItemAPI api = apiFactory.getAPI(ItemAPI.class);
		
		ItemCreate create = new ItemCreate();
		List<FieldValuesUpdate> fields = new ArrayList<FieldValuesUpdate>();
		
		for (FieldValuesView fieldValueView : item.getFields()) {
			FieldValuesUpdate e = new FieldValuesUpdate(fieldValueView.getId(), fieldValueView.getValues());
			fields.add(e);
		}
		create.setFields(fields);
		return api.addItem(item.getApplication().getId(), create, false);
	}

	public void updateItem(Item item) {
		ItemAPI api = apiFactory.getAPI(ItemAPI.class);
		
		ItemCreate update = new ItemCreate();
		List<FieldValuesUpdate> fields = new ArrayList<FieldValuesUpdate>();
		
		for (FieldValuesView fieldValueView : item.getFields()) {
			FieldValuesUpdate e = new FieldValuesUpdate(fieldValueView.getId(), fieldValueView.getValues());
			fields.add(e);
		}
		update.setFields(fields);
		api.updateItem(item.getId(), update, false);
	}
	
	private void inputStreamAsFile(InputStream is, File f) throws IOException {
		OutputStream salida = new FileOutputStream(f);
		byte[] buf = new byte[1024];
		int len;
		while ((len = is.read(buf)) > 0) {
			salida.write(buf, 0, len);
		}
		salida.close();
		is.close();
	}
}
