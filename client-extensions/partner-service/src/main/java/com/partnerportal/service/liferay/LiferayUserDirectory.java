package com.partnerportal.service.liferay;

import com.fasterxml.jackson.databind.JsonNode;

import com.partnerportal.service.domain.UserDirectory;
import com.partnerportal.service.domain.UserInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class LiferayUserDirectory implements UserDirectory {

	public LiferayUserDirectory(LiferayClient liferayClient) {
		_liferayClient = liferayClient;
	}

	@Override
	public UserInfo getUser(long userId) {
		return _toUserInfo(
			_liferayClient.get(
				_USER_ACCOUNTS + "/" + userId + "?fields=" + _FIELDS));
	}

	@Override
	public List<UserInfo> getUsersWithRole(String roleName) {
		List<UserInfo> userInfos = new ArrayList<>();

		for (int page = 1;; page++) {
			JsonNode jsonNode = _liferayClient.get(
				_USER_ACCOUNTS + "?fields=" + _FIELDS + "&pageSize=100&page=" +
					page + "&filter=" +
						Urls.param(
							"roleNames/any(r:r eq " +
								LiferayPartnerRepository.quote(roleName) +
									")"));

			for (JsonNode item : jsonNode.path("items")) {
				userInfos.add(_toUserInfo(item));
			}

			if (page >= jsonNode.path(
					"lastPage"
				).asInt(
					1
				)) {

				return userInfos;
			}
		}
	}

	private UserInfo _toUserInfo(JsonNode jsonNode) {
		Set<String> roleNames = new LinkedHashSet<>();

		for (JsonNode roleBrief : jsonNode.path("roleBriefs")) {
			roleNames.add(
				roleBrief.path(
					"name"
				).asText());
		}

		return new UserInfo(
			jsonNode.path(
				"id"
			).asLong(),
			jsonNode.path(
				"name"
			).asText(),
			jsonNode.path(
				"emailAddress"
			).asText(),
			roleNames);
	}

	private static final String _FIELDS = "id,name,emailAddress,roleBriefs";

	private static final String _USER_ACCOUNTS =
		"/o/headless-admin-user/v1.0/user-accounts";

	private final LiferayClient _liferayClient;

}
