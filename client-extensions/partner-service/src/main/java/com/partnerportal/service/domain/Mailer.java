package com.partnerportal.service.domain;

/**
 * Sends one email rendered from a notification template.
 */
public interface Mailer {

	void send(MailMessage mailMessage);

}
