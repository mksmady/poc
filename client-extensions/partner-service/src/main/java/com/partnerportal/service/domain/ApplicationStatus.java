package com.partnerportal.service.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Picklist {@code PM_PL_APPLICATION_STATUS} and its allowed transitions. Must
 * match {@code docs/status-lifecycle.md} and the picklist state flow in
 * {@code partner-batch}.
 */
public enum ApplicationStatus {

	APPROVAL_PENDING("approvalPending"), AUTHORIZED("authorized"),
	CONTRACT_PENDING("contractPending"), DEACTIVATED("deactivated"),
	EXPIRED("expired"), INVITATION_SENT("invitationSent"),
	REGISTRATION_IN_PROGRESS("registrationInProgress"), REJECTED("rejected"),
	REVISION_REQUESTED("revisionRequested"), SUBMITTED("submitted"),
	TERMINATED("terminated"), UNDER_LEGAL_REVIEW("underLegalReview");

	public static ApplicationStatus fromKey(String key) {
		for (ApplicationStatus applicationStatus : values()) {
			if (applicationStatus._key.equals(key)) {
				return applicationStatus;
			}
		}

		throw new IllegalArgumentException("Unknown application status " + key);
	}

	public Set<ApplicationStatus> allowedTargets() {
		return Collections.unmodifiableSet(_transitions.get(this));
	}

	public boolean canTransitionTo(ApplicationStatus target) {
		return _transitions.get(
			this
		).contains(
			target
		);
	}

	public boolean isTerminal() {
		return _transitions.get(
			this
		).isEmpty();
	}

	public String key() {
		return _key;
	}

	private static void _allow(
		ApplicationStatus from, ApplicationStatus... targets) {

		Set<ApplicationStatus> set = EnumSet.noneOf(ApplicationStatus.class);

		Collections.addAll(set, targets);

		_transitions.put(from, set);
	}

	ApplicationStatus(String key) {
		_key = key;
	}

	private static final Map<ApplicationStatus, Set<ApplicationStatus>>
		_transitions = new EnumMap<>(ApplicationStatus.class);

	static {
		_allow(INVITATION_SENT, REGISTRATION_IN_PROGRESS, EXPIRED);
		_allow(REGISTRATION_IN_PROGRESS, EXPIRED, SUBMITTED);
		_allow(EXPIRED, INVITATION_SENT);
		_allow(SUBMITTED, APPROVAL_PENDING, UNDER_LEGAL_REVIEW);
		_allow(APPROVAL_PENDING, REVISION_REQUESTED, UNDER_LEGAL_REVIEW, AUTHORIZED);
		_allow(UNDER_LEGAL_REVIEW, REVISION_REQUESTED, CONTRACT_PENDING, REJECTED);
		_allow(REVISION_REQUESTED, SUBMITTED);
		_allow(CONTRACT_PENDING, AUTHORIZED, TERMINATED);
		_allow(AUTHORIZED, DEACTIVATED, TERMINATED);
		_allow(DEACTIVATED, AUTHORIZED, TERMINATED);
		_allow(REJECTED);
		_allow(TERMINATED);
	}

	private final String _key;

}
