package com.partnerportal.service.domain;

public class IllegalTransitionException extends RuntimeException {

	public IllegalTransitionException(
		ApplicationStatus from, ApplicationStatus to) {

		super(
			"Application status cannot change from " + from.key() + " to " +
				to.key());

		_from = from;
		_to = to;
	}

	public ApplicationStatus getFrom() {
		return _from;
	}

	public ApplicationStatus getTo() {
		return _to;
	}

	private final ApplicationStatus _from;
	private final ApplicationStatus _to;

}
