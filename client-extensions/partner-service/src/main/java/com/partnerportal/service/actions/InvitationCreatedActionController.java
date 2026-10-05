package com.partnerportal.service.actions;

import com.fasterxml.jackson.databind.JsonNode;

import com.partnerportal.service.domain.InvitationService;

import java.util.Collections;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Object action {@code PM_ACTION_INVITATION_CREATED} (On After Add on
 * PartnerInvitation). Invitations created through partner-service already
 * carry a token hash, so this only completes invitations created elsewhere.
 */
@RequestMapping("/object/action/invitation-created")
@RestController
public class InvitationCreatedActionController {

	public InvitationCreatedActionController(
		InvitationService invitationService) {

		_invitationService = invitationService;
	}

	@PostMapping
	public Map<String, Object> post(
		@AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode body) {

		ObjectActionPayload objectActionPayload = new ObjectActionPayload(body);

		String tokenHash = objectActionPayload.getValue(
			"tokenHash"
		).asText(
			""
		);

		if (!tokenHash.isEmpty()) {
			return Collections.singletonMap("completed", false);
		}

		boolean completed = _invitationService.completeSetup(
			objectActionPayload.getEntryId(), objectActionPayload.getUserId());

		if (_log.isInfoEnabled()) {
			_log.info(
				"Invitation " + objectActionPayload.getEntryId() +
					(completed ? " completed" : " already set up"));
		}

		return Collections.singletonMap("completed", completed);
	}

	private static final Log _log = LogFactory.getLog(
		InvitationCreatedActionController.class);

	private final InvitationService _invitationService;

}
