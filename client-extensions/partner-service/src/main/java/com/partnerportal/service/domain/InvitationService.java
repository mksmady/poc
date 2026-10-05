package com.partnerportal.service.domain;

import com.fasterxml.jackson.databind.node.ObjectNode;

import com.partnerportal.service.api.ApiException;
import com.partnerportal.service.liferay.Entries;
import com.partnerportal.service.liferay.Fields;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * US-001: create, resend and list invitations.
 */
@Service
public class InvitationService {

	public static final String DUPLICATE_MESSAGE =
		"An active invitation already exists for this email";

	public static final Set<ApplicationStatus> RESENDABLE = Collections.unmodifiableSet(
		EnumSet.of(
			ApplicationStatus.INVITATION_SENT,
			ApplicationStatus.REGISTRATION_IN_PROGRESS,
			ApplicationStatus.EXPIRED));

	public static final String STATE_ACTIVE = "active";

	public static final String STATE_EXPIRED = "expired";

	public static final String STATE_REVOKED = "revoked";

	public static final String STATE_USED = "used";

	public static final String TAB_ALL = "all";

	public static final String TAB_EXPIRED = "expired";

	public static final String TAB_EXPIRING = "expiring";

	public static final String TAB_IN_PROGRESS = "inProgress";

	public static final String TAB_INVITATION_SENT = "invitationSent";

	public InvitationService(
		PartnerRepository partnerRepository, StatusService statusService,
		SettingsService settingsService, TokenService tokenService,
		Mailer mailer, UserDirectory userDirectory, AlertService alertService,
		Links links, Clock clock) {

		_partnerRepository = partnerRepository;
		_statusService = statusService;
		_settingsService = settingsService;
		_tokenService = tokenService;
		_mailer = mailer;
		_userDirectory = userDirectory;
		_alertService = alertService;
		_links = links;
		_clock = clock;
	}

	/**
	 * Called by the {@code invitation-created} object action. Completes an
	 * invitation that was created outside partner-service (Control Panel,
	 * batch); does nothing when partner-service already set it up.
	 */
	public boolean completeSetup(long invitationId, long triggeringUserId) {
		ObjectNode invitation = _partnerRepository.getInvitation(invitationId);

		String tokenHash = Entries.text(invitation, "tokenHash");

		if (((tokenHash != null) && !tokenHash.isEmpty()) ||
			(_partnerRepository.findApplicationByInvitation(invitationId) !=
				null)) {

			return false;
		}

		long amUserId = Entries.longValue(
			invitation, Fields.INVITATION_INVITED_BY);

		if (amUserId == 0) {
			amUserId = triggeringUserId;
		}

		UserInfo am = _userDirectory.getUser(amUserId);

		PartnerSettings partnerSettings = _settingsService.get();

		Instant now = Instant.now(_clock);

		Instant expiresAt = _expiresAt(now, partnerSettings);

		String token = _tokenService.generate();

		Map<String, Object> fields = new LinkedHashMap<>();

		fields.put("email", _normalizeEmail(Entries.text(invitation, "email")));
		fields.put("expiresAt", Entries.dateTime(expiresAt));
		fields.put(Fields.INVITATION_INVITED_BY, amUserId);
		fields.put("reminderCount", 0);
		fields.put("resendCount", 0);
		fields.put("sentAt", Entries.dateTime(now));
		fields.put("state", STATE_ACTIVE);
		fields.put("tokenHash", _tokenService.hash(token));

		invitation = _partnerRepository.patchInvitation(invitationId, fields);

		_createApplication(invitationId, am.getId());

		_sendInvite(invitation, token, expiresAt, am);

		return true;
	}

	public InvitationResult create(
		String email, String firstName, String lastName, UserInfo actor) {

		if (!actor.isAm() && !actor.isAdmin()) {
			throw ApiException.forbidden(
				"Only account managers and admins can send invitations");
		}

		String normalizedEmail = _normalizeEmail(email);

		List<String> errors = new ArrayList<>();

		if ((normalizedEmail == null) ||
			!_EMAIL_PATTERN.matcher(
				normalizedEmail
			).matches()) {

			errors.add("Enter a valid email address");
		}

		if (_isBlank(firstName)) {
			errors.add("First name is required");
		}

		if (_isBlank(lastName)) {
			errors.add("Last name is required");
		}

		if (!errors.isEmpty()) {
			throw ApiException.badRequest(String.join(". ", errors));
		}

		if (!_partnerRepository.findInvitationsByEmailAndState(
				normalizedEmail, STATE_ACTIVE
			).isEmpty()) {

			throw ApiException.conflict(DUPLICATE_MESSAGE);
		}

		List<String> warnings = watchlistWarnings(normalizedEmail);

		PartnerSettings partnerSettings = _settingsService.get();

		Instant now = Instant.now(_clock);

		Instant expiresAt = _expiresAt(now, partnerSettings);

		String token = _tokenService.generate();

		Map<String, Object> fields = new LinkedHashMap<>();

		fields.put("email", normalizedEmail);
		fields.put("expiresAt", Entries.dateTime(expiresAt));
		fields.put("firstName", firstName.trim());
		fields.put(Fields.INVITATION_INVITED_BY, actor.getId());
		fields.put("lastName", lastName.trim());
		fields.put("reminderCount", 0);
		fields.put("resendCount", 0);
		fields.put("sentAt", Entries.dateTime(now));
		fields.put("state", STATE_ACTIVE);
		fields.put("tokenHash", _tokenService.hash(token));

		ObjectNode invitation = _partnerRepository.createInvitation(fields);

		long invitationId = Entries.id(invitation);

		ObjectNode application = _createApplication(
			invitationId, actor.getId());

		boolean emailSent = _sendInvite(invitation, token, expiresAt, actor);

		return new InvitationResult(
			invitationId, Entries.id(application), expiresAt, emailSent,
			warnings);
	}

	/**
	 * Rows for the invitations dashboard (US-001 AC7). Account managers see
	 * the invitations they sent; admins and legal see all.
	 */
	public List<Map<String, Object>> list(
		UserInfo actor, String tab, String search) {

		if (!actor.isAm() && !actor.isAdmin() && !actor.isLegal()) {
			throw ApiException.forbidden("Not allowed to view invitations");
		}

		long invitedByUserId =
			(actor.isAdmin() || actor.isLegal()) ? 0 : actor.getId();

		Map<Long, ObjectNode> applicationsByInvitation = new HashMap<>();

		for (ObjectNode application : _partnerRepository.listApplications()) {
			applicationsByInvitation.put(
				Entries.longValue(application, Fields.APPLICATION_INVITATION),
				application);
		}

		Instant now = Instant.now(_clock);

		String normalizedSearch = _isBlank(search) ? null :
			search.trim(
			).toLowerCase(
				Locale.ROOT
			);
		String normalizedTab = _isBlank(tab) ? TAB_ALL : tab;

		List<Map<String, Object>> rows = new ArrayList<>();

		for (ObjectNode invitation :
				_partnerRepository.listInvitations(invitedByUserId)) {

			ObjectNode application = applicationsByInvitation.get(
				Entries.id(invitation));

			ApplicationStatus applicationStatus = (application == null) ?
				null : _statusService.getStatus(application);

			if (!_matchesTab(
					normalizedTab, applicationStatus,
					Entries.instant(invitation, "expiresAt"), now) ||
				!_matchesSearch(normalizedSearch, invitation, application)) {

				continue;
			}

			rows.add(_toRow(invitation, application, applicationStatus));
		}

		rows.sort(
			Comparator.comparing(
				(Map<String, Object> row) -> String.valueOf(
					row.get("sentAt")),
				Comparator.reverseOrder()));

		return rows;
	}

	public InvitationResult resend(long invitationId, UserInfo actor) {
		ObjectNode invitation = _partnerRepository.getInvitation(invitationId);

		ObjectNode application = _partnerRepository.findApplicationByInvitation(
			invitationId);

		if (!actor.isAdmin() &&
			!(actor.isAm() &&
			  (_isUser(invitation, Fields.INVITATION_INVITED_BY, actor) ||
			   _isUser(application, Fields.APPLICATION_ASSIGNED_AM, actor)))) {

			throw ApiException.forbidden(
				"Only the inviting account manager or an admin can resend " +
					"this invitation");
		}

		if (application == null) {
			throw ApiException.conflict(
				"This invitation has no application to resend");
		}

		ApplicationStatus applicationStatus = _statusService.getStatus(
			application);

		if (!RESENDABLE.contains(applicationStatus)) {
			throw ApiException.conflict(
				"An invitation can only be resent while the application is " +
					"invitation sent, registration in progress or expired");
		}

		PartnerSettings partnerSettings = _settingsService.get();

		Instant now = Instant.now(_clock);

		Instant expiresAt = _expiresAt(now, partnerSettings);

		String token = _tokenService.generate();

		Map<String, Object> fields = new LinkedHashMap<>();

		fields.put("expiresAt", Entries.dateTime(expiresAt));
		fields.put(
			"resendCount", Entries.integer(invitation, "resendCount", 0) + 1);
		fields.put("sentAt", Entries.dateTime(now));
		fields.put("state", STATE_ACTIVE);

		// Replacing the hash revokes the previous token (CLAUDE.md rule 6)

		fields.put("tokenHash", _tokenService.hash(token));

		invitation = _partnerRepository.patchInvitation(invitationId, fields);

		if (applicationStatus == ApplicationStatus.EXPIRED) {
			_statusService.transition(
				Entries.id(application), ApplicationStatus.INVITATION_SENT,
				actor.getId(), "Invitation resent");
		}

		UserInfo am = actor;

		long assignedAMUserId = Entries.longValue(
			application, Fields.APPLICATION_ASSIGNED_AM);

		if ((assignedAMUserId != 0) && (assignedAMUserId != actor.getId())) {
			am = _userDirectory.getUser(assignedAMUserId);
		}

		boolean emailSent = _sendInvite(invitation, token, expiresAt, am);

		return new InvitationResult(
			invitationId, Entries.id(application), expiresAt, emailSent,
			Collections.emptyList());
	}

	/**
	 * Warnings when the email domain matches the website of a terminated
	 * partner (US-001 AC2). The invitation can still be sent.
	 */
	public List<String> watchlistWarnings(String email) {
		String domain = _domainOfEmail(email);

		if (domain == null) {
			return Collections.emptyList();
		}

		List<String> warnings = new ArrayList<>();

		for (ObjectNode application :
				_partnerRepository.findApplicationsByStatus(
					EnumSet.of(ApplicationStatus.TERMINATED))) {

			String websiteDomain = _domainOfWebsite(
				Entries.text(application, "website"));

			if (domain.equals(websiteDomain)) {
				warnings.add(
					"The email domain " + domain +
						" matches a terminated partner: " +
							Entries.text(application, "companyName"));
			}
		}

		return warnings;
	}

	public static class InvitationResult {

		public InvitationResult(
			long invitationId, long applicationId, Instant expiresAt,
			boolean emailSent, List<String> warnings) {

			this.invitationId = invitationId;
			this.applicationId = applicationId;
			this.expiresAt = expiresAt;
			this.emailSent = emailSent;
			this.warnings = warnings;
		}

		public final long applicationId;
		public final boolean emailSent;
		public final Instant expiresAt;
		public final long invitationId;
		public final List<String> warnings;

	}

	private static String _domainOfEmail(String email) {
		if (email == null) {
			return null;
		}

		int index = email.lastIndexOf('@');

		if ((index < 0) || (index == (email.length() - 1))) {
			return null;
		}

		return email.substring(
			index + 1
		).toLowerCase(
			Locale.ROOT
		);
	}

	private static String _domainOfWebsite(String website) {
		if (_isBlank(website)) {
			return null;
		}

		String value = website.trim();

		if (!value.contains("://")) {
			value = "http://" + value;
		}

		try {
			String host = URI.create(
				value
			).getHost();

			if (host == null) {
				return null;
			}

			host = host.toLowerCase(Locale.ROOT);

			if (host.startsWith("www.")) {
				host = host.substring(4);
			}

			return host;
		}
		catch (IllegalArgumentException illegalArgumentException) {
			return null;
		}
	}

	private static boolean _isBlank(String value) {
		return (value == null) || value.trim().isEmpty();
	}

	private static boolean _isUser(
		ObjectNode entry, String field, UserInfo userInfo) {

		if (entry == null) {
			return false;
		}

		if (Entries.longValue(entry, field) == userInfo.getId()) {
			return true;
		}

		return false;
	}

	private static String _normalizeEmail(String email) {
		if (email == null) {
			return null;
		}

		return email.trim(
		).toLowerCase(
			Locale.ROOT
		);
	}

	private ObjectNode _createApplication(long invitationId, long amUserId) {
		Map<String, Object> fields = new LinkedHashMap<>();

		fields.put(Fields.APPLICATION_ASSIGNED_AM, amUserId);
		fields.put(Fields.APPLICATION_INVITATION, invitationId);

		ObjectNode application = _partnerRepository.createApplication(fields);

		_statusService.recordCreated(Entries.id(application), amUserId);

		return application;
	}

	private Instant _expiresAt(Instant sentAt, PartnerSettings partnerSettings) {
		return sentAt.plus(
			Duration.ofDays(partnerSettings.getInvitationExpiryDays()));
	}

	private boolean _matchesSearch(
		String search, ObjectNode invitation, ObjectNode application) {

		if (search == null) {
			return true;
		}

		List<String> values = new ArrayList<>();

		values.add(Entries.text(invitation, "email"));
		values.add(
			Entries.text(invitation, "firstName") + " " +
				Entries.text(invitation, "lastName"));

		if (application != null) {
			values.add(Entries.text(application, "companyName"));
		}

		for (String value : values) {
			if ((value != null) &&
				value.toLowerCase(
					Locale.ROOT
				).contains(
					search
				)) {

				return true;
			}
		}

		return false;
	}

	private boolean _matchesTab(
		String tab, ApplicationStatus applicationStatus, Instant expiresAt,
		Instant now) {

		if (TAB_ALL.equals(tab)) {
			return true;
		}

		if (TAB_INVITATION_SENT.equals(tab)) {
			return applicationStatus == ApplicationStatus.INVITATION_SENT;
		}

		if (TAB_IN_PROGRESS.equals(tab)) {
			return applicationStatus ==
				ApplicationStatus.REGISTRATION_IN_PROGRESS;
		}

		if (TAB_EXPIRED.equals(tab)) {
			return applicationStatus == ApplicationStatus.EXPIRED;
		}

		if (TAB_EXPIRING.equals(tab)) {
			if (((applicationStatus != ApplicationStatus.INVITATION_SENT) &&
				 (applicationStatus !=
					 ApplicationStatus.REGISTRATION_IN_PROGRESS)) ||
				(expiresAt == null)) {

				return false;
			}

			return !expiresAt.isBefore(now) &&
				   expiresAt.isBefore(now.plus(Duration.ofDays(7)));
		}

		throw ApiException.badRequest("Unknown tab " + tab);
	}

	private boolean _sendInvite(
		ObjectNode invitation, String token, Instant expiresAt, UserInfo am) {

		String email = Entries.text(invitation, "email");

		try {
			_mailer.send(
				MailMessage.of(
					"PM_INVITE", email
				).value(
					"PARTNER_NAME",
					Entries.text(invitation, "firstName") + " " +
						Entries.text(invitation, "lastName")
				).value(
					"AM_NAME", am.getName()
				).value(
					"LINK", _links.registration(token)
				).value(
					"EXPIRES_AT", Dates.format(expiresAt)
				));

			return true;
		}
		catch (RuntimeException runtimeException) {
			_alertService.alert(
				"Invitation email failed",
				"PM_INVITE could not be sent for invitation " +
					Entries.id(invitation) +
						". The account manager can resend it.",
				runtimeException);

			return false;
		}
	}

	private Map<String, Object> _toRow(
		ObjectNode invitation, ObjectNode application,
		ApplicationStatus applicationStatus) {

		Map<String, Object> row = new LinkedHashMap<>();

		row.put("invitationId", Entries.id(invitation));
		row.put(
			"applicationId",
			(application == null) ? null : Entries.id(application));
		row.put("firstName", Entries.text(invitation, "firstName"));
		row.put("lastName", Entries.text(invitation, "lastName"));
		row.put("email", Entries.text(invitation, "email"));
		row.put(
			"companyName",
			(application == null) ? null :
				Entries.text(application, "companyName"));
		row.put(
			"status",
			(applicationStatus == null) ? null : applicationStatus.key());
		row.put("state", Entries.picklistKey(invitation, "state"));
		row.put("sentAt", Entries.text(invitation, "sentAt"));
		row.put("expiresAt", Entries.text(invitation, "expiresAt"));
		row.put(
			"reminderCount", Entries.integer(invitation, "reminderCount", 0));
		row.put("resendCount", Entries.integer(invitation, "resendCount", 0));
		row.put(
			"invitedByUserId",
			Entries.longValue(invitation, Fields.INVITATION_INVITED_BY));
		row.put(
			"resendable",
			(applicationStatus != null) &&
			RESENDABLE.contains(applicationStatus));

		return row;
	}

	private static final Pattern _EMAIL_PATTERN = Pattern.compile(
		"^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private final AlertService _alertService;
	private final Clock _clock;
	private final Links _links;
	private final Mailer _mailer;
	private final PartnerRepository _partnerRepository;
	private final SettingsService _settingsService;
	private final StatusService _statusService;
	private final TokenService _tokenService;
	private final UserDirectory _userDirectory;

}
