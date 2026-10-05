package com.partnerportal.service.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Dates as they appear in emails (English only for release 1, open question
 * 12).
 */
public final class Dates {

	public static String format(Instant instant) {
		if (instant == null) {
			return "";
		}

		return _DATE_FORMATTER.format(instant);
	}

	private Dates() {
	}

	private static final DateTimeFormatter _DATE_FORMATTER =
		DateTimeFormatter.ofPattern(
			"d MMMM yyyy, HH:mm 'UTC'", Locale.ENGLISH
		).withZone(
			ZoneOffset.UTC
		);

}
