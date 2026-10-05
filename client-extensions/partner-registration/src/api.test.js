import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {ApiError, INVALID_LINK, createApi, liferayMessage, modeOf, sha256Hex, sniff} from './api';

const CATEGORIES = {networking: 'Networking'};
const FUTURE = '2099-01-01T00:00:00Z';

function json(status, body) {
	return Promise.resolve({
		json: () => Promise.resolve(body),
		ok: status >= 200 && status < 300,
		status,
	});
}

/**
 * A tiny in-memory Object API: enough of /o/c/... for the wizard calls.
 */
function fakePortal({applicationStatus = 'invitationSent', state = 'active', expiresAt = FUTURE, tokenHash} = {}) {
	const db = {
		applicationcontacts: [],
		applicationdocuments: [],
		partnerapplications: [
			{applicationStatus: {key: applicationStatus}, id: 20, r_invitationApplication_c_partnerInvitationId: 10},
		],
		partnerinvitations: [
			{email: 'ana@acme.test', expiresAt, firstName: 'Ana', id: 10, lastName: 'Lopez', state: {key: state}, tokenHash},
		],
		partnersettingses: [{id: 1, maxUploadMb: 1}],
		poslocations: [],
	};
	let nextId = 100;
	const calls = [];

	const handler = vi.fn(async (url, init = {}) => {
		const method = init.method || 'GET';
		const {pathname, searchParams} = new URL(url, 'http://portal.test');

		calls.push(`${method} ${pathname}`);

		if (pathname.startsWith('/o/headless-delivery/v1.0/document-folders/')) {
			return json(200, {id: 900});
		}

		if (pathname.startsWith('/o/headless-delivery/v1.0/documents/')) {
			return json(204, null);
		}

		const [, , , collection, id] = pathname.split('/');
		const rows = db[collection];

		if (method === 'GET' && !id) {
			const filter = searchParams.get('filter') || '';
			const match = filter.match(/^(\w+) eq '([^']*)'$/);
			const items = match
				? rows.filter((row) => String(row[match[1]]?.key ?? row[match[1]]) === match[2])
				: rows;

			return json(200, {items});
		}

		const row = rows.find((r) => r.id === Number(id));

		if (method === 'GET') {
			return row ? json(200, row) : json(404, {title: 'Not found'});
		}

		if (method === 'PATCH') {
			const body = JSON.parse(init.body);

			for (const [field, value] of Object.entries(body)) {
				row[field] = field === 'applicationStatus' ? {key: value} : value;
			}

			return json(200, row);
		}

		if (method === 'POST') {
			const created = {...JSON.parse(init.body), id: nextId++};

			if (collection === 'applicationdocuments') {
				created.documentType = {key: created.documentType};
				created.file = {name: 'upload.pdf'};
			}

			rows.push(created);

			return json(200, created);
		}

		if (method === 'DELETE') {
			rows.splice(rows.indexOf(row), 1);

			return json(204, null);
		}

		throw new Error(`unhandled ${method} ${pathname}`);
	});

	return {calls, db, handler};
}

let portal;
let api;
const TOKEN = 'tok_EN';

beforeEach(async () => {
	portal = fakePortal({tokenHash: await sha256Hex(TOKEN)});
	vi.stubGlobal('fetch', portal.handler);
	api = createApi({categories: CATEGORIES, folderId: 77, password: 'pw', user: 'reg@portal.test'});
});

afterEach(() => {
	vi.unstubAllGlobals();
});

describe('helpers', () => {
	it('hashes like the portal (SHA-256 hex)', async () => {
		expect(await sha256Hex('abc')).toBe(
			'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad'
		);
	});

	it('decides edit, read-only or invalid (AC1, AC11)', () => {
		const active = {expiresAt: FUTURE, state: {key: 'active'}};

		expect(modeOf(active, 'registrationInProgress')).toBe('edit');
		expect(modeOf(active, 'approvalPending')).toBe('readOnly');
		expect(modeOf({...active, expiresAt: '2000-01-01T00:00:00Z'}, 'invitationSent')).toBe('invalid');
		expect(modeOf({expiresAt: FUTURE, state: {key: 'used'}}, 'approvalPending')).toBe('readOnly');
		expect(modeOf({expiresAt: FUTURE, state: {key: 'revoked'}}, 'invitationSent')).toBe('invalid');
		expect(modeOf(active, 'authorized')).toBe('invalid');
	});

	it('reads Liferay validation messages', () => {
		expect(
			liferayMessage({detail: '[{"errorMessage":"An active invitation already exists for this email"}]'})
		).toBe('An active invitation already exists for this email');
		expect(liferayMessage({title: 'Not found'})).toBe('Not found');
		expect(liferayMessage(null)).toMatch(/Something went wrong/);
	});

	it('sniffs the file type from the content (AC8)', async () => {
		expect((await sniff(new File(['%PDF-1.7'], 'x.png'))).extension).toBe('pdf');
		expect(await sniff(new File(['MZ...'], 'x.pdf'))).toBeNull();
	});
});

describe('open (AC1)', () => {
	it('signs in as the registration user without cookies', async () => {
		await api.open(TOKEN);

		const [, init] = portal.handler.mock.calls[0];

		expect(init.headers.Authorization).toBe('Basic ' + btoa('reg@portal.test:pw'));
		expect(init.credentials).toBe('omit');
	});

	it('finds the invitation by token hash and moves to registrationInProgress', async () => {
		const result = await api.open(TOKEN);

		expect(result.status).toBe('registrationInProgress');
		expect(result.readOnly).toBe(false);
		expect(result.invitee.firstName).toBe('Ana');
		expect(result.maxUploadMb).toBe(1);
		expect(portal.calls).toContain('PATCH /o/c/partnerapplications/20');
	});

	it('refuses a wrong token, an expired link and a revoked link', async () => {
		await expect(api.open('other')).rejects.toMatchObject({message: INVALID_LINK, status: 404});

		portal.db.partnerinvitations[0].expiresAt = '2000-01-01T00:00:00Z';
		await expect(api.open(TOKEN)).rejects.toMatchObject({message: INVALID_LINK});

		portal.db.partnerinvitations[0].expiresAt = FUTURE;
		portal.db.partnerinvitations[0].state = {key: 'revoked'};
		await expect(api.open(TOKEN)).rejects.toMatchObject({message: INVALID_LINK});
	});

	it('stops working after a resend changes the hash', async () => {
		const {session} = await api.open(TOKEN);

		portal.db.partnerinvitations[0].tokenHash = 'f'.repeat(64);

		await expect(api.saveDraft(session, {company: {}, compliance: {}, contacts: [], posLocations: [], questionnaire: {}})).rejects.toMatchObject({message: INVALID_LINK});
	});
});

describe('saveDraft (AC3)', () => {
	const draft = (extra = {}) => ({
		company: {},
		compliance: {},
		contacts: [{role: 'primary'}],
		posLocations: [{categories: []}],
		questionnaire: {businessType: 'reseller'},
		...extra,
	});

	it('does not store the empty starting rows', async () => {
		const {session} = await api.open(TOKEN);

		const result = await api.saveDraft(session, draft());

		expect(result.draft.questionnaire).toEqual({businessType: 'reseller'});
		expect(portal.db.applicationcontacts).toHaveLength(0);
		expect(portal.db.poslocations).toHaveLength(0);
	});

	it('refuses a started row without its required fields before writing anything', async () => {
		const {session} = await api.open(TOKEN);
		const before = portal.calls.length;

		const error = await api
			.saveDraft(session, draft({contacts: [{role: 'primary'}, {phone: '+49 1', role: 'finance'}]}))
			.catch((e) => e);

		expect(error).toBeInstanceOf(ApiError);
		expect(error.status).toBe(422);
		expect(error.errors.map((e) => e.field)).toEqual(['contacts[1].fullName', 'contacts[1].email']);
		expect(portal.calls.slice(before).filter((c) => !c.startsWith('GET'))).toEqual([]);
	});

	it('creates, updates and removes contacts to match the draft (AC5)', async () => {
		const {session} = await api.open(TOKEN);

		let result = await api.saveDraft(session, draft({contacts: [
			{email: 'a@acme.test', fullName: 'Ana', role: 'primary'},
			{email: 'f@acme.test', fullName: 'Fin', role: 'finance'},
		]}));

		const [first] = result.draft.contacts;

		result = await api.saveDraft(session, draft({contacts: [{...first, fullName: 'Ana Lopez'}]}));

		expect(result.draft.contacts).toEqual([expect.objectContaining({fullName: 'Ana Lopez', id: first.id})]);
	});

	it('switches to existing-company mode on an authorized VAT match (AC9)', async () => {
		portal.db.partnerapplications.push({
			applicationStatus: {key: 'authorized'},
			city: 'Munich',
			companyName: 'Acme Holding',
			id: 30,
			r_applicationAccount_accountEntryId: 777,
			vatId: 'DE 123.456',
		});

		// The fake filter understands one condition; answer the lookup directly

		const original = portal.handler.getMockImplementation();

		portal.handler.mockImplementation((url, init) =>
			/applicationStatus%20eq|applicationStatus\+eq|applicationStatus eq/.test(decodeURIComponent(url))
				? json(200, {items: portal.db.partnerapplications.filter((a) => a.id === 30)})
				: original(url, init)
		);

		const {session} = await api.open(TOKEN);
		const result = await api.saveDraft(session, draft({company: {companyName: 'Typed', vatId: 'de123456'}}));

		expect(result.draft.existingCompany).toBe(true);
		expect(result.draft.company.companyName).toBe('Acme Holding');
		expect(portal.db.partnerapplications[0].r_applicationAccount_accountEntryId).toBe(777);
	});
});

describe('documents (AC8)', () => {
	it('uploads to the folder, attaches, and removes the folder copy', async () => {
		const {session} = await api.open(TOKEN);

		const result = await api.uploadDocument(session, {
			documentType: 'tradeRegister',
			file: new File(['%PDF-1.4'], '../Extract.PDF'),
			note: 'page 1',
		});

		expect(portal.calls.filter((c) => !c.startsWith('GET'))).toEqual(
			expect.arrayContaining([
				'POST /o/headless-delivery/v1.0/document-folders/77/documents',
				'POST /o/c/applicationdocuments',
				'DELETE /o/headless-delivery/v1.0/documents/900',
			])
		);
		expect(result.draft.documents[0]).toMatchObject({documentType: 'tradeRegister'});
	});

	it('refuses other content and large files', async () => {
		const {session} = await api.open(TOKEN);

		await expect(
			api.uploadDocument(session, {documentType: 'other', file: new File(['<html>'], 'x.pdf')})
		).rejects.toMatchObject({status: 415});

		await expect(
			api.uploadDocument(session, {
				documentType: 'other',
				file: Object.defineProperty(new File(['%PDF-'], 'big.pdf'), 'size', {value: 2 * 1024 * 1024}),
			})
		).rejects.toMatchObject({status: 413});
	});
});

describe('submit (AC10)', () => {
	it('lists what is missing without changing the status', async () => {
		const {session} = await api.open(TOKEN);

		const error = await api.submit(session).catch((e) => e);

		expect(error.status).toBe(422);
		expect(error.errors.map((e) => `${e.step}.${e.field}`)).toEqual(
			expect.arrayContaining(['company.companyName', 'contacts.contacts', 'pos.posLocations', 'documents.vatCertificate'])
		);
		expect(portal.db.partnerapplications[0].applicationStatus.key).toBe('registrationInProgress');
	});
});
