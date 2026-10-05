package com.partnerportal.service.liferay;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Calls Liferay headless APIs as the headless-server OAuth app. Paths start
 * with {@code /o/} and are already URL-encoded: build query values with
 * {@link Urls#param} and ERC or name segments with {@link Urls#segment}.
 */
public interface LiferayClient {

	void delete(String path);

	JsonNode get(String path);

	JsonNode patch(String path, Object body);

	JsonNode post(String path, Object body);

	/**
	 * Posts {@code multipart/form-data}: one file part named {@code file} and
	 * one JSON part named {@code jsonPartName}.
	 */
	JsonNode postMultipart(
		String path, String fileName, String contentType, byte[] bytes,
		String jsonPartName, String json);

	JsonNode put(String path, Object body);

}
