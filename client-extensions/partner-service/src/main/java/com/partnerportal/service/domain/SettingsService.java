package com.partnerportal.service.domain;

import org.springframework.stereotype.Service;

/**
 * Reads {@code PartnerSettings} fresh on every call so admin edits apply
 * without a restart (CLAUDE.md rule 8: never constants).
 */
@Service
public class SettingsService {

	public SettingsService(PartnerRepository partnerRepository) {
		_partnerRepository = partnerRepository;
	}

	public PartnerSettings get() {
		return PartnerSettings.from(_partnerRepository.getSettings());
	}

	private final PartnerRepository _partnerRepository;

}
