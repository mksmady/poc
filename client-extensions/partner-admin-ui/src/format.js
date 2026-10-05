export const STATUS_LABELS = {
	approvalPending: 'Approval pending',
	authorized: 'Authorized',
	contractPending: 'Contract pending',
	deactivated: 'Deactivated',
	expired: 'Expired',
	invitationSent: 'Invitation sent',
	registrationInProgress: 'Registration in progress',
	rejected: 'Rejected',
	revisionRequested: 'Revision requested',
	submitted: 'Submitted',
	terminated: 'Terminated',
	underLegalReview: 'Under legal review',
};

const STATUS_DISPLAY = {
	authorized: 'success',
	deactivated: 'secondary',
	expired: 'warning',
	rejected: 'danger',
	terminated: 'danger',
};

export function statusLabel(status) {
	return STATUS_LABELS[status] ?? (status || '—');
}

export function statusDisplay(status) {
	return STATUS_DISPLAY[status] ?? 'info';
}

export function formatDate(value) {
	if (!value) {
		return '—';
	}

	const date = new Date(value);

	if (Number.isNaN(date.getTime())) {
		return value;
	}

	return date.toLocaleDateString(undefined, {
		day: 'numeric',
		month: 'short',
		year: 'numeric',
	});
}

// Same rule as partner-service InvitationService: something@something.tld
const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

export function isEmail(value) {
	return EMAIL_PATTERN.test(value.trim());
}
