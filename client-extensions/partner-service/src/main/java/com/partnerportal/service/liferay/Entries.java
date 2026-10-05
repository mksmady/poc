package com.partnerportal.service.liferay;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Null-safe readers for object entry JSON as returned by {@code /o/c/...}.
 */
public final class Entries {

	public static boolean bool(JsonNode entry, String field) {
		JsonNode node = entry.get(field);

		return (node != null) && node.asBoolean(false);
	}

	/**
	 * Formats an instant for a Liferay DateTime field. DXP 2024.Q1 rejects
	 * fractional seconds, so the value is truncated to whole seconds.
	 */
	public static String dateTime(Instant instant) {
		return instant.truncatedTo(
			ChronoUnit.SECONDS
		).toString();
	}

	public static long id(JsonNode entry) {
		return entry.path(
			"id"
		).asLong();
	}

	public static Instant instant(JsonNode entry, String field) {
		String value = text(entry, field);

		if ((value == null) || value.isEmpty()) {
			return null;
		}

		return Instant.parse(value);
	}

	public static int integer(JsonNode entry, String field, int defaultValue) {
		JsonNode node = entry.get(field);

		if ((node == null) || node.isNull()) {
			return defaultValue;
		}

		return node.asInt(defaultValue);
	}

	/**
	 * Relationship fields hold the related entry id; 0 means not set.
	 */
	public static long longValue(JsonNode entry, String field) {
		JsonNode node = entry.get(field);

		if ((node == null) || node.isNull()) {
			return 0;
		}

		return node.asLong();
	}

	/**
	 * Picklist values come back as {@code {"key": "...", "name": "..."}}.
	 */
	public static String picklistKey(JsonNode entry, String field) {
		JsonNode node = entry.get(field);

		if ((node == null) || node.isNull()) {
			return null;
		}

		if (node.isTextual()) {
			return node.asText();
		}

		JsonNode key = node.get("key");

		if ((key == null) || key.isNull()) {
			return null;
		}

		return key.asText();
	}

	public static String text(JsonNode entry, String field) {
		JsonNode node = entry.get(field);

		if ((node == null) || node.isNull()) {
			return null;
		}

		return node.asText();
	}

	private Entries() {
	}

}
