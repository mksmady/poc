package com.partnerportal.service.liferay;

import com.fasterxml.jackson.databind.JsonNode;

import com.partnerportal.service.domain.MailMessage;
import com.partnerportal.service.domain.Mailer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Renders a Liferay notification template (by ERC) with the message values and
 * queues it through {@code POST /o/notification/v1.0/notification-queue-entries}
 * (open question 13). The rendered body may contain a link token; it goes only
 * into the queued email, never onto an object.
 */
@Component
public class LiferayMailer implements Mailer {

	public LiferayMailer(
		LiferayClient liferayClient,
		@Value("${partner.mail.from:noreply@partnerportal.local}") String from,
		@Value("${partner.mail.from.name:Partner Portal}") String fromName) {

		_liferayClient = liferayClient;
		_from = from;
		_fromName = fromName;
	}

	public static String render(
		String template, Map<String, String> values,
		Map<String, String> htmlValues) {

		if (template == null) {
			return "";
		}

		Matcher matcher = _PLACEHOLDER_PATTERN.matcher(template);

		StringBuffer sb = new StringBuffer();

		while (matcher.find()) {
			String placeholder = matcher.group(1);

			String replacement = htmlValues.get(placeholder);

			if (replacement == null) {
				String value = values.get(placeholder);

				replacement = (value == null) ? "" : HtmlUtils.htmlEscape(value);
			}

			matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}

		matcher.appendTail(sb);

		return sb.toString();
	}

	@Override
	public void send(MailMessage mailMessage) {
		String to = mailMessage.getTo();

		if ((to == null) || to.isEmpty()) {
			throw new IllegalArgumentException(
				"No recipient for " + mailMessage.getTemplateERC());
		}

		JsonNode template = _liferayClient.get(
			"/o/notification/v1.0/notification-templates" +
				"/by-external-reference-code/" +
					Urls.segment(mailMessage.getTemplateERC()));

		Map<String, String> values = new LinkedHashMap<>(
			mailMessage.getValues());

		values.put("RECIPIENT_EMAIL", mailMessage.getTo());

		// Subjects are plain text: render them without HTML escaping

		String subject = render(
			_localized(template, "subject"), Collections.emptyMap(),
			values);
		String body = render(
			_localized(template, "body"), values, mailMessage.getHtmlValues());

		Map<String, Object> recipient = new LinkedHashMap<>();

		recipient.put("from", _from);
		recipient.put("fromName", _fromName);
		recipient.put("to", mailMessage.getTo());

		Map<String, Object> queueEntry = new LinkedHashMap<>();

		queueEntry.put("body", body);
		queueEntry.put("recipients", Collections.singletonList(recipient));
		queueEntry.put("subject", subject);
		queueEntry.put("type", "email");

		_liferayClient.post(
			"/o/notification/v1.0/notification-queue-entries", queueEntry);
	}

	private String _localized(JsonNode template, String field) {
		JsonNode node = template.path(field);

		if (node.isTextual()) {
			return node.asText();
		}

		if (node.has("en_US")) {
			return node.path(
				"en_US"
			).asText();
		}

		return template.path(
			field + "_i18n"
		).path(
			"en-US"
		).asText(
			""
		);
	}

	private static final Pattern _PLACEHOLDER_PATTERN = Pattern.compile(
		"\\[%([A-Z0-9_]+)%\\]");

	private final String _from;
	private final String _fromName;
	private final LiferayClient _liferayClient;

}
