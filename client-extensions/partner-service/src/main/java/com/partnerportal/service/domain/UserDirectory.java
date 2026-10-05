package com.partnerportal.service.domain;

import java.util.List;

/**
 * Reads Liferay users and their roles.
 */
public interface UserDirectory {

	UserInfo getUser(long userId);

	List<UserInfo> getUsersWithRole(String roleName);

}
