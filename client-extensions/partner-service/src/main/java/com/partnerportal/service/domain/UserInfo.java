package com.partnerportal.service.domain;

import com.partnerportal.service.bootstrap.RoleBootstrap;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A Liferay user as partner-service needs it: identity plus regular role
 * names.
 */
public class UserInfo {

	public static final String ROLE_LIFERAY_ADMINISTRATOR = "Administrator";

	public UserInfo(
		long id, String name, String emailAddress, Set<String> roleNames) {

		_id = id;
		_name = name;
		_emailAddress = emailAddress;
		_roleNames = Collections.unmodifiableSet(
			new LinkedHashSet<>(roleNames));
	}

	public String getEmailAddress() {
		return _emailAddress;
	}

	public long getId() {
		return _id;
	}

	public String getName() {
		return _name;
	}

	public Set<String> getRoleNames() {
		return _roleNames;
	}

	/**
	 * Partner Portal Admin, or the Liferay Administrator role.
	 */
	public boolean isAdmin() {
		if (_roleNames.contains(RoleBootstrap.ROLE_ADMIN) ||
			_roleNames.contains(ROLE_LIFERAY_ADMINISTRATOR)) {

			return true;
		}

		return false;
	}

	public boolean isAm() {
		return _roleNames.contains(RoleBootstrap.ROLE_AM);
	}

	public boolean isLegal() {
		return _roleNames.contains(RoleBootstrap.ROLE_LEGAL);
	}

	private final String _emailAddress;
	private final long _id;
	private final String _name;
	private final Set<String> _roleNames;

}
