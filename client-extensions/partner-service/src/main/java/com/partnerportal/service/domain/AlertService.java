package com.partnerportal.service.domain;

import com.partnerportal.service.bootstrap.RoleBootstrap;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.stereotype.Service;

/**
 * Sends {@code PM_ADMIN_ALERT} to every Partner Portal Admin. Never throws:
 * an alert must not break the operation that raised it.
 */
@Service
public class AlertService {

	public AlertService(Mailer mailer, UserDirectory userDirectory) {
		_mailer = mailer;
		_userDirectory = userDirectory;
	}

	public void alert(String subject, String message, Throwable throwable) {
		_log.error("Admin alert: " + subject + ": " + message, throwable);

		String details = (throwable == null) ? "" : String.valueOf(throwable);

		try {
			List<UserInfo> admins = _userDirectory.getUsersWithRole(
				RoleBootstrap.ROLE_ADMIN);

			for (UserInfo admin : admins) {
				try {
					_mailer.send(
						MailMessage.of(
							"PM_ADMIN_ALERT", admin.getEmailAddress()
						).value(
							"SUBJECT", subject
						).value(
							"MESSAGE", message
						).value(
							"DETAILS", details
						));
				}
				catch (RuntimeException runtimeException) {
					_log.error(
						"Unable to send admin alert to user " + admin.getId(),
						runtimeException);
				}
			}
		}
		catch (RuntimeException runtimeException) {
			_log.error("Unable to look up admins for an alert", runtimeException);
		}
	}

	private static final Log _log = LogFactory.getLog(AlertService.class);

	private final Mailer _mailer;
	private final UserDirectory _userDirectory;

}
