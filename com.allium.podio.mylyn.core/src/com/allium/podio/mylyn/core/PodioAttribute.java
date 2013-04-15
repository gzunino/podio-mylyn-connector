/*******************************************************************************
 * Copyright (c) 2006, 2008 Steffen Pingel and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Steffen Pingel - initial API and implementation
 *******************************************************************************/

package com.allium.podio.mylyn.core;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.mylyn.tasks.core.data.TaskAttribute;

import com.allium.podio.mylyn.core.PodioAttributeMapper.Flag;

/**
 * @author Steffen Pingel
 */
public enum PodioAttribute {

	CREATED_ON(Key.CREATED_ON, "Creation Date", TaskAttribute.DATE_CREATION, TaskAttribute.TYPE_DATE, Flag.READ_ONLY),
	CREATED_BY(Key.CREATED_BY, "Created By", TaskAttribute.USER_REPORTER, TaskAttribute.TYPE_PERSON, Flag.READ_ONLY),

	CHANGED_ON(Key.CHANGED_ON, "Last Modification", TaskAttribute.DATE_MODIFICATION, TaskAttribute.TYPE_DATE, Flag.READ_ONLY),
	CHANGED_BY(Key.CHANGED_BY, "Modified By", TaskAttribute.USER_ASSIGNED, TaskAttribute.TYPE_PERSON, Flag.READ_ONLY),

	TITLE(Key.TITLE, "Summary", TaskAttribute.SUMMARY, TaskAttribute.TYPE_LONG_RICH_TEXT),
	DESCRIPTION(Key.SUMMARY, "Description", TaskAttribute.DESCRIPTION,
			TaskAttribute.TYPE_LONG_RICH_TEXT),

	ID(Key.ID, "ID:", TaskAttribute.TASK_KEY, TaskAttribute.TYPE_SHORT_TEXT, Flag.PEOPLE),

	TAGS(Key.TAGS, "Keywords", TaskAttribute.KEYWORDS, TaskAttribute.TYPE_SHORT_TEXT, Flag.ATTRIBUTE),

	RATING(Key.RATING, "Priority", TaskAttribute.PRIORITY, TaskAttribute.TYPE_SINGLE_SELECT, Flag.ATTRIBUTE),

	TYPE(Key.TYPE, "Type", TaskAttribute.TASK_KIND, TaskAttribute.TYPE_SINGLE_SELECT, Flag.ATTRIBUTE),
	LINK(Key.LINK, "Link", TaskAttribute.TASK_URL, TaskAttribute.TYPE_URL, Flag.ATTRIBUTE,
			Flag.READ_ONLY);

	static Map<String, PodioAttribute> attributeByTracKey = new HashMap<String, PodioAttribute>();

	static Map<String, String> tracKeyByTaskKey = new HashMap<String, String>();

	private final String podioKey;

	private final String prettyName;

	private final String taskKey;

	private final String type;

	private EnumSet<Flag> flags;

	public static PodioAttribute getByTaskKey(final String taskKey) {
		for (PodioAttribute attribute : values()) {
			if (taskKey.equals(attribute.getTaskKey())) {
				return attribute;
			}
		}
		return null;
	}

	public static PodioAttribute getByTracKey(final String tracKey) {
		for (PodioAttribute attribute : values()) {
			if (tracKey.equals(attribute.getPodioKey())) {
				return attribute;
			}
		}
		return null;
	}

	PodioAttribute(final Key tracKey, final String prettyName, final String taskKey, final String type, final Flag firstFlag, final Flag... moreFlags) {
		this.podioKey = tracKey.getKey();
		this.taskKey = taskKey;
		this.prettyName = prettyName;
		this.type = type;
		if (firstFlag == null) {
			this.flags = PodioAttributeMapper.NO_FLAGS;
		} else {
			this.flags = EnumSet.of(firstFlag, moreFlags);
		}
	}

	PodioAttribute(final Key tracKey, final String prettyName, final String taskKey, final String type) {
		this(tracKey, prettyName, taskKey, type, null);
	}

	public String getTaskKey() {
		return taskKey;
	}

	public String getPodioKey() {
		return podioKey;
	}

	public String getKind() {
		if (flags.contains(Flag.ATTRIBUTE)) {
			return TaskAttribute.KIND_DEFAULT;
		} else if (flags.contains(Flag.PEOPLE)) {
			return TaskAttribute.KIND_PEOPLE;
		}
		return null;
	}

	public String getType() {
		return type;
	}

	public boolean isReadOnly() {
		return flags.contains(Flag.READ_ONLY);
	}

	@Override
	public String toString() {
		return prettyName;
	}

	public enum Key {
		CREATED_ON("createdOn"), CREATED_BY("createdBy"), CHANGED_ON("changedOn"), CHANGED_BY("changedby"),
		TITLE("title"), ID("id"), TAGS("tags"), LINK("link"), RATING("rating"), COMMENTS("comments"),
		TYPE("type"), SUMMARY("summary");

		public static Key fromKey(final String name) {
			for (Key key : Key.values()) {
				if (key.getKey().equals(name)) {
					return key;
				}
			}
			return null;
		}

		private String key;

		Key(final String key) {
			this.key = key;
		}

		@Override
		public String toString() {
			return key;
		}

		public String getKey() {
			return key;
		}
	};

}
