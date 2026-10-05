package com.partnerportal.service.domain;

import com.partnerportal.service.support.InMemoryPartnerRepository;

import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SettingsServiceTest {

	@Test
	public void testDefaultsWhenValuesAreMissing() {
		InMemoryPartnerRepository repository = new InMemoryPartnerRepository();

		PartnerSettings partnerSettings = new SettingsService(
			repository
		).get();

		Assertions.assertEquals(30, partnerSettings.getInvitationExpiryDays());
		Assertions.assertEquals(
			Arrays.asList(7, 21, 27), partnerSettings.getReminderDays());
		Assertions.assertEquals(5, partnerSettings.getReviewSlaDays());
		Assertions.assertEquals(14, partnerSettings.getRevisionLinkDays());
		Assertions.assertEquals(72, partnerSettings.getActivationLinkHours());
		Assertions.assertEquals(10, partnerSettings.getMaxUploadMb());
		Assertions.assertEquals("0 0 2 * * *", partnerSettings.getJobCron());
	}

	@Test
	public void testReadsTheEntryEachTime() {
		InMemoryPartnerRepository repository = new InMemoryPartnerRepository();

		SettingsService settingsService = new SettingsService(repository);

		repository.settings.put("invitationExpiryDays", 30);

		Assertions.assertEquals(
			30,
			settingsService.get(
			).getInvitationExpiryDays());

		repository.settings.put("invitationExpiryDays", 45);
		repository.settings.put("reminderDays", " 21, 3,x,3,0 ");
		repository.settings.put("maxUploadMb", 5);

		PartnerSettings partnerSettings = settingsService.get();

		Assertions.assertEquals(45, partnerSettings.getInvitationExpiryDays());
		Assertions.assertEquals(
			Arrays.asList(3, 21), partnerSettings.getReminderDays());
		Assertions.assertEquals(
			5L * 1024 * 1024, partnerSettings.getMaxUploadBytes());
	}

}
