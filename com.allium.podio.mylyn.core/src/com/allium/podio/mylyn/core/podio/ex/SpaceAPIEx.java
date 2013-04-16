/**
 * 
 */
package com.allium.podio.mylyn.core.podio.ex;

import java.util.List;

import com.podio.ResourceFactory;
import com.podio.space.SpaceAPI;
import com.sun.jersey.api.client.GenericType;

/**
 * @author Guillermo Zunino
 *
 */
public class SpaceAPIEx extends SpaceAPI {

	public SpaceAPIEx(ResourceFactory resourceFactory) {
		super(resourceFactory);
	}
	
	public List<SpaceMemberEx> getMembers(int spaceId) {
		return getResourceFactory().getApiResource(
				"/space/" + spaceId + "/member/").get(
				new GenericType<List<SpaceMemberEx>>() {
				});
	}

}
