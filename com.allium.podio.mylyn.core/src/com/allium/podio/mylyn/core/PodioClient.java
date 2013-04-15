package com.allium.podio.mylyn.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.mylyn.commons.net.AbstractWebLocation;
import org.eclipse.mylyn.commons.net.AuthenticationCredentials;
import org.eclipse.mylyn.commons.net.AuthenticationType;
import org.joda.time.DateTime;

import com.podio.APIFactory;
import com.podio.ResourceFactory;
import com.podio.app.AppAPI;
import com.podio.app.Application;
import com.podio.app.ApplicationField;
import com.podio.app.ApplicationMini;
import com.podio.common.ReferenceType;
import com.podio.file.FileAPI;
import com.podio.file.FileUpdate;
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
import com.podio.stream.StreamAPI;
import com.podio.stream.StreamObjectV2;
import com.podio.user.UserAPI;

public class PodioClient {

	private static final int LIMIT = 500;
	private static final String PODIO_KEY = "39IkNfAli80bZoDY614aVdYzz8LEW5RKBYY6sQa1kNV9u7ZQfQZ3KPJPsWaO7asj";
	private static final String PODIO_CLIENTID = "mylyn-podio-connector";

	private APIFactory apiFactory;

	private final AbstractWebLocation location;
	// private final String repositoryUrl;
	private final PodioClientData data;

	// public PodioClient(final String username, final String password) {
	// ResourceFactory resourceFactory = new ResourceFactory(
	// new OAuthClientCredentials(PODIO_CLIENTID, PODIO_KEY),
	// new OAuthUsernameCredentials(username, password));
	// apiFactory = new APIFactory(resourceFactory);
	// }

	public PodioClient(final AbstractWebLocation location) {
		this.location = location;
		// this.repositoryUrl = location.getUrl();

		this.data = new PodioClientData();

		AuthenticationCredentials cred = this.location
				.getCredentials(AuthenticationType.REPOSITORY);

		createAPIFactory(cred);
	}

	/**
	 * @param cred
	 */
	private void createAPIFactory(final AuthenticationCredentials cred) {
		ResourceFactory resourceFactory = new ResourceFactory(
				new OAuthClientCredentials(PODIO_CLIENTID, PODIO_KEY),
				new OAuthUsernameCredentials(cred.getUserName(), cred.getPassword()));
		apiFactory = new APIFactory(resourceFactory);
	}

	// public static PodioClient getClient(final String username, final String
	// password) {
	// if (instance == null) {
	// instance = new PodioClient(username, password);
	// }
	// return instance;
	// }

	// public static PodioClient getClient(final TaskRepository repository) {
	// AuthenticationCredentials creds =
	// repository.getCredentials(AuthenticationType.REPOSITORY);
	// return getClient(creds.getUserName(), creds.getPassword());
	// }

	public String connect() {
		try {
			UserAPI api = apiFactory.getAPI(UserAPI.class);
			return api.getStatus().getUser().getStatus().toString();
		} catch (Exception e) {
			// instance = null;
			throw e;
		}
	}

	public List<OrganizationWithSpaces> getOrgs() {
		if (data.cacheOrgs == null) {
			OrgAPI api = apiFactory.getAPI(OrgAPI.class);
			data.cacheOrgs = api.getOrganizations();
		}
		return data.cacheOrgs;
	}

	public List<ApplicationMini> getApplications(final int spaceId) {
		List<ApplicationMini> apps = data.cacheApps.get(spaceId);
		if (apps == null) {
			AppAPI api = apiFactory.getAPI(AppAPI.class);
			apps = api.getAppsOnSpace(spaceId);
			data.cacheApps.put(spaceId, apps);
		}
		return apps;
	}

	public List<ItemBadge> queryItems(final int appId, final FilterByValue<?>... filters) {
		ItemAPI api = apiFactory.getAPI(ItemAPI.class);
		ItemsResponse items = api.getItems(appId, LIMIT, 0, StandardSortBy.LAST_EDIT_ON, true, filters);
		return items.getItems();
	}

	public Set<Integer> getChangedItems(final Date since) {
		Set<Integer> ids = new HashSet<Integer>();

		StreamAPI api = apiFactory.getAPI(StreamAPI.class);
		DateTime from = null; // TODO get DateTime from Date
		DateTime to = new DateTime(0);
		List<StreamObjectV2> streams = api.getGlobalStreamV2(LIMIT, 0, from, to);
		for (StreamObjectV2 stream : streams) {
			if (ReferenceType.ITEM.equals(stream.getType())) {
				ids.add(stream.getId());
			}
		}
		return ids;
	}

	public List<ApplicationField> getFields(final int appId) {
		List<ApplicationField> fields = data.cacheFields.get(appId);
		if (fields == null) {
			AppAPI api = apiFactory.getAPI(AppAPI.class);
			Application app = api.getApp(appId);
			fields = app.getFields();
			data.cacheFields.put(appId, fields);
		}
		return fields;
	}

	public Item getItem(final int id, final boolean force) {
		Item item = data.cacheItems.get(id);
		if (item == null || force) {
			ItemAPI api = apiFactory.getAPI(ItemAPI.class);
			item = api.getItem(id);
			data.cacheItems.put(id, item);
		}
		return item;
	}

	public InputStream getFile(final int fileId, final String filename) throws IOException {
		FileAPI api = apiFactory.getAPI(FileAPI.class);
		// ConfigurationScope.INSTANCE.getLocation()
		File target = File.createTempFile(filename, null);
		api.downloadFile(fileId, target, null);
		return new FileInputStream(target);
	}

	public int uploadFile(final String filename, final String description, final InputStream is)
			throws IOException {
		FileAPI api = apiFactory.getAPI(FileAPI.class);

		File source = File.createTempFile(filename, null);
		// IPath path = InstanceScope.INSTANCE.getLocation();
		// File source = path.append(filename).toFile();

		inputStreamAsFile(is, source);
		int id = api.uploadFile(filename, source);

		FileUpdate update = new FileUpdate(description);
		api.updateFile(id, update);

		return id;
	}

	public void attachFile(final int itemId, final int fileId) {
		ItemAPI api = apiFactory.getAPI(ItemAPI.class);
		ItemCreate update = new ItemCreate();
		update.setFileIds(Collections.singletonList(fileId));
		api.updateItem(itemId, update, false);

	}

	public int createItem(final Item item) {
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

	public void updateItem(final Item item) {
		ItemAPI api = apiFactory.getAPI(ItemAPI.class);

		ItemCreate update = new ItemCreate();
		List<FieldValuesUpdate> fields = new ArrayList<FieldValuesUpdate>();

		if (item.getFields() != null) {
			for (FieldValuesView fieldValueView : item.getFields()) {
				FieldValuesUpdate e = new FieldValuesUpdate(fieldValueView.getId(),
						fieldValueView.getValues());
				fields.add(e);
			}
			update.setFields(fields);
			api.updateItem(item.getId(), update, false);
		}
	}

	private void inputStreamAsFile(final InputStream is, final File f) throws IOException {
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
