package com.partnerportal.service.api;

import com.partnerportal.service.job.DailyJob;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets an admin run the daily job now (support and end-to-end checks).
 */
@RequestMapping("/api/jobs")
@RestController
public class JobController {

	public JobController(CurrentUser currentUser, DailyJob dailyJob) {
		_currentUser = currentUser;
		_dailyJob = dailyJob;
	}

	@PostMapping("/daily")
	public Map<String, Object> runDaily(@AuthenticationPrincipal Jwt jwt) {
		if (!_currentUser.resolve(
				jwt
			).isAdmin()) {

			throw ApiException.forbidden("Only admins can run the daily job");
		}

		return _dailyJob.run(
		).toMap();
	}

	private final CurrentUser _currentUser;
	private final DailyJob _dailyJob;

}
