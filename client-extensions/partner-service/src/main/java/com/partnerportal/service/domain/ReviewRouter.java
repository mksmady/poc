package com.partnerportal.service.domain;

import com.fasterxml.jackson.databind.node.ObjectNode;

import com.partnerportal.service.liferay.Entries;

/**
 * Moves a submitted application to its review stage: back to legal when
 * legal asked for the revision, otherwise to the account manager
 * ({@code docs/status-lifecycle.md}, "submitted" rows).
 */
public interface ReviewRouter {

	static ApplicationStatus reviewStage(ObjectNode application) {
		if ("legal".equals(
				Entries.picklistKey(application, "revisionRequestedBy"))) {

			return ApplicationStatus.UNDER_LEGAL_REVIEW;
		}

		return ApplicationStatus.APPROVAL_PENDING;
	}

	void routeSubmitted(long applicationId);

}
