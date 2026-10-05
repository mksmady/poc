package com.partnerportal.service.job;

import com.partnerportal.service.domain.SettingsService;

import java.time.ZoneOffset;
import java.util.Date;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * Schedules {@link DailyJob} with the cron in {@code PartnerSettings.jobCron}
 * (UTC). The cron is read again after every run, so an admin edit applies
 * from the next run without a restart.
 */
@Component
@ConditionalOnProperty(
	havingValue = "true", matchIfMissing = true,
	name = "partner.job.enabled"
)
public class DailyJobScheduler implements SchedulingConfigurer {

	public static final String DEFAULT_CRON = "0 0 2 * * *";

	public DailyJobScheduler(
		DailyJob dailyJob, SettingsService settingsService) {

		_dailyJob = dailyJob;
		_settingsService = settingsService;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar scheduledTaskRegistrar) {
		scheduledTaskRegistrar.addTriggerTask(
			() -> {
				try {
					_dailyJob.run();
				}
				catch (RuntimeException runtimeException) {
					_log.error("Daily job failed", runtimeException);
				}
			},
			triggerContext -> {
				Date next = new CronTrigger(
					_cron(), TimeZone.getTimeZone(ZoneOffset.UTC)
				).nextExecutionTime(
					triggerContext
				);

				if (_log.isInfoEnabled()) {
					_log.info("Next daily job run at " + next.toInstant());
				}

				return next;
			});
	}

	private String _cron() {
		try {
			String cron = _settingsService.get(
			).getJobCron();

			new CronTrigger(cron);

			return cron;
		}
		catch (RuntimeException runtimeException) {
			_log.error(
				"Unable to read PartnerSettings.jobCron; using " + DEFAULT_CRON,
				runtimeException);

			return DEFAULT_CRON;
		}
	}

	private static final Log _log = LogFactory.getLog(DailyJobScheduler.class);

	private final DailyJob _dailyJob;
	private final SettingsService _settingsService;

}
