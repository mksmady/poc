// US-001 over Liferay's Object API as the signed-in staff user
// (Liferay.Util.fetch adds the session and CSRF token). The portal does the
// rest (partner-batch/scripts): the invitation-created action issues the
// link and creates the application, a validation rule refuses a second
// active invitation for the same email, and raising resendCount resends.
// Who may see and resend what follows the user's Liferay permissions; the
// "AM sees own" filter is applied here (docs/data-model.md, "own").

const RESENDABLE = ['invitationSent', 'registrationInProgress', 'expired'];

const STAFF_ALL = ['Administrator', 'Partner Portal Admin', 'Partner Legal'];

export class ApiError extends Error {
	constructor(message, status) {
		super(message);

		this.status = status;
	}
}

function portalFetch() {
	const liferayFetch = window.Liferay?.Util?.fetch;

	if (!liferayFetch) {
		throw new ApiError('Open this page inside the partner portal', 0);
	}

	return liferayFetch;
}

/**
 * Liferay answers validation rules with {detail: '[{"errorMessage": ...}]'}
 * and other errors with {title}.
 */
export function liferayMessage(payload) {
	if (payload?.detail) {
		try {
			const details = JSON.parse(payload.detail);

			if (Array.isArray(details) && details[0]?.errorMessage) {
				return details.map((detail) => detail.errorMessage).join(' ');
			}
		}
		catch {
			// Not JSON.
		}
	}

	return payload?.title || 'Something went wrong. Try again.';
}

async function request(path, {body, method = 'GET'} = {}) {
	const response = await portalFetch()(path, {
		body: body === undefined ? undefined : JSON.stringify(body),
		headers: {
			Accept: 'application/json',
			...(body === undefined ? {} : {'Content-Type': 'application/json'}),
		},
		method,
	});

	let payload = null;

	try {
		payload = response.status === 204 ? null : await response.json();
	}
	catch {
		// Handled below.
	}

	if (!response.ok) {
		throw new ApiError(liferayMessage(payload), response.status);
	}

	return payload;
}

const key = (value) => (value && typeof value === 'object' ? value.key : value ?? null);

async function all(path) {
	return (await request(`${path}${path.includes('?') ? '&' : '?'}pageSize=-1`)).items || [];
}

let me;

async function currentUser() {
	if (!me) {
		me = request('/o/headless-admin-user/v1.0/my-user-account').catch((error) => {
			me = undefined;

			throw error;
		});
	}

	return me;
}

function matchesTab(tab, status, expiresAt, now) {
	switch (tab) {
		case 'all':
			return true;
		case 'invitationSent':
			return status === 'invitationSent';
		case 'inProgress':
			return status === 'registrationInProgress';
		case 'expired':
			return status === 'expired';
		case 'expiring': {
			if (!['invitationSent', 'registrationInProgress'].includes(status) || !expiresAt) {
				return false;
			}

			const time = Date.parse(expiresAt);

			return time >= now && time < now + 7 * 24 * 60 * 60 * 1000;
		}
		default:
			throw new ApiError(`Unknown tab ${tab}`, 400);
	}
}

/**
 * Rows for the invitations dashboard (AC7): AMs see the invitations they sent
 * or are assigned to; admins and legal see all.
 */
export async function listInvitations({search = '', tab = 'all'} = {}) {
	const [user, invitations, applications] = await Promise.all([
		currentUser(),
		all('/o/c/partnerinvitations'),
		all('/o/c/partnerapplications'),
	]);

	const seesAll = (user.roleBriefs || []).some((role) => STAFF_ALL.includes(role.name));
	const byInvitation = new Map(
		applications.map((application) => [application.r_invitationApplication_c_partnerInvitationId, application])
	);
	const needle = search.trim().toLowerCase();
	const now = Date.now();

	return invitations
		.map((invitation) => {
			const application = byInvitation.get(invitation.id);
			const status = key(application?.applicationStatus);

			return {
				applicationId: application?.id ?? null,
				assignedAMUserId: application?.r_applicationAssignedAM_userId ?? 0,
				companyName: application?.companyName || null,
				email: invitation.email,
				expiresAt: invitation.expiresAt,
				firstName: invitation.firstName,
				invitationId: invitation.id,
				invitedByUserId: invitation.r_invitationInvitedBy_userId ?? 0,
				lastName: invitation.lastName,
				reminderCount: invitation.reminderCount ?? 0,
				resendCount: invitation.resendCount ?? 0,
				resendable: RESENDABLE.includes(status),
				sentAt: invitation.sentAt,
				state: key(invitation.state),
				status,
			};
		})
		.filter((row) => seesAll || row.invitedByUserId === user.id || row.assignedAMUserId === user.id)
		.filter((row) => matchesTab(tab, row.status, row.expiresAt, now))
		.filter(
			(row) =>
				!needle ||
				[`${row.firstName} ${row.lastName}`, row.email, row.companyName]
					.some((value) => (value || '').toLowerCase().includes(needle))
		)
		.sort((a, b) => String(b.sentAt).localeCompare(String(a.sentAt)));
}

function domainOf(value) {
	const match = (value || '').toLowerCase().match(/^(?:[a-z]+:\/\/)?(?:[^@/]*@)?(?:www\.)?([^/:?#]+)/);

	return match ? match[1] : null;
}

/**
 * AC2: a terminated partner with the same domain is a warning, not a stop.
 */
async function watchlistWarnings(email) {
	const domain = domainOf(email.trim().split('@')[1]);

	if (!domain) {
		return [];
	}

	const terminated = await all(`/o/c/partnerapplications?filter=${encodeURIComponent("applicationStatus eq 'terminated'")}`);

	return terminated
		.filter((application) => domainOf(application.website) === domain)
		.map((application) => `The email domain ${domain} matches a terminated partner: ${application.companyName}`);
}

/**
 * AC1. The portal sets the real expiry from PartnerSettings when it issues
 * the link; the value sent here only satisfies the required field.
 */
export async function createInvitation({email, firstName, lastName}) {
	const user = await currentUser();

	const invitation = await request('/o/c/partnerinvitations', {
		body: {
			email: email.trim().toLowerCase(),
			expiresAt: new Date(Math.floor(Date.now() / 1000) * 1000 + 30 * 86400000).toISOString(),
			firstName: firstName.trim(),
			lastName: lastName.trim(),
			r_invitationInvitedBy_userId: user.id,
			reminderCount: 0,
			resendCount: 0,
			state: 'active',
		},
		method: 'POST',
	});

	const issued = await request(`/o/c/partnerinvitations/${invitation.id}`);

	return {
		emailSent: Boolean(issued.tokenHash),
		expiresAt: issued.expiresAt,
		invitationId: invitation.id,
		warnings: await watchlistWarnings(email).catch(() => []),
	};
}

/**
 * AC6: raising resendCount makes the portal revoke the old link and send a
 * new one; it refuses when the user or the status does not allow it.
 */
export async function resendInvitation(invitationId) {
	const invitation = await request(`/o/c/partnerinvitations/${invitationId}`);

	await request(`/o/c/partnerinvitations/${invitationId}`, {
		body: {resendCount: (invitation.resendCount ?? 0) + 1},
		method: 'PATCH',
	});

	const resent = await request(`/o/c/partnerinvitations/${invitationId}`);

	return {
		emailSent: resent.tokenHash !== invitation.tokenHash,
		expiresAt: resent.expiresAt,
		invitationId,
		warnings: [],
	};
}

export function resetClientForTests() {
	me = undefined;
}
