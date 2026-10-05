package com.partnerportal.service.api;

import com.fasterxml.jackson.databind.node.ObjectNode;

import com.partnerportal.service.actions.InvitationCreatedActionController;
import com.partnerportal.service.liferay.Entries;
import com.partnerportal.service.support.FakeUserDirectory;
import com.partnerportal.service.support.InMemoryPartnerRepository;
import com.partnerportal.service.support.TestContext;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Endpoint tests for US-001: the controllers called directly with a Liferay
 * style JWT (user id in the subject).
 */
public class InvitationControllerTest {

	public static Jwt jwt(long userId) {
		return Jwt.withTokenValue(
			"token"
		).header(
			"alg", "none"
		).subject(
			String.valueOf(userId)
		).build();
	}

	@Test
	public void testCreateListResend() {
		InvitationController.CreateInvitationRequest request =
			new InvitationController.CreateInvitationRequest();

		request.email = "jane@example.com";
		request.firstName = "Jane";
		request.lastName = "Doe";

		ResponseEntity<Map<String, Object>> response = _controller.create(
			jwt(FakeUserDirectory.AM), request);

		Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());

		long invitationId = (Long)response.getBody().get("invitationId");

		List<Map<String, Object>> rows = _controller.list(
			jwt(FakeUserDirectory.AM), null, null);

		Assertions.assertEquals(1, rows.size());

		Map<String, Object> resent = _controller.resend(
			jwt(FakeUserDirectory.AM), invitationId);

		Assertions.assertEquals(invitationId, resent.get("invitationId"));
	}

	@Test
	public void testDuplicateIsConflictWithTheStoryMessage() {
		InvitationController.CreateInvitationRequest request =
			new InvitationController.CreateInvitationRequest();

		request.email = "jane@example.com";
		request.firstName = "Jane";
		request.lastName = "Doe";

		_controller.create(jwt(FakeUserDirectory.AM), request);

		ApiException apiException = Assertions.assertThrows(
			ApiException.class,
			() -> _controller.create(jwt(FakeUserDirectory.AM), request));

		ResponseEntity<Map<String, Object>> response =
			new ApiExceptionHandler().handle(apiException);

		Assertions.assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		Assertions.assertEquals(
			"An active invitation already exists for this email",
			response.getBody().get("title"));
	}

	@Test
	public void testInvitationCreatedActionIsNoOpWhenHashIsSet()
		throws Exception {

		InvitationCreatedActionController actionController =
			new InvitationCreatedActionController(_ctx.invitationService);

		ObjectNode body = (ObjectNode)InMemoryPartnerRepository.OBJECT_MAPPER.readTree(
			"{\"classPK\": 5, \"userId\": 2, \"objectEntry\": {\"values\": " +
				"{\"tokenHash\": \"abc\"}}}");

		Assertions.assertEquals(
			false,
			actionController.post(
				jwt(FakeUserDirectory.AM), body
			).get(
				"completed"
			));

		long invitationId = Entries.id(
			_ctx.repository.createInvitation(
				Map.of(
					"email", "x@example.com", "firstName", "X", "lastName",
					"Y", "state", "active")));

		body = (ObjectNode)InMemoryPartnerRepository.OBJECT_MAPPER.readTree(
			"{\"classPK\": " + invitationId +
				", \"userId\": 2, \"objectEntry\": {\"values\": {}}}");

		Assertions.assertEquals(
			true,
			actionController.post(
				jwt(FakeUserDirectory.AM), body
			).get(
				"completed"
			));
	}

	@Test
	public void testMissingTokenIsForbidden() {
		Assertions.assertThrows(
			ApiException.class, () -> _controller.list(null, null, null));
	}

	private final TestContext _ctx = new TestContext();
	private final InvitationController _controller = new InvitationController(
		new CurrentUser(_ctx.userDirectory), _ctx.invitationService);

}
