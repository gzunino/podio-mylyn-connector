/*******************************************************************************
 * Copyright (c) 2006, 2009 Steffen Pingel and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Steffen Pingel - initial API and implementation
 *******************************************************************************/

package com.allium.podio.mylyn.core;

import java.text.ParseException;
import java.util.Date;
import java.util.EnumSet;

import org.eclipse.core.runtime.Assert;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.data.TaskAttribute;
import org.eclipse.mylyn.tasks.core.data.TaskAttributeMapper;

/**
 * Provides a mapping from Mylyn task keys to Trac ticket keys.
 * 
 * @author Steffen Pingel
 */
public class PodioAttributeMapper extends TaskAttributeMapper {

	public enum Flag {
		READ_ONLY, ATTRIBUTE, PEOPLE
	};

	public static final EnumSet<Flag> NO_FLAGS = EnumSet.noneOf(Flag.class);

	private final PodioClient client;

	public PodioAttributeMapper(final TaskRepository taskRepository, final PodioClient client) {
		super(taskRepository);
		Assert.isNotNull(client);
		this.client = client;
	}

	@Override
	public Date getDateValue(final TaskAttribute attribute) {
		return getDateFromString(attribute.getValue());
	}

	/**
	 * @param attribute
	 * @return
	 * @throws ParseException
	 */
	public static Date getDateFromString(final String date) {
		try {
			return new Date(Long.parseLong(date));
			//			return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse(date);
		} catch (NumberFormatException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * @param attribute
	 * @return
	 * @throws ParseException
	 */
	public static String getStringFromDate(final Date date) {
		//		return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format(date);
		return date.getTime()+"";
	}

	@Override
	public String mapToRepositoryKey(final TaskAttribute parent, final String key) {
		PodioAttribute attribute = PodioAttribute.getByTaskKey(key);
		return (attribute != null) ? attribute.getPodioKey() : key;
	}

	//	@Override
	//	public void setDateValue(TaskAttribute attribute, Date date) {
	//		if (date == null) {
	//			attribute.clearValues();
	//		} else {
	//			attribute.setValue(TracUtil.toTracTime(date) + ""); //$NON-NLS-1$
	//		}
	//	}

	//	@Override
	//	public Map<String, String> getOptions(TaskAttribute attribute) {
	//		Map<String, String> options = getRepositoryOptions(client, attribute.getId());
	//		return (options != null) ? options : super.getOptions(attribute);
	//	}

	//	public static Map<String, String> getRepositoryOptions(ITracClient client, String trackKey) {
	//		if (client.hasAttributes()) {
	//			if (TracAttribute.STATUS.getTracKey().equals(trackKey)) {
	//				return getOptions(client.getTicketStatus(), false);
	//			} else if (TracAttribute.RESOLUTION.getTracKey().equals(trackKey)) {
	//				return getOptions(client.getTicketResolutions(), false);
	//			} else if (TracAttribute.COMPONENT.getTracKey().equals(trackKey)) {
	//				return getOptions(client.getComponents(), false);
	//			} else if (TracAttribute.VERSION.getTracKey().equals(trackKey)) {
	//				return getOptions(client.getVersions(), true);
	//			} else if (TracAttribute.PRIORITY.getTracKey().equals(trackKey)) {
	//				return getOptions(client.getPriorities(), false);
	//			} else if (TracAttribute.SEVERITY.getTracKey().equals(trackKey)) {
	//				return getOptions(client.getSeverities(), false);
	//			} else if (TracAttribute.MILESTONE.getTracKey().equals(trackKey)) {
	//				return getOptions(client.getMilestones(), true);
	//			} else if (TracAttribute.TYPE.getTracKey().equals(trackKey)) {
	//				return getOptions(client.getTicketTypes(), false);
	//			}
	//		}
	//		return null;
	//	}

	//	private static Map<String, String> getOptions(Object[] values, boolean allowEmpty) {
	//		if (values != null && values.length > 0) {
	//			Map<String, String> options = new LinkedHashMap<String, String>();
	//			if (allowEmpty) {
	//				options.put("", ""); //$NON-NLS-1$ //$NON-NLS-2$
	//			}
	//			for (Object value : values) {
	//				options.put(value.toString(), value.toString());
	//			}
	//			return options;
	//		}
	//		return null;
	//	}

}
