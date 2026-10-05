package com.partnerportal.service.api;

import com.partnerportal.service.domain.InvitationService;
import com.partnerportal.service.domain.InvitationService.InvitationResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * US-001 for {@code partner-admin-ui}, called with the user-agent OAuth app as
 * the signed-in account manager or admin.
 */
@RequestMapping("/api/invitations")
@RestController
public class InvitationController {

	public InvitationController(
		CurrentUser currentUser, InvitationService invitationService) {

		_currentUser = currentUser;
		_invitationService = invitationService;
	}

	@PostMapping
	public ResponseEntity<Map<String, Object>> create(
		@AuthenticationPrincipal Jwt jwt,
		@RequestBody CreateInvitationRequest createInvitationRequest) {

		InvitationResult invitationResult = _invitationService.create(
			createInvitationRequest.email, createInvitationRequest.firstName,
			createInvitationRequest.lastName, _currentUser.resolve(jwt));

		return ResponseEntity.status(
			HttpStatus.CREATED
		).body(
			_toMap(invitationResult)
		);
	}

	@GetMapping
	public List<Map<String, Object>> list(
		@AuthenticationPrincipal Jwt jwt,
		@RequestParam(required = false) String search,
		@RequestParam(required = false) String tab) {

		return _invitationService.list(_currentUser.resolve(jwt), tab, search);
	}

	@PostMapping("/{invitationId}/resend")
	public Map<String, Object> resend(
		@AuthenticationPrincipal Jwt jwt, @PathVariable long invitationId) {

		return _toMap(
			_invitationService.resend(
				invitationId, _currentUser.resolve(jwt)));
	}

	public static class CreateInvitationRequest {

		public String email;
		public String firstName;
		public String lastName;

	}

	private Map<String, Object> _toMap(InvitationResult invitationResult) {
		Map<String, Object> map = new LinkedHashMap<>();

		map.put("invitationId", invitationResult.invitationId);
		map.put("applicationId", invitationResult.applicationId);
		map.put("expiresAt", invitationResult.expiresAt.toString());
		map.put("emailSent", invitationResult.emailSent);
		map.put("warnings", invitationResult.warnings);

		return map;
	}

	private final CurrentUser _currentUser;
	private final InvitationService _invitationService;

}
