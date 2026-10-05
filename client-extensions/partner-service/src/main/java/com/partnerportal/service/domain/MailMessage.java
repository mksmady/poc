package com.partnerportal.service.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A notification template ERC, one recipient and the placeholder values.
 * {@link #value} values are HTML-escaped when rendered; {@link #html} values
 * are inserted as they are and must be built from escaped parts.
 */
public class MailMessage {

	public static MailMessage of(String templateERC, String to) {
		return new MailMessage(templateERC, to);
	}

	public Map<String, String> getHtmlValues() {
		return Collections.unmodifiableMap(_htmlValues);
	}

	public String getTemplateERC() {
		return _templateERC;
	}

	public String getTo() {
		return _to;
	}

	public Map<String, String> getValues() {
		return Collections.unmodifiableMap(_values);
	}

	public MailMessage html(String placeholder, String html) {
		_htmlValues.put(placeholder, html);

		return this;
	}

	@Override
	public String toString() {
		return _templateERC + " to " + _to + " " + _values.keySet();
	}

	public MailMessage value(String placeholder, String value) {
		_values.put(placeholder, value);

		return this;
	}

	private MailMessage(String templateERC, String to) {
		_templateERC = templateERC;
		_to = to;
	}

	private final Map<String, String> _htmlValues = new LinkedHashMap<>();
	private final String _templateERC;
	private final String _to;
	private final Map<String, String> _values = new LinkedHashMap<>();

}
