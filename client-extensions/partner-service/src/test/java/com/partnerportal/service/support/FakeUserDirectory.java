package com.partnerportal.service.support;

import com.partnerportal.service.bootstrap.RoleBootstrap;
import com.partnerportal.service.domain.UserDirectory;
import com.partnerportal.service.domain.UserInfo;
import com.partnerportal.service.liferay.LiferayException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FakeUserDirectory implements UserDirectory {

	public static final long ADMIN = 1;

	public static final long AM = 2;

	public static final long AM_OTHER = 3;

	public static final long LEGAL = 4;

	public static final long NOBODY = 5;

	public static FakeUserDirectory standard() {
		FakeUserDirectory fakeUserDirectory = new FakeUserDirectory();

		fakeUserDirectory.add(
			ADMIN, "Ada Admin", "admin@example.com", RoleBootstrap.ROLE_ADMIN);
		fakeUserDirectory.add(
			AM, "Max Manager", "am@example.com", RoleBootstrap.ROLE_AM);
		fakeUserDirectory.add(
			AM_OTHER, "Olga Other", "am2@example.com", RoleBootstrap.ROLE_AM);
		fakeUserDirectory.add(
			LEGAL, "Lea Legal", "legal@example.com", RoleBootstrap.ROLE_LEGAL);
		fakeUserDirectory.add(NOBODY, "Nora Nobody", "nobody@example.com");

		return fakeUserDirectory;
	}

	public UserInfo add(
		long id, String name, String email, String... roleNames) {

		UserInfo userInfo = new UserInfo(
			id, name, email, new HashSet<>(Arrays.asList(roleNames)));

		users.put(id, userInfo);

		return userInfo;
	}

	@Override
	public UserInfo getUser(long userId) {
		UserInfo userInfo = users.get(userId);

		if (userInfo == null) {
			throw new LiferayException(
				"GET", "/user-accounts/" + userId, 404, "not found");
		}

		return userInfo;
	}

	@Override
	public List<UserInfo> getUsersWithRole(String roleName) {
		return users.values(
		).stream(
		).filter(
			userInfo -> userInfo.getRoleNames(
			).contains(
				roleName
			)
		).collect(
			Collectors.toList()
		);
	}

	public final Map<Long, UserInfo> users = new LinkedHashMap<>();

}
