package com.partnerportal.service.domain;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Routes a submitted application straight to its review stage. Used when the
 * Kaleo workflow is not bound to PartnerApplication
 * ({@code partner.workflow.enabled=false}); with the workflow, the workflow
 * action does this.
 */
@Component
@ConditionalOnProperty(
	havingValue = "false", matchIfMissing = true,
	name = "partner.workflow.enabled"
)
public class DirectReviewRouter implements ReviewRouter {

	public DirectReviewRouter(
		PartnerRepository partnerRepository, StatusService statusService) {

		_partnerRepository = partnerRepository;
		_statusService = statusService;
	}

	@Override
	public void routeSubmitted(long applicationId) {
		_statusService.transition(
			applicationId,
			ReviewRouter.reviewStage(
				_partnerRepository.getApplication(applicationId)),
			0, "Routed to review");
	}

	private final PartnerRepository _partnerRepository;
	private final StatusService _statusService;

}
