package com.partnerportal.service.liferay;

import com.fasterxml.jackson.databind.JsonNode;

import com.partnerportal.service.domain.MailMessage;
import com.partnerportal.service.support.FakeLiferayClient;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LiferayMailerTest {

	@Test
	public void testRenderEscapesValuesAndBlanksUnknownPlaceholders() {
		Assertions.assertEquals(
			"<p>Hi &lt;b&gt;Jo&lt;/b&gt; <i>ok</i> !</p>",
			LiferayMailer.render(
				"<p>Hi [%NAME%] [%HTML%] [%UNKNOWN%]!</p>",
				Map.of("NAME", "<b>Jo</b>"), Map.of("HTML", "<i>ok</i>")));
	}

	@Test
	public void testSendQueuesRenderedTemplate() {
		FakeLiferayClient fakeLiferayClient = new FakeLiferayClient();

		fakeLiferayClient.respond(
			"/o/notification/v1.0/notification-templates" +
				"/by-external-reference-code/PM_INVITE",
			"{\"subject\": {\"en_US\": \"Hello [%NAME%]\"}, \"body\": " +
				"{\"en_US\": \"<a href=\\\"[%LINK%]\\\">go</a>\"}}");

		new LiferayMailer(
			fakeLiferayClient, "from@example.com", "Portal"
		).send(
			MailMessage.of(
				"PM_INVITE", "to@example.com"
			).value(
				"NAME", "Jo & Co"
			).value(
				"LINK", "https://x/r?t=abc"
			));

		FakeLiferayClient.Call call = fakeLiferayClient.calls.get(1);

		Assertions.assertEquals("POST", call.method);

		JsonNode body = call.body;

		Assertions.assertEquals("Hello Jo & Co", body.get("subject").asText());
		Assertions.assertEquals(
			"<a href=\"https://x/r?t=abc\">go</a>", body.get("body").asText());
		Assertions.assertEquals("email", body.get("type").asText());
		Assertions.assertEquals(
			"to@example.com", body.get("recipients").get(0).get("to").asText());
	}

}
