package com.partnerportal.service.job;

import com.partnerportal.service.api.ApiException;
import com.partnerportal.service.api.CurrentUser;
import com.partnerportal.service.api.InvitationControllerTest;
import com.partnerportal.service.api.JobController;
import com.partnerportal.service.domain.ApplicationStatus;
import com.partnerportal.service.domain.InvitationService.InvitationResult;
import com.partnerportal.service.domain.MailMessage;
import com.partnerportal.service.liferay.Entries;
import com.partnerportal.service.liferay.Fields;
import com.partnerportal.service.support.FakeUserDirectory;
import com.partnerportal.service.support.TestContext;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * US-001 AC8 and AC9 and the SLA reminder, on a fake clock.
 */
public class DailyJobTest {

	@Test
	public void testAc8RemindersOnConfiguredDaysOnlyOncePerDay() {
		InvitationResult result = _invite("jane@example.com");

		for (int day = 1; day <= 29; day++) {
			_ctx.advance(Duration.ofDays(1));

			_job.run();
			_job.run();
		}

		Assertions.assertEquals(
			3, _ctx.mailer.byTemplate("PM_INVITE_REMINDER").size());
		Assertions.assertEquals(
			3,
			Entries.integer(
				_ctx.repository.invitations.get(result.invitationId),
				"reminderCount", 0));

		MailMessage reminder = _ctx.mailer.byTemplate(
			"PM_INVITE_REMINDER"
		).get(
			0
		);

		Assertions.assertEquals("jane@example.com", reminder.getTo());
		Assertions.assertEquals(
			"Max Manager", reminder.getValues().get("AM_NAME"));
	}

	@Test
	public void testAc8NoRemindersAfterSubmit() {
		InvitationResult result = _invite("jane@example.com");

		_ctx.statusService.transition(
			result.applicationId, ApplicationStatus.REGISTRATION_IN_PROGRESS, 0,
			null);
		_ctx.statusService.transition(
			result.applicationId, ApplicationStatus.SUBMITTED, 0, null);

		_ctx.advance(Duration.ofDays(7));

		Assertions.assertEquals(0, _job.run().reminders);
	}

	@Test
	public void testAc9ExpireAfterExpiresAtAndNotifyAm() {
		InvitationResult result = _invite("jane@example.com");

		_ctx.advance(Duration.ofDays(29));

		Assertions.assertEquals(0, _job.run().expired);

		_ctx.advance(Duration.ofDays(1));

		Assertions.assertEquals(1, _job.run().expired);
		Assertions.assertEquals(0, _job.run().expired);

		Assertions.assertEquals(
			"expired", _ctx.repository.statusOf(result.applicationId));
		Assertions.assertEquals(
			"expired",
			Entries.picklistKey(
				_ctx.repository.invitations.get(result.invitationId), "state"));

		MailMessage mailMessage = _ctx.mailer.byTemplate(
			"PM_INVITE_EXPIRED"
		).get(
			0
		);

		Assertions.assertEquals("am@example.com", mailMessage.getTo());
		Assertions.assertEquals(
			"jane@example.com", mailMessage.getValues().get("PARTNER_EMAIL"));
	}

	@Test
	public void testFailureOnOneItemAlertsAndContinues() {
		_invite("a@example.com");
		_invite("b@example.com");

		_ctx.mailer.failTemplates.add("PM_INVITE_EXPIRED");

		_ctx.advance(Duration.ofDays(31));

		DailyJob.Report report = _job.run();

		Assertions.assertEquals(2, report.failures);
		Assertions.assertEquals(
			2, _ctx.mailer.byTemplate("PM_ADMIN_ALERT").size());
	}

	@Test
	public void testJobEndpointIsAdminOnly() {
		JobController jobController = new JobController(
			new CurrentUser(_ctx.userDirectory), _job);

		Assertions.assertThrows(
			ApiException.class,
			() -> jobController.runDaily(
				InvitationControllerTest.jwt(FakeUserDirectory.AM)));

		Map<String, Object> report = jobController.runDaily(
			InvitationControllerTest.jwt(FakeUserDirectory.ADMIN));

		Assertions.assertEquals(0, report.get("failures"));
	}

	@Test
	public void testSlaReminderAfterReviewSlaDays() {
		InvitationResult result = _invite("jane@example.com");

		long applicationId = result.applicationId;

		_ctx.statusService.transition(
			applicationId, ApplicationStatus.REGISTRATION_IN_PROGRESS, 0, null);
		_ctx.statusService.transition(
			applicationId, ApplicationStatus.SUBMITTED, 0, null);
		_ctx.statusService.transition(
			applicationId, ApplicationStatus.UNDER_LEGAL_REVIEW, 0, null);

		_ctx.advance(Duration.ofDays(5));

		Assertions.assertEquals(0, _job.run().slaReminders);

		_ctx.advance(Duration.ofDays(1));

		Assertions.assertEquals(1, _job.run().slaReminders);

		// No legal reviewer yet: every Partner Legal user, plus admins

		Assertions.assertEquals(
			2, _ctx.mailer.byTemplate("PM_SLA_REMINDER").size());

		_ctx.repository.patchApplication(
			applicationId,
			Map.of(Fields.APPLICATION_LEGAL_REVIEWER, FakeUserDirectory.LEGAL));

		_ctx.statusService.transition(
			applicationId, ApplicationStatus.CONTRACT_PENDING, 0, null);

		Assertions.assertEquals(0, _job.run().slaReminders);
	}

	private InvitationResult _invite(String email) {
		return _ctx.invitationService.create(
			email, "Jane", "Doe",
			_ctx.userDirectory.getUser(FakeUserDirectory.AM));
	}

	private final TestContext _ctx = new TestContext();
	private final DailyJob _job = new DailyJob(
		_ctx.repository, _ctx.statusService, _ctx.settingsService, _ctx.mailer,
		_ctx.userDirectory, _ctx.alertService, _ctx.links, _ctx.clock);

}
