package com.allium.podio.mylyn.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.allium.podio.mylyn.core.PodioClient;
import com.podio.app.ApplicationField;
import com.podio.app.ApplicationMini;
import com.podio.item.Item;
import com.podio.item.ItemBadge;
import com.podio.org.OrganizationWithSpaces;
import com.podio.space.SpaceMini;

public class PodioClientTest {
	private PodioClient client;

	@Before
	public void setupClient() {
		client = PodioClient.getClient("guillez@gmail.com", "Guille!563");
	}
	
	@Test
	public void testSpaces() {
		List<OrganizationWithSpaces> orgs = client.getOrgs();
		
		assertThat(orgs, notNullValue());
		assertThat(orgs, not(empty()));
		assertThat(orgs.get(0).getId(), not(0));
		assertThat(orgs.get(0).getName(), notNullValue());
		assertThat(orgs.get(0).getName(), not(equalToIgnoringWhiteSpace("")));
		
		List<SpaceMini> spaces = orgs.get(0).getSpaces();
		assertThat(spaces, notNullValue());
		assertThat(spaces, not(empty()));
		assertThat(spaces.get(0).getId(), not(0));
		assertThat(spaces.get(0).getName(), notNullValue());
		assertThat(spaces.get(0).getName(), not(equalToIgnoringWhiteSpace("")));
	}
	
	@Test
	public void testApps() {
		int spaceId = client.getOrgs().get(0).getSpaces().get(0).getId();
		List<ApplicationMini> apps = client.getApplications(spaceId);

		assertThat(apps, notNullValue());
		assertThat(apps, not(empty()));
		assertThat(apps.get(0).getId(), not(0));
		assertThat(apps.get(0).getConfiguration(), notNullValue());
		assertThat(apps.get(0).getConfiguration().getName(), not(equalToIgnoringWhiteSpace("")));
	}
	
	@Test
	public void testFields() {
		int appId = getAnAppId();
		
		List<ApplicationField> fields = client.getFields(appId);
		
		assertThat(fields, notNullValue());
		assertThat(fields, not(empty()));
		assertThat(fields.get(0).getId(), not(0));
		assertThat(fields.get(0).getConfiguration(), notNullValue());
		assertThat(fields.get(0).getConfiguration().getSettings(), notNullValue());
	}

	
	@Test
	public void testQueryItems() {
		int appId = getAnAppId();
		
		List<ItemBadge> items = client.queryItems(appId);
		assertThat(items, notNullValue());
		assertThat(items.size(), not(0));
	}
	
	@Test
	public void testItem() {
		int appId = getAnAppId();
		
		List<ItemBadge> items = client.queryItems(appId);
		ItemBadge itemMini = items.get(0);
		
		Item item = client.getItem(itemMini.getId());
		
		assertThat(item, notNullValue());
		assertThat(item.getId(), not(nullValue()));
	}

	/**
	 * @return
	 */
	private int getAnAppId() {
		int spaceId = client.getOrgs().get(0).getSpaces().get(0).getId();
		List<ApplicationMini> apps = client.getApplications(spaceId);
		int appId = apps.get(0).getId();
		return appId;
	}
}