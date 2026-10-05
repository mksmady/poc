package com.partnerportal.service.job;

import com.fasterxml.jackson.databind.node.ObjectNode;

import com.partnerportal.service.bootstrap.RoleBootstrap;
import com.partnerportal.service.domain.AlertService;
import com.partnerportal.service.domain.Dates;
import com.partnerportal.service.domain.ApplicationStatus;
import com.partnerportal.service.domain.InvitationService;
import com.partnerportal.service.domain.Links;
import com.partnerportal.service.domain.MailMessage;
import com.partnerportal.service.domain.Mailer;
import com.partnerportal.service.domain.PartnerRepository;
import com.partnerportal.service.domain.PartnerSettings;
import com.partnerportal.service.domain.SettingsService;
import com.partnerportal.service.domain.StatusService;
import com.partnerportal.service.domain.UserDirectory;
import com.partnerportal.service.domain.UserInfo;
import com.partnerportal.service.liferay.Entries;
import com.partnerportal.service.liferay.Fields;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.stereotype.Component;

/**
 * The daily run (US-001 AC8, AC9; architecture "Daily job"): expire overdue
 * invitations, send invitation reminders on {@code reminderDays}, send SLA
 * reminders for reviews waiting longer than {@code reviewSlaDays}. One item
 * failing raises {@code PM_ADMIN_ALERT} and does not stop the others.
 */
@Component
public class DailyJob {

	public static final Set<ApplicationStatus> OPEN_INVITATION = EnumSet.of(
		ApplicationStatus.INVITATION_SENT,
		ApplicationStatus.REGISTRATION_IN_PROGRESS);

	public static final Set<ApplicationStatus> UNDER_REVIEW = EnumSet.of(
		ApplicationStatus.APPROVAL_PENDING, ApplicationStatus.CONTRACT_PENDING,
		ApplicationStatus.UNDER_LEGAL_REVIEW);

	public DailyJob(
		PartnerRepository partnerRepository, StatusService statusService,
		SettingsService settingsService, Mailer mailer,
		UserDirectory userDirectory, AlertService alertService, Links links,
		Clock clock) {

		_partnerRepository = partnerRepository;
		_statusService = statusService;
		_settingsService = settingsService;
		_mailer = mailer;
		_userDirectory = userDirectory;
		_alertService = alertService;
		_links = links;
		_clock = clock;
	}

	public synchronized Report run() {
		Report report = new Report();

		Instant now = Instant.now(_clock);

		PartnerSettings partnerSettings = _settingsService.get();

		Map<Long, ObjectNode> applicationsByInvitation = new HashMap<>();

		for (ObjectNode application : _partnerRepository.listApplications()) {
			applicationsByInvitation.put(
				Entries.longValue(application, Fields.APPLICATION_INVITATION),
				application);
		}

		for (ObjectNode invitation : _partnerRepository.listInvitations(0)) {
			ObjectNode application = applicationsByInvitation.get(
				Entries.id(invitation));

			if ((application == null) ||
				!OPEN_INVITATION.contains(
					_statusService.getStatus(application))) {

				continue;
			}

			try {
				Instant expiresAt = Entries.instant(invitation, "expiresAt");

				if ((expiresAt != null) && !now.isBefore(expiresAt)) {
					_expire(invitation, application, expiresAt);

					report.expired++;
				}
				else if (_remind(invitation, application, partnerSettings, now)) {
					report.reminders++;
				}
			}
			catch (RuntimeException runtimeException) {
				report.failures++;

				_alertService.alert(
					"Daily job failed for an invitation",
					"Invitation " + Entries.id(invitation) +
						" could not be expired or reminded.",
					runtimeException);
			}
		}

		for (ObjectNode application :
				_partnerRepository.findApplicationsByStatus(UNDER_REVIEW)) {

			try {
				if (_slaReminder(application, partnerSettings, now)) {
					report.slaReminders++;
				}
			}
			catch (RuntimeException runtimeException) {
				report.failures++;

				_alertService.alert(
					"Daily job failed for an application",
					"SLA check failed for application " +
						Entries.id(application) + ".",
					runtimeException);
			}
		}

		if (_log.isInfoEnabled()) {
			_log.info("Daily job: " + report);
		}

		return report;
	}

	public static class Report {

		public Map<String, Object> toMap() {
			Map<String, Object> map = new LinkedHashMap<>();

			map.put("expired", expired);
			map.put("reminders", reminders);
			map.put("slaReminders", slaReminders);
			map.put("failures", failures);

			return map;
		}

		@Override
		public String toString() {
			return toMap().toString();
		}

		public int expired;
		public int failures;
		public int reminders;
		public int slaReminders;

	}

	private void _expire(
		ObjectNode invitation, ObjectNode application, Instant expiresAt) {

		long applicationId = Entries.id(application);

		_statusService.transition(
			applicationId, ApplicationStatus.EXPIRED, 0,
			"Invitation expired without a submitted application");

		_partnerRepository.patchInvitation(
			Entries.id(invitation),
			Map.of("state", InvitationService.STATE_EXPIRED));

		long amUserId = Entries.longValue(
			application, Fields.APPLICATION_ASSIGNED_AM);

		if (amUserId == 0) {
			return;
		}

		UserInfo am = _userDirectory.getUser(amUserId);

		_mailer.send(
			MailMessage.of(
				"PM_INVITE_EXPIRED", am.getEmailAddress()
			).value(
				"PARTNER_NAME", _partnerName(invitation)
			).value(
				"PARTNER_EMAIL", Entries.text(invitation, "email")
			).value(
				"EXPIRES_AT", _formatDate(expiresAt)
			).value(
				"LINK", _links.invitations()
			));
	}

	private String _formatDate(Instant instant) {
		return Dates.format(instant);
	}

	private String _partnerName(ObjectNode invitation) {
		return Entries.text(invitation, "firstName") + " " +
			Entries.text(invitation, "lastName");
	}

	/**
	 * Sends a reminder when today is day N after {@code sentAt} for an N in
	 * {@code reminderDays}, and none was sent today.
	 */
	private boolean _remind(
		ObjectNode invitation, ObjectNode application,
		PartnerSettings partnerSettings, Instant now) {

		Instant sentAt = Entries.instant(invitation, "sentAt");

		if (sentAt == null) {
			return false;
		}

		long day = Duration.between(
			sentAt, now
		).toDays();

		if (!partnerSettings.getReminderDays(
			).contains(
				(int)day
			)) {

			return false;
		}

		Instant lastReminderAt = Entries.instant(invitation, "lastReminderAt");

		LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);

		if ((lastReminderAt != null) &&
			LocalDate.ofInstant(
				lastReminderAt, ZoneOffset.UTC
			).equals(
				today
			)) {

			return false;
		}

		String amName = "";

		long amUserId = Entries.longValue(
			application, Fields.APPLICATION_ASSIGNED_AM);

		if (amUserId != 0) {
			amName = _userDirectory.getUser(
				amUserId
			).getName();
		}

		_mailer.send(
			MailMessage.of(
				"PM_INVITE_REMINDER", Entries.text(invitation, "email")
			).value(
				"PARTNER_NAME", _partnerName(invitation)
			).value(
				"AM_NAME", amName
			).value(
				"EXPIRES_AT",
				_formatDate(Entries.instant(invitation, "expiresAt"))
			));

		Map<String, Object> fields = new LinkedHashMap<>();

		fields.put("lastReminderAt", Entries.dateTime(now));
		fields.put(
			"reminderCount", Entries.integer(invitation, "reminderCount", 0) + 1);

		_partnerRepository.patchInvitation(Entries.id(invitation), fields);

		return true;
	}

	private boolean _slaReminder(
		ObjectNode application, PartnerSettings partnerSettings, Instant now) {

		long applicationId = Entries.id(application);

		Instant lastChange = null;

		for (ObjectNode row :
				_partnerRepository.findStatusHistory(applicationId)) {

			Instant changedAt = Entries.instant(row, "changedAt");

			if ((changedAt != null) &&
				((lastChange == null) || changedAt.isAfter(lastChange))) {

				lastChange = changedAt;
			}
		}

		if (lastChange == null) {
			return false;
		}

		long waitedDays = Duration.between(
			lastChange, now
		).toDays();

		if (waitedDays <= partnerSettings.getReviewSlaDays()) {
			return false;
		}

		ApplicationStatus applicationStatus = _statusService.getStatus(
			application);

		Set<String> recipients = new LinkedHashSet<>();

		if (applicationStatus == ApplicationStatus.UNDER_LEGAL_REVIEW) {
			long legalUserId = Entries.longValue(
				application, Fields.APPLICATION_LEGAL_REVIEWER);

			if (legalUserId != 0) {
				recipients.add(
					_userDirectory.getUser(
						legalUserId
					).getEmailAddress());
			}
			else {
				for (UserInfo userInfo :
						_userDirectory.getUsersWithRole(
							RoleBootstrap.ROLE_LEGAL)) {

					recipients.add(userInfo.getEmailAddress());
				}
			}
		}
		else {
			long amUserId = Entries.longValue(
				application, Fields.APPLICATION_ASSIGNED_AM);

			if (amUserId != 0) {
				recipients.add(
					_userDirectory.getUser(
						amUserId
					).getEmailAddress());
			}
		}

		for (UserInfo userInfo :
				_userDirectory.getUsersWithRole(RoleBootstrap.ROLE_ADMIN)) {

			recipients.add(userInfo.getEmailAddress());
		}

		List<RuntimeException> failures = new ArrayList<>();

		for (String recipient : recipients) {
			try {
				_mailer.send(
					MailMessage.of(
						"PM_SLA_REMINDER", recipient
					).value(
						"COMPANY_NAME", Entries.text(application, "companyName")
					).value(
						"DAYS", String.valueOf(waitedDays)
					).value(
						"STATUS", applicationStatus.key()
					).value(
						"SLA_DAYS",
						String.valueOf(partnerSettings.getReviewSlaDays())
					).value(
						"LINK", _links.application(applicationId)
					));
			}
			catch (RuntimeException runtimeException) {
				failures.add(runtimeException);
			}
		}

		if (!failures.isEmpty()) {
			throw failures.get(0);
		}

		return true;
	}

	private static final Log _log = LogFactory.getLog(DailyJob.class);

	private final AlertService _alertService;
	private final Clock _clock;
	private final Links _links;
	private final Mailer _mailer;
	private final PartnerRepository _partnerRepository;
	private final SettingsService _settingsService;
	private final StatusService _statusService;
	private final UserDirectory _userDirectory;

}
