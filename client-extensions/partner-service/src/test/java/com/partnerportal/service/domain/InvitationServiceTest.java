package com.partnerportal.service.domain;

import com.fasterxml.jackson.databind.node.ObjectNode;

import com.partnerportal.service.api.ApiException;
import com.partnerportal.service.domain.InvitationService.InvitationResult;
import com.partnerportal.service.liferay.Entries;
import com.partnerportal.service.liferay.Fields;
import com.partnerportal.service.support.FakeUserDirectory;
import com.partnerportal.service.support.TestContext;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

public class InvitationServiceTest {

	@Test
	public void testAc1CreatesInvitationAndApplication() {
		InvitationResult result = _create("Jane@Example.com ");

		ObjectNode invitation = _ctx.repository.invitations.get(
			result.invitationId);

		Assertions.assertEquals("jane@example.com", Entries.text(invitation, "email"));
		Assertions.assertEquals("active", Entries.picklistKey(invitation, "state"));
		Assertions.assertEquals(
			FakeUserDirectory.AM,
			Entries.longValue(invitation, Fields.INVITATION_INVITED_BY));
		Assertions.assertEquals(
			"invitationSent", _ctx.repository.statusOf(result.applicationId));

		ObjectNode application = _ctx.repository.applications.get(
			result.applicationId);

		Assertions.assertEquals(
			result.invitationId,
			Entries.longValue(application, Fields.APPLICATION_INVITATION));
		Assertions.assertEquals(
			FakeUserDirectory.AM,
			Entries.longValue(application, Fields.APPLICATION_ASSIGNED_AM));
		Assertions.assertEquals(
			1, _ctx.repository.historyOf(
				result.applicationId
			).size());
	}

	@Test
	public void testAc1RequiresFieldsAndRole() {
		_assertStatus(
			HttpStatus.BAD_REQUEST,
			() -> _ctx.invitationService.create("not-an-email", "A", "B", _am()));
		_assertStatus(
			HttpStatus.BAD_REQUEST,
			() -> _ctx.invitationService.create("a@b.com", " ", "B", _am()));
		_assertStatus(
			HttpStatus.FORBIDDEN,
			() -> _ctx.invitationService.create(
				"a@b.com", "A", "B",
				_ctx.userDirectory.getUser(FakeUserDirectory.NOBODY)));
	}

	@Test
	public void testAc2DuplicateAndWatchlist() {
		_create("jane@example.com");

		ApiException apiException = Assertions.assertThrows(
			ApiException.class, () -> _create("JANE@example.com"));

		Assertions.assertEquals(HttpStatus.CONFLICT, apiException.getHttpStatus());
		Assertions.assertEquals(
			InvitationService.DUPLICATE_MESSAGE, apiException.getMessage());

		Map<String, Object> fields = new LinkedHashMap<>();

		fields.put("applicationStatus", "terminated");
		fields.put("companyName", "Bad Corp");
		fields.put("website", "https://www.badcorp.com/about");

		_ctx.repository.createApplication(fields);

		InvitationResult result = _create("joe@badcorp.com");

		Assertions.assertEquals(1, result.warnings.size());
		Assertions.assertTrue(result.warnings.get(0).contains("Bad Corp"));
	}

	@Test
	public void testAc3Ac5TokenOnlyHashedAndEmailed() {
		InvitationResult result = _create("jane@example.com");

		MailMessage mailMessage = _ctx.mailer.last();

		Assertions.assertEquals("PM_INVITE", mailMessage.getTemplateERC());
		Assertions.assertEquals("jane@example.com", mailMessage.getTo());
		Assertions.assertEquals(
			"Jane Doe", mailMessage.getValues().get("PARTNER_NAME"));
		Assertions.assertEquals(
			"Max Manager", mailMessage.getValues().get("AM_NAME"));
		Assertions.assertEquals(
			"3 November 2026, 08:00 UTC",
			mailMessage.getValues().get("EXPIRES_AT"));

		String link = mailMessage.getValues().get("LINK");

		Assertions.assertTrue(
			link.startsWith("https://portal.example.com/web/partners/register?t="));

		String token = link.substring(link.indexOf("?t=") + 3);

		ObjectNode invitation = _ctx.repository.invitations.get(
			result.invitationId);

		Assertions.assertEquals(
			_ctx.tokenService.hash(token), Entries.text(invitation, "tokenHash"));
		Assertions.assertFalse(invitation.toString().contains(token));
	}

	@Test
	public void testAc4ExpiryFollowsTheSettingForNewInvitationsOnly() {
		InvitationResult first = _create("a@example.com");

		_ctx.repository.settings.put("invitationExpiryDays", 10);

		InvitationResult second = _create("b@example.com");

		Assertions.assertEquals(
			TestContext.START.plus(Duration.ofDays(30)), first.expiresAt);
		Assertions.assertEquals(
			TestContext.START.plus(Duration.ofDays(10)), second.expiresAt);
		Assertions.assertEquals(
			first.expiresAt,
			Entries.instant(
				_ctx.repository.invitations.get(first.invitationId),
				"expiresAt"));
	}

	@Test
	public void testAc5MailFailureAlertsAdminsButKeepsInvitation() {
		_ctx.mailer.failTemplates.add("PM_INVITE");

		InvitationResult result = _create("jane@example.com");

		Assertions.assertFalse(result.emailSent);
		Assertions.assertNotNull(
			_ctx.repository.invitations.get(result.invitationId));
		Assertions.assertEquals(
			"admin@example.com",
			_ctx.mailer.byTemplate(
				"PM_ADMIN_ALERT"
			).get(
				0
			).getTo());
	}

	@Test
	public void testAc6ResendRevokesOldTokenAndReopensExpired() {
		InvitationResult result = _create("jane@example.com");

		String oldHash = Entries.text(
			_ctx.repository.invitations.get(result.invitationId), "tokenHash");

		_ctx.statusService.transition(
			result.applicationId, ApplicationStatus.EXPIRED, 0, "expired");

		_ctx.repository.patchApplication(
			result.applicationId, Map.of("companyName", "Draft Co"));

		_ctx.advance(Duration.ofDays(40));

		InvitationResult resent = _ctx.invitationService.resend(
			result.invitationId, _am());

		ObjectNode invitation = _ctx.repository.invitations.get(
			result.invitationId);

		Assertions.assertNotEquals(oldHash, Entries.text(invitation, "tokenHash"));
		Assertions.assertEquals(1, Entries.integer(invitation, "resendCount", 0));
		Assertions.assertEquals(
			TestContext.START.plus(Duration.ofDays(70)), resent.expiresAt);
		Assertions.assertEquals(
			"invitationSent", _ctx.repository.statusOf(result.applicationId));
		Assertions.assertEquals(
			"Draft Co",
			Entries.text(
				_ctx.repository.applications.get(result.applicationId),
				"companyName"));
		Assertions.assertEquals(2, _ctx.mailer.byTemplate("PM_INVITE").size());
	}

	@Test
	public void testAc6ResendRules() {
		InvitationResult result = _create("jane@example.com");

		_assertStatus(
			HttpStatus.FORBIDDEN,
			() -> _ctx.invitationService.resend(
				result.invitationId,
				_ctx.userDirectory.getUser(FakeUserDirectory.AM_OTHER)));

		_ctx.invitationService.resend(
			result.invitationId,
			_ctx.userDirectory.getUser(FakeUserDirectory.ADMIN));

		_ctx.statusService.transition(
			result.applicationId, ApplicationStatus.REGISTRATION_IN_PROGRESS, 0,
			null);
		_ctx.statusService.transition(
			result.applicationId, ApplicationStatus.SUBMITTED, 0, null);

		_assertStatus(
			HttpStatus.CONFLICT,
			() -> _ctx.invitationService.resend(result.invitationId, _am()));
	}

	@Test
	public void testAc7DashboardTabsSearchAndOwnership() {
		InvitationResult soon = _create("soon@example.com");

		_ctx.repository.patchInvitation(
			soon.invitationId,
			Map.of(
				"expiresAt",
				TestContext.START.plus(
					Duration.ofDays(3)
				).toString()));

		InvitationResult expired = _create("old@example.com");

		_ctx.statusService.transition(
			expired.applicationId, ApplicationStatus.EXPIRED, 0, null);

		_ctx.invitationService.create(
			"other@example.com", "Other", "Person",
			_ctx.userDirectory.getUser(FakeUserDirectory.AM_OTHER));

		Assertions.assertEquals(2, _list(_am(), "all", null).size());
		Assertions.assertEquals(
			3,
			_list(
				_ctx.userDirectory.getUser(FakeUserDirectory.ADMIN), "all", null
			).size());
		Assertions.assertEquals(1, _list(_am(), "expiring", null).size());
		Assertions.assertEquals(1, _list(_am(), "expired", null).size());
		Assertions.assertEquals(1, _list(_am(), "invitationSent", null).size());
		Assertions.assertEquals(0, _list(_am(), "inProgress", null).size());
		Assertions.assertEquals(1, _list(_am(), "all", "OLD@").size());

		Map<String, Object> row = _list(_am(), "expired", null).get(0);

		Assertions.assertFalse(row.containsKey("tokenHash"));
		Assertions.assertEquals(true, row.get("resendable"));
	}

	@Test
	public void testCompleteSetupForInvitationCreatedOutsideTheService() {
		Map<String, Object> fields = new LinkedHashMap<>();

		fields.put("email", "Ext@Example.com");
		fields.put("expiresAt", "2030-01-01T00:00:00Z");
		fields.put("firstName", "Ext");
		fields.put("lastName", "Ernal");
		fields.put("state", "active");

		long invitationId = Entries.id(
			_ctx.repository.createInvitation(fields));

		Assertions.assertTrue(
			_ctx.invitationService.completeSetup(
				invitationId, FakeUserDirectory.AM));
		Assertions.assertFalse(
			_ctx.invitationService.completeSetup(
				invitationId, FakeUserDirectory.AM));

		ObjectNode invitation = _ctx.repository.invitations.get(invitationId);

		Assertions.assertEquals("ext@example.com", Entries.text(invitation, "email"));
		Assertions.assertNotNull(Entries.text(invitation, "tokenHash"));
		Assertions.assertNotNull(
			_ctx.repository.findApplicationByInvitation(invitationId));
		Assertions.assertEquals(1, _ctx.mailer.byTemplate("PM_INVITE").size());
	}

	private UserInfo _am() {
		return _ctx.userDirectory.getUser(FakeUserDirectory.AM);
	}

	private void _assertStatus(HttpStatus httpStatus, Runnable runnable) {
		ApiException apiException = Assertions.assertThrows(
			ApiException.class, runnable::run);

		Assertions.assertEquals(httpStatus, apiException.getHttpStatus());
	}

	private InvitationResult _create(String email) {
		return _ctx.invitationService.create(email, "Jane", "Doe", _am());
	}

	private List<Map<String, Object>> _list(
		UserInfo userInfo, String tab, String search) {

		return _ctx.invitationService.list(userInfo, tab, search);
	}

	private final TestContext _ctx = new TestContext();

}
