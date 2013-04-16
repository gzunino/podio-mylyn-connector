/**
 * 
 */
package com.allium.podio.mylyn.core.podio.ex;

import org.codehaus.jackson.annotate.JsonProperty;

import com.podio.contact.ProfileMini;
import com.podio.space.SpaceMember;

/**
 * @author Guillermo Zunino
 *
 */
public class SpaceMemberEx extends SpaceMember {

	@Override
	@JsonProperty("profile")
	public ProfileMini getUser() {
		return super.getUser();
	}
	
}
