package com.partnerportal.service.domain;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Absolute links into the portal that go into emails.
 */
@Component
public class Links {

	public Links(
		@Value("${partner.portal.url:${com.liferay.lxc.dxp.server.protocol}://${com.liferay.lxc.dxp.mainDomain}}")
		String portalURL,
		@Value("${partner.registration.path:/web/partners/register}")
		String registrationPath,
		@Value("${partner.invitations.path:/web/partners/invitations}")
		String invitationsPath,
		@Value("${partner.review.path:/web/partners/review}") String reviewPath) {

		_portalURL = portalURL.endsWith("/") ?
			portalURL.substring(0, portalURL.length() - 1) : portalURL;
		_registrationPath = registrationPath;
		_invitationsPath = invitationsPath;
		_reviewPath = reviewPath;
	}

	public String application(long applicationId) {
		return _portalURL + _reviewPath + "?applicationId=" + applicationId;
	}

	public String invitations() {
		return _portalURL + _invitationsPath;
	}

	public String portal() {
		return _portalURL;
	}

	public String registration(String token) {
		return _portalURL + _registrationPath + "?t=" +
			URLEncoder.encode(token, StandardCharsets.UTF_8);
	}

	private final String _invitationsPath;
	private final String _portalURL;
	private final String _registrationPath;
	private final String _reviewPath;

}
