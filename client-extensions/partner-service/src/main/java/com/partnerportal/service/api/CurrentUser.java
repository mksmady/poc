package com.partnerportal.service.api;

import com.partnerportal.service.domain.UserDirectory;
import com.partnerportal.service.domain.UserInfo;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resolves the Liferay user behind a user-agent OAuth token. Liferay puts
 * the user id in the JWT subject.
 */
@Component
public class CurrentUser {

	public static long userId(Jwt jwt) {
		if (jwt == null) {
			throw ApiException.forbidden("Sign in to continue");
		}

		try {
			return Long.parseLong(jwt.getSubject());
		}
		catch (NumberFormatException numberFormatException) {
			throw ApiException.forbidden("Unrecognized user token");
		}
	}

	public CurrentUser(UserDirectory userDirectory) {
		_userDirectory = userDirectory;
	}

	public UserInfo resolve(Jwt jwt) {
		return _userDirectory.getUser(userId(jwt));
	}

	private final UserDirectory _userDirectory;

}
