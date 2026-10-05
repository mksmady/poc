import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {ApiError, createInvitation, listInvitations, liferayMessage, resendInvitation, resetClientForTests} from './api';

function json(status, body) {
	return Promise.resolve({
		json: () => Promise.resolve(body),
		ok: status >= 200 && status < 300,
		status,
	});
}

const DAY = 86400000;
const iso = (offsetDays) => new Date(Date.now() + offsetDays * DAY).toISOString();

const INVITATIONS = [
	{email: 'ana@acme.test', expiresAt: iso(20), firstName: 'Ana', id: 1, lastName: 'Lopez', r_invitationInvitedBy_userId: 7, sentAt: iso(-10), state: {key: 'active'}},
	{email: 'bo@beta.test', expiresAt: iso(3), firstName: 'Bo', id: 2, lastName: 'Berg', r_invitationInvitedBy_userId: 7, sentAt: iso(-27), state: {key: 'active'}},
	{email: 'cy@other.test', expiresAt: iso(-1), firstName: 'Cy', id: 3, lastName: 'Diaz', r_invitationInvitedBy_userId: 8, sentAt: iso(-31), state: {key: 'expired'}},
];

const APPLICATIONS = [
	{applicationStatus: {key: 'invitationSent'}, id: 11, r_invitationApplication_c_partnerInvitationId: 1},
	{applicationStatus: {key: 'registrationInProgress'}, companyName: 'Beta AB', id: 12, r_invitationApplication_c_partnerInvitationId: 2},
	{applicationStatus: {key: 'expired'}, id: 13, r_applicationAssignedAM_userId: 8, r_invitationApplication_c_partnerInvitationId: 3},
	{applicationStatus: {key: 'terminated'}, companyName: 'Old Acme', id: 14, website: 'https://www.acme.test'},
];

let routes;

beforeEach(() => {
	resetClientForTests();

	routes = {
		'GET /o/c/partnerapplications': () => json(200, {items: APPLICATIONS}),
		'GET /o/c/partnerinvitations': () => json(200, {items: INVITATIONS}),
		'GET /o/headless-admin-user/v1.0/my-user-account': () => json(200, {id: 7, roleBriefs: [{name: 'Partner Account Manager'}]}),
	};

	window.Liferay = {
		Util: {
			fetch: vi.fn((url, init = {}) => {
				const {pathname, searchParams} = new URL(url, 'http://portal.test');
				const route = routes[`${init.method || 'GET'} ${pathname}`];

				if (pathname === '/o/c/partnerapplications' && searchParams.get('filter')) {
					return json(200, {items: APPLICATIONS.filter((a) => a.applicationStatus.key === 'terminated')});
				}

				if (!route) {
					throw new Error(`unexpected ${init.method || 'GET'} ${pathname}`);
				}

				return route(init);
			}),
		},
	};
});

afterEach(() => {
	delete window.Liferay;
});

describe('listInvitations (AC7)', () => {
	it('shows an AM only their own invitations, newest first', async () => {
		const rows = await listInvitations();

		expect(rows.map((row) => row.invitationId)).toEqual([1, 2]);
		expect(rows[1]).toMatchObject({companyName: 'Beta AB', resendable: true, status: 'registrationInProgress'});
	});

	it('shows admins everything', async () => {
		routes['GET /o/headless-admin-user/v1.0/my-user-account'] = () =>
			json(200, {id: 1, roleBriefs: [{name: 'Partner Portal Admin'}]});

		expect((await listInvitations()).map((row) => row.invitationId)).toEqual([1, 2, 3]);
	});

	it('filters by tab and search', async () => {
		expect((await listInvitations({tab: 'expiring'})).map((r) => r.invitationId)).toEqual([2]);
		expect((await listInvitations({tab: 'inProgress'})).map((r) => r.invitationId)).toEqual([2]);
		expect((await listInvitations({search: 'beta ab'})).map((r) => r.invitationId)).toEqual([2]);
		expect((await listInvitations({search: 'LOPEZ'})).map((r) => r.invitationId)).toEqual([1]);
	});

	it('uses the portal session', async () => {
		await listInvitations();

		expect(window.Liferay.Util.fetch).toHaveBeenCalled();
	});
});

describe('createInvitation (AC1, AC2)', () => {
	it('posts the invitation and reports the issued expiry and watchlist warnings', async () => {
		let posted;

		routes['POST /o/c/partnerinvitations'] = (init) => {
			posted = JSON.parse(init.body);

			return json(200, {id: 9});
		};
		routes['GET /o/c/partnerinvitations/9'] = () => json(200, {expiresAt: '2026-11-04T10:00:00Z', id: 9, tokenHash: 'abc'});

		const result = await createInvitation({email: ' New@Acme.test ', firstName: 'Nia', lastName: 'New'});

		expect(posted).toMatchObject({email: 'new@acme.test', r_invitationInvitedBy_userId: 7, state: 'active'});
		expect(result).toMatchObject({emailSent: true, expiresAt: '2026-11-04T10:00:00Z', invitationId: 9});
		expect(result.warnings).toEqual(['The email domain acme.test matches a terminated partner: Old Acme']);
	});

	it('passes on the portal refusal of a duplicate', async () => {
		routes['POST /o/c/partnerinvitations'] = () =>
			json(400, {detail: '[{"errorMessage":"An active invitation already exists for this email"}]', status: 'BAD_REQUEST'});

		const error = await createInvitation({email: 'ana@acme.test', firstName: 'A', lastName: 'L'}).catch((e) => e);

		expect(error).toBeInstanceOf(ApiError);
		expect(error.message).toBe('An active invitation already exists for this email');
	});
});

describe('resendInvitation (AC6)', () => {
	it('raises resendCount and reports the new link', async () => {
		let patched;
		let current = {expiresAt: iso(20), id: 1, resendCount: 0, tokenHash: 'old'};

		routes['GET /o/c/partnerinvitations/1'] = () => json(200, current);
		routes['PATCH /o/c/partnerinvitations/1'] = (init) => {
			patched = JSON.parse(init.body);
			current = {...current, expiresAt: iso(30), resendCount: 1, tokenHash: 'new'};

			return json(200, current);
		};

		const result = await resendInvitation(1);

		expect(patched).toEqual({resendCount: 1});
		expect(result.emailSent).toBe(true);
	});
});

describe('liferayMessage', () => {
	it('reads validation and plain errors', () => {
		expect(liferayMessage({detail: '[{"errorMessage":"No"}]'})).toBe('No');
		expect(liferayMessage({title: 'Forbidden'})).toBe('Forbidden');
	});
});

describe('outside the portal', () => {
	it('fails clearly', async () => {
		delete window.Liferay;

		await expect(listInvitations()).rejects.toThrow('Open this page inside the partner portal');
	});
});
