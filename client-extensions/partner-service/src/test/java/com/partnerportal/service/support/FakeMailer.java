package com.partnerportal.service.support;

import com.partnerportal.service.domain.MailMessage;
import com.partnerportal.service.domain.Mailer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FakeMailer implements Mailer {

	public List<MailMessage> byTemplate(String templateERC) {
		return sent.stream(
		).filter(
			mailMessage -> mailMessage.getTemplateERC(
			).equals(
				templateERC
			)
		).collect(
			Collectors.toList()
		);
	}

	public MailMessage last() {
		return sent.get(sent.size() - 1);
	}

	@Override
	public void send(MailMessage mailMessage) {
		if (failTemplates.contains(mailMessage.getTemplateERC())) {
			throw new IllegalStateException("SMTP down");
		}

		sent.add(mailMessage);
	}

	public final List<String> failTemplates = new ArrayList<>();
	public final List<MailMessage> sent = new ArrayList<>();

}
