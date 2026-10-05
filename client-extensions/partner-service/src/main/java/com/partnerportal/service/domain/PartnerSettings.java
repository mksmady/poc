package com.partnerportal.service.domain;

import com.fasterxml.jackson.databind.JsonNode;

import com.partnerportal.service.liferay.Entries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The single {@code PartnerSettings} entry ({@code PM_SETTINGS_DEFAULT}).
 * Defaults apply only when a value is missing in Liferay.
 */
public class PartnerSettings {

	public static final String ERC = "PM_SETTINGS_DEFAULT";

	public static PartnerSettings from(JsonNode entry) {
		return new PartnerSettings(
			Entries.integer(entry, "invitationExpiryDays", 30),
			_parseDays(Entries.text(entry, "reminderDays"), "7,21,27"),
			Entries.integer(entry, "reviewSlaDays", 5),
			Entries.integer(entry, "revisionLinkDays", 14),
			Entries.integer(entry, "activationLinkHours", 72),
			Entries.integer(entry, "maxUploadMb", 10),
			_withDefault(Entries.text(entry, "jobCron"), "0 0 2 * * *"));
	}

	public PartnerSettings(
		int invitationExpiryDays, List<Integer> reminderDays, int reviewSlaDays,
		int revisionLinkDays, int activationLinkHours, int maxUploadMb,
		String jobCron) {

		_invitationExpiryDays = invitationExpiryDays;
		_reminderDays = Collections.unmodifiableList(
			new ArrayList<>(reminderDays));
		_reviewSlaDays = reviewSlaDays;
		_revisionLinkDays = revisionLinkDays;
		_activationLinkHours = activationLinkHours;
		_maxUploadMb = maxUploadMb;
		_jobCron = jobCron;
	}

	public int getActivationLinkHours() {
		return _activationLinkHours;
	}

	public int getInvitationExpiryDays() {
		return _invitationExpiryDays;
	}

	public String getJobCron() {
		return _jobCron;
	}

	public long getMaxUploadBytes() {
		return _maxUploadMb * 1024L * 1024L;
	}

	public int getMaxUploadMb() {
		return _maxUploadMb;
	}

	/**
	 * Days after {@code sentAt} on which a reminder is sent, ascending.
	 */
	public List<Integer> getReminderDays() {
		return _reminderDays;
	}

	public int getReviewSlaDays() {
		return _reviewSlaDays;
	}

	public int getRevisionLinkDays() {
		return _revisionLinkDays;
	}

	private static List<Integer> _parseDays(String value, String defaultValue) {
		List<Integer> days = new ArrayList<>();

		for (String part : _withDefault(value, defaultValue).split(",")) {
			String trimmed = part.trim();

			if (trimmed.isEmpty()) {
				continue;
			}

			try {
				int day = Integer.parseInt(trimmed);

				if ((day > 0) && !days.contains(day)) {
					days.add(day);
				}
			}
			catch (NumberFormatException numberFormatException) {

				// Ignore a malformed entry rather than stop all reminders

			}
		}

		Collections.sort(days);

		return days;
	}

	private static String _withDefault(String value, String defaultValue) {
		if ((value == null) || value.trim().isEmpty()) {
			return defaultValue;
		}

		return value;
	}

	private final int _activationLinkHours;
	private final int _invitationExpiryDays;
	private final String _jobCron;
	private final int _maxUploadMb;
	private final List<Integer> _reminderDays;
	private final int _reviewSlaDays;
	private final int _revisionLinkDays;

}
