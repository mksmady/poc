// Runs the element's own API code (src/api.js) against a local portal:
//   npx vite-node scripts/live-check.mjs
// Creates an invitation as the local admin, takes the link from the queued
// PM_INVITE, then registers through the wizard API as the registration user.
// Local development only; it leaves a submitted test application behind.

import {createApi} from '../src/api.js';

const BASE = process.env.LIFERAY_URL || 'http://localhost:8080';
const ADMIN = 'Basic ' + btoa(`${process.env.LIFERAY_USER || 'test@liferay.com'}:${process.env.LIFERAY_PASSWORD || 'test'}`);

async function admin(method, path, body) {
	const response = await fetch(BASE + path, {
		body: body && JSON.stringify(body),
		headers: {Accept: 'application/json', Authorization: ADMIN, ...(body ? {'Content-Type': 'application/json'} : {})},
		method,
	});

	return response.json();
}

const ok = (label, condition, extra = '') => console.log(`${condition ? 'PASS' : 'FAIL'}  ${label}${extra ? '  ' + extra : ''}`);

const email = `live-check-${Date.now()}@example.com`;
const me = await admin('GET', '/o/headless-admin-user/v1.0/my-user-account');

await admin('POST', '/o/c/partnerinvitations', {email, expiresAt: '2030-01-01T00:00:00Z', firstName: 'Liv', lastName: 'Check', reminderCount: 0, resendCount: 0, state: 'active', r_invitationInvitedBy_userId: me.id});
await new Promise((resolve) => setTimeout(resolve, 1500));

const queue = await admin('GET', '/o/notification/v1.0/notification-queue-entries?pageSize=200');
const mail = queue.items.find((item) => JSON.stringify(item.recipients).includes(email));
const token = mail?.body.match(/[?&](?:amp;)?t=([A-Za-z0-9_-]{43})/)?.[1];

ok('PM_INVITE has the link', Boolean(token));

const api = createApi({
	baseUrl: BASE,
	categories: {networking: 'Networking', security: 'Security'},
	folderId: import.meta.env.VITE_UPLOAD_FOLDER_ID,
	password: import.meta.env.VITE_REGISTRATION_PASSWORD,
	user: import.meta.env.VITE_REGISTRATION_USER,
});

const opened = await api.open(token);

ok('open', opened.status === 'registrationInProgress' && !opened.readOnly, opened.status);

let result = await api.saveDraft(opened.session, {
	company: {city: 'Berlin', companyName: 'Live Check GmbH', country: 'de', employeeBand: '10-49', postalCode: '10115', registrationNumber: 'HRB 42', street: 'Main 1', vatId: 'DE4242', website: 'https://live.example'},
	compliance: {gdprConsent: true, sanctionsDeclaration: true},
	contacts: [{email: 'liv@live.example', fullName: 'Liv Check', role: 'primary'}, {role: 'other'}],
	posLocations: [{categories: ['networking'], city: 'Berlin', country: 'DE', name: 'Store', posType: 'store', street: 'Main 1'}],
	questionnaire: {businessType: 'reseller'},
});

ok('save draft (blank second contact skipped)', result.draft.contacts.length === 1 && result.draft.posLocations.length === 1);

const missing = await api.submit(opened.session).catch((error) => error);

ok('submit without documents refused field by field', missing.status === 422 && missing.errors.some((e) => e.field === 'vatCertificate'));

for (const documentType of ['tradeRegister', 'vatCertificate']) {
	result = await api.uploadDocument(opened.session, {documentType, file: new File(['%PDF-1.4\n%%EOF\n'], `${documentType}.pdf`)});
}

ok('two documents attached', result.draft.documents.length === 2, result.draft.documents.map((d) => d.fileName).join(', '));

result = await api.submit(opened.session);

ok('submitted and routed', result.readOnly && result.status === 'approvalPending', result.status);

const reopened = await api.open(token);

ok('link opens read-only after submit (AC11)', reopened.readOnly === true);

const refused = await api.saveDraft(reopened.session, result.draft).catch((error) => error);

ok('changes refused after submit', refused.status === 409);
