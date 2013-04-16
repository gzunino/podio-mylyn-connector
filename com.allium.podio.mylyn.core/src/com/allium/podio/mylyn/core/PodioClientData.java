package com.allium.podio.mylyn.core;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.podio.app.ApplicationField;
import com.podio.app.ApplicationMini;
import com.podio.item.Item;
import com.podio.org.OrganizationWithSpaces;
import com.podio.space.SpaceMember;

public class PodioClientData implements Serializable {

	private static final long serialVersionUID = -8582091619395030438L;
	List<OrganizationWithSpaces> cacheOrgs;
	final Map<Integer, List<ApplicationField>> cacheFields = new HashMap<Integer, List<ApplicationField>>();
	final Map<Integer, List<ApplicationMini>> cacheApps = new HashMap<Integer, List<ApplicationMini>>();
	final Map<Integer, Item> cacheItems = new HashMap<Integer, Item>();
	final Map<Integer, List<? extends SpaceMember>> cacheMembers = new HashMap<Integer, List<? extends SpaceMember>>();
	
}
