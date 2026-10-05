package com.partnerportal.service.liferay;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.web.util.UriUtils;

/**
 * Encoding for the pre-encoded paths {@link LiferayClient} takes.
 */
public final class Urls {

	/**
	 * Encodes a query parameter value (an OData filter, a search string).
	 */
	public static String param(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	/**
	 * Encodes one path segment (an ERC or role name).
	 */
	public static String segment(String value) {
		return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
	}

	private Urls() {
	}

}
