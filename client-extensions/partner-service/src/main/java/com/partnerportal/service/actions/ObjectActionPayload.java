package com.partnerportal.service.actions;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Reads the JSON Liferay posts to an {@code objectAction} client extension:
 * {@code classPK}, {@code userId}, {@code objectEntry} (with {@code values})
 * and, on update, {@code originalObjectEntry}.
 */
public class ObjectActionPayload {

	public ObjectActionPayload(JsonNode jsonNode) {
		_jsonNode = jsonNode;
	}

	public long getEntryId() {
		long classPK = _jsonNode.path(
			"classPK"
		).asLong();

		if (classPK != 0) {
			return classPK;
		}

		JsonNode objectEntry = _jsonNode.path("objectEntry");

		long id = objectEntry.path(
			"id"
		).asLong();

		if (id != 0) {
			return id;
		}

		return objectEntry.path(
			"objectEntryId"
		).asLong();
	}

	/**
	 * A field value from {@code objectEntry.values}, or from the entry itself.
	 */
	public JsonNode getValue(String field) {
		return _value(_jsonNode.path("objectEntry"), field);
	}

	public JsonNode getOriginalValue(String field) {
		return _value(_jsonNode.path("originalObjectEntry"), field);
	}

	public long getUserId() {
		return _jsonNode.path(
			"userId"
		).asLong();
	}

	private JsonNode _value(JsonNode objectEntry, String field) {
		JsonNode values = objectEntry.path("values");

		if (values.has(field)) {
			return values.get(field);
		}

		return objectEntry.path(field);
	}

	private final JsonNode _jsonNode;

}
