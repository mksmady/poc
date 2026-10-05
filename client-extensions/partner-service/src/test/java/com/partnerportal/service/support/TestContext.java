package com.partnerportal.service.support;

import com.partnerportal.service.domain.AlertService;
import com.partnerportal.service.domain.DirectReviewRouter;
import com.partnerportal.service.domain.InvitationService;
import com.partnerportal.service.domain.Links;
import com.partnerportal.service.domain.RegistrationService;
import com.partnerportal.service.domain.RegistrationSessions;
import com.partnerportal.service.domain.SettingsService;
import com.partnerportal.service.domain.StatusService;
import com.partnerportal.service.domain.TokenService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;

/**
 * Wires the domain services over in-memory fakes and a clock tests can move.
 */
public class TestContext {

	public static final Instant START = Instant.parse("2026-10-04T08:00:00Z");

	public TestContext() {
		repository.settings.put("invitationExpiryDays", 30);
		repository.settings.put("reminderDays", "7,21,27");
		repository.settings.put("reviewSlaDays", 5);
		repository.settings.put("revisionLinkDays", 14);
		repository.settings.put("activationLinkHours", 72);
		repository.settings.put("maxUploadMb", 10);
	}

	public void advance(Duration duration) {
		clock.instant = clock.instant.plus(duration);
	}

	public final MutableClock clock = new MutableClock(START);
	public final InMemoryPartnerRepository repository =
		new InMemoryPartnerRepository();
	public final FakeMailer mailer = new FakeMailer();
	public final FakeUserDirectory userDirectory = FakeUserDirectory.standard();
	public final Links links = new Links(
		"https://portal.example.com", "/web/partners/register",
		"/web/partners/invitations", "/web/partners/review");
	public final TokenService tokenService = new TokenService();
	public final SettingsService settingsService = new SettingsService(
		repository);
	public final StatusService statusService = new StatusService(
		repository, clock);
	public final AlertService alertService = new AlertService(
		mailer, userDirectory);
	public final InvitationService invitationService = new InvitationService(
		repository, statusService, settingsService, tokenService, mailer,
		userDirectory, alertService, links, clock);
	public final FakeDocumentStore documentStore = new FakeDocumentStore();
	public final RegistrationSessions registrationSessions =
		new RegistrationSessions(
			Base64.getEncoder(
			).encodeToString(
				new byte[32]
			),
			60, clock);
	public final DirectReviewRouter reviewRouter = new DirectReviewRouter(
		repository, statusService);
	public final RegistrationService registrationService =
		new RegistrationService(
			repository, statusService, settingsService, tokenService,
			registrationSessions, documentStore, reviewRouter, mailer,
			userDirectory, alertService, links, clock,
			"networking=Networking,security=Security");

	public static class MutableClock extends Clock {

		public MutableClock(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Instant instant() {
			return instant;
		}

		@Override
		public Clock withZone(ZoneId zoneId) {
			return this;
		}

		public Instant instant;

	}

}
