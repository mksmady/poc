#!/usr/bin/env node

// Applies the partner portal logic that batch JSON cannot carry to a running
// portal, and regenerates the batch files for fresh installs. Safe to rerun.
//
//   node tools/partner-setup.mjs
//
// Environment (local defaults are the fresh-bundle admin from CLAUDE.md):
//   LIFERAY_URL       http://localhost:8080
//   LIFERAY_USER      test@liferay.com
//   LIFERAY_PASSWORD  test
//
// What it does (docs/architecture.md, "Logic inside Liferay"):
//   1. Upserts the Groovy object actions and validation rules by ERC from
//      client-extensions/partner-batch/scripts/*.groovy, and rewrites
//      partner-batch/batch/4x-*.batch-engine-data.json from the same sources.
//   2. Adds (never replaces) role permissions: the "Partner Registration
//      Client" role used by the registration page, and UPDATE on invitations
//      for account managers (resend).
//   3. Creates the registration user and its Documents and Media upload
//      folder, and writes their settings to
//      client-extensions/partner-registration/.env.local (git-ignored). The
//      password is generated once and kept in that file.

import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SCRIPTS = path.join(ROOT, 'client-extensions/partner-batch/scripts');
const BATCH = path.join(ROOT, 'client-extensions/partner-batch/batch');
const ENV_FILE = path.join(ROOT, 'client-extensions/partner-registration/.env.local');

const URL_BASE = (process.env.LIFERAY_URL || 'http://localhost:8080').replace(/\/+$/, '');
const AUTH =
	'Basic ' +
	Buffer.from(
		`${process.env.LIFERAY_USER || 'test@liferay.com'}:${process.env.LIFERAY_PASSWORD || 'test'}`
	).toString('base64');

export const REGISTRATION_ROLE = 'Partner Registration Client';
export const REGISTRATION_USER = 'partner-registration@partnerportal.local';
const UPLOAD_FOLDER = 'Partner registration uploads';

const NOT_EDITABLE = 'This application has been submitted and can no longer be changed.';

export const ACTIONS = [
	{
		condition: '',
		description: 'Issues the registration link and creates the application.',
		erc: 'PM_ACTION_INVITATION_CREATED',
		name: 'invitationCreated',
		object: 'PM_OBJ_INVITATION',
		script: 'invitation-issue.groovy',
		trigger: 'onAfterAdd',
	},
	{
		condition: 'oldValue("resendCount") != resendCount',
		description: 'Resend: revokes the old link and issues a new one.',
		erc: 'PM_ACTION_INVITATION_RESEND',
		name: 'invitationResend',
		object: 'PM_OBJ_INVITATION',
		script: 'invitation-issue.groovy',
		trigger: 'onAfterUpdate',
	},
	{
		condition: 'oldValue("applicationStatus") != applicationStatus',
		description: 'Writes status history; on submit notifies and routes.',
		erc: 'PM_ACTION_APPLICATION_STATUS_CHANGED',
		name: 'applicationStatusChanged',
		object: 'PM_OBJ_APPLICATION',
		script: 'application-status-changed.groovy',
		trigger: 'onAfterUpdate',
	},
];

export const VALIDATIONS = [
	{
		erc: 'PM_VAL_INVITATION_ACTIVE_UNIQUE',
		message: 'An active invitation already exists for this email',
		name: 'Active invitation unique per email',
		object: 'PM_OBJ_INVITATION',
		script: 'invitation-validate-active-unique.groovy',
	},
	{
		erc: 'PM_VAL_INVITATION_RESEND',
		message:
			'Only the inviting account manager or an admin can resend, while the application is invitation sent, registration in progress or expired.',
		name: 'Resend allowed',
		object: 'PM_OBJ_INVITATION',
		script: 'invitation-validate-resend.groovy',
	},
	{
		erc: 'PM_VAL_APPLICATION_REGISTRATION',
		message: NOT_EDITABLE,
		name: 'Registration page limits',
		object: 'PM_OBJ_APPLICATION',
		script: 'application-validate-registration.groovy',
	},
	{
		erc: 'PM_VAL_APPLICATION_SUBMIT',
		message: 'The application is not complete. Check every step and submit again.',
		name: 'Complete on submit',
		object: 'PM_OBJ_APPLICATION',
		script: 'application-validate-submit.groovy',
	},
	...[
		['PM_OBJ_CONTACT', 'PM_VAL_CONTACT_REGISTRATION'],
		['PM_OBJ_POS', 'PM_VAL_POS_REGISTRATION'],
		['PM_OBJ_DOCUMENT', 'PM_VAL_DOCUMENT_REGISTRATION'],
	].map(([object, erc]) => ({
		erc,
		message: NOT_EDITABLE,
		name: 'Registration page limits',
		object,
		script: 'child-validate-registration.groovy',
	})),
];

// Entry actions on "com.liferay.object.model.ObjectDefinition#<id>",
// ADD_OBJECT_ENTRY on "com.liferay.object#<id>" (as RoleBootstrap uses).

const REGISTRATION_GRANTS = [
	['PM_OBJ_INVITATION', ['VIEW'], false],
	['PM_OBJ_APPLICATION', ['VIEW', 'UPDATE'], false],
	['PM_OBJ_CONTACT', ['VIEW', 'UPDATE', 'DELETE'], true],
	['PM_OBJ_POS', ['VIEW', 'UPDATE', 'DELETE'], true],
	['PM_OBJ_DOCUMENT', ['VIEW', 'DELETE'], true],
	['PM_OBJ_SETTINGS', ['VIEW'], false],
];

const AM_GRANTS = [['PM_OBJ_INVITATION', ['UPDATE'], false]];

async function api(method, pathname, body, {form, allow = []} = {}) {
	const response = await fetch(URL_BASE + pathname, {
		body: form || (body === undefined ? undefined : JSON.stringify(body)),
		headers: {
			Accept: 'application/json',
			Authorization: AUTH,
			...(body !== undefined && !form ? {'Content-Type': 'application/json'} : {}),
		},
		method,
	});

	const text = await response.text();

	if (!response.ok && !allow.includes(response.status)) {
		throw new Error(`${method} ${pathname} -> ${response.status} ${text.slice(0, 400)}`);
	}

	try {
		return {body: text ? JSON.parse(text) : null, status: response.status};
	}
	catch {
		return {body: text, status: response.status};
	}
}

async function jsonws(pathname, params) {
	const form = new URLSearchParams(params);
	const response = await fetch(`${URL_BASE}/api/jsonws${pathname}`, {
		body: form,
		headers: {Authorization: AUTH},
		method: 'POST',
	});
	const text = await response.text();

	if (!response.ok || /"exception"/.test(text)) {
		throw new Error(`jsonws ${pathname} -> ${response.status} ${text.slice(0, 300)}`);
	}

	return text ? JSON.parse(text) : null;
}

const definitions = new Map();

async function definition(erc) {
	if (!definitions.has(erc)) {
		const {body} = await api(
			'GET',
			`/o/object-admin/v1.0/object-definitions/by-external-reference-code/${erc}`
		);

		definitions.set(erc, body);
	}

	return definitions.get(erc);
}

function script(file) {
	return fs.readFileSync(path.join(SCRIPTS, file), 'utf8');
}

function actionBody(action) {
	return {
		active: true,
		conditionExpression: action.condition,
		description: action.description,
		externalReferenceCode: action.erc,
		label: {en_US: action.name.replace(/([A-Z])/g, ' $1').replace(/^./, (c) => c.toUpperCase())},
		name: action.name,
		objectActionExecutorKey: 'groovy',
		objectActionTriggerKey: action.trigger,
		parameters: {script: script(action.script)},
	};
}

function validationBody(validation) {
	return {
		active: true,
		engine: 'groovy',
		errorLabel: {en_US: validation.message},
		externalReferenceCode: validation.erc,
		name: {en_US: validation.name},
		script: script(validation.script),
	};
}

async function upsertActions() {
	for (const action of ACTIONS) {
		const def = await definition(action.object);
		const {body} = await api(
			'GET',
			`/o/object-admin/v1.0/object-definitions/${def.id}/object-actions?pageSize=-1`
		);
		const existing = (body.items || []).find(
			(item) => item.externalReferenceCode === action.erc || item.name === action.name
		);

		if (existing) {
			await api('PUT', `/o/object-admin/v1.0/object-actions/${existing.id}`, actionBody(action));
		}
		else {
			await api(
				'POST',
				`/o/object-admin/v1.0/object-definitions/${def.id}/object-actions`,
				actionBody(action)
			);
		}

		console.log(`action ${action.erc}: ${existing ? 'updated' : 'created'}`);
	}
}

async function upsertValidations() {
	for (const validation of VALIDATIONS) {
		const def = await definition(validation.object);
		const {body} = await api(
			'GET',
			`/o/object-admin/v1.0/object-definitions/${def.id}/object-validation-rules?pageSize=-1`
		);
		const existing = (body.items || []).find(
			(item) => item.externalReferenceCode === validation.erc
		);

		if (existing) {
			await api(
				'PUT',
				`/o/object-admin/v1.0/object-validation-rules/${existing.id}`,
				validationBody(validation)
			);
		}
		else {
			await api(
				'POST',
				`/o/object-admin/v1.0/object-definitions/${def.id}/object-validation-rules`,
				validationBody(validation)
			);
		}

		console.log(`validation ${validation.erc}: ${existing ? 'updated' : 'created'}`);
	}
}

function writeBatchFiles() {
	const byObject = (list) =>
		list.reduce((map, item) => map.set(item.object, [...(map.get(item.object) || []), item]), new Map());

	const write = (file, className, objectERC, items) => {
		fs.writeFileSync(
			path.join(BATCH, file),
			JSON.stringify(
				{
					configuration: {
						className,
						parameters: {
							containsHeaders: 'true',
							createStrategy: 'INSERT',
							externalReferenceCode: objectERC,
							importStrategy: 'ON_ERROR_CONTINUE',
						},
						taskItemDelegateName: 'DEFAULT',
					},
					items,
				},
				null,
				'\t'
			) + '\n'
		);
	};

	const short = {
		PM_OBJ_APPLICATION: 'application',
		PM_OBJ_CONTACT: 'contact',
		PM_OBJ_DOCUMENT: 'document',
		PM_OBJ_INVITATION: 'invitation',
		PM_OBJ_POS: 'pos',
	};

	for (const [object, actions] of byObject(ACTIONS)) {
		write(
			`40-object-actions-${short[object]}.batch-engine-data.json`,
			'com.liferay.object.admin.rest.dto.v1_0.ObjectAction',
			object,
			actions.map(actionBody)
		);
	}

	for (const [object, validations] of byObject(VALIDATIONS)) {
		write(
			`45-validation-rules-${short[object]}.batch-engine-data.json`,
			'com.liferay.object.admin.rest.dto.v1_0.ObjectValidationRule',
			object,
			validations.map(validationBody)
		);
	}

	console.log('batch files written');
}

async function companyId() {
	const {body} = await api('GET', '/o/headless-portal-instances/v1.0/portal-instances');

	return body.items.find((item) => item.active !== false).companyId;
}

async function findRole(name) {
	const {body} = await api(
		'GET',
		`/o/headless-admin-user/v1.0/roles?search=${encodeURIComponent(name)}&pageSize=50`
	);

	return (body.items || []).find((role) => role.name === name);
}

async function grant(roleId, company, grants) {
	for (const [objectERC, entryActions, add] of grants) {
		const def = await definition(objectERC);
		const actions = [
			...entryActions.map((actionId) => [`com.liferay.object.model.ObjectDefinition#${def.id}`, actionId]),
			...(add ? [[`com.liferay.object#${def.id}`, 'ADD_OBJECT_ENTRY']] : []),
		];

		for (const [name, actionId] of actions) {
			await jsonws('/resourcepermission/add-resource-permission', {
				actionId,
				companyId: company,
				groupId: 0,
				name,
				primKey: company,
				roleId,
				scope: 1,
			});
		}
	}
}

async function signsIn(password) {

	// A wrong password falls back to the guest user, so check the identity

	const response = await fetch(`${URL_BASE}/o/headless-admin-user/v1.0/my-user-account`, {
		headers: {
			Accept: 'application/json',
			Authorization:
				'Basic ' + Buffer.from(`${REGISTRATION_USER}:${password}`).toString('base64'),
		},
	});

	return response.ok && (await response.json()).emailAddress === REGISTRATION_USER;
}

const PASSWORD_POLICY = 'Partner Registration Client';

async function registrationPasswordPolicy(company, changeable = false) {
	const found = await jsonws('/passwordpolicy/search', {
		'-orderByComparator': '',
		companyId: company,
		end: -1,
		name: PASSWORD_POLICY,
		start: -1,
	});
	const existing = (found || []).find((policy) => policy.name === PASSWORD_POLICY);

	const settings = {
		allowDictionaryWords: true,
		changeRequired: false,
		changeable,
		checkSyntax: false,
		description:
			'Registration page user (US-002): no forced change, user cannot change it, no lockout. tools/partner-setup.mjs opens changes only while it sets the password.',
		expireable: false,
		graceLimit: 0,
		history: false,
		historyCount: 0,
		lockout: false,
		lockoutDuration: 0,
		maxAge: 0,
		maxFailure: 0,
		minAge: 0,
		minAlphanumeric: 0,
		minLength: 6,
		minLowerCase: 0,
		minNumbers: 0,
		minSymbols: 0,
		minUpperCase: 0,
		name: PASSWORD_POLICY,
		regex: '',
		resetFailureCount: 600,
		resetTicketMaxAge: 86400,
		serviceContext: '{}',
		warningTime: 0,
	};

	if (existing) {
		await jsonws('/passwordpolicy/update-password-policy', {
			passwordPolicyId: existing.passwordPolicyId,
			...settings,
		});

		return existing.passwordPolicyId;
	}

	return (await jsonws('/passwordpolicy/add-password-policy', settings)).passwordPolicyId;
}

/**
 * Runs a Groovy snippet inside Liferay for the few settings no API exposes.
 * It is attached for a moment as an action on PartnerSettings updates,
 * triggered by re-saving the settings entry unchanged, then removed.
 */
async function runGroovy(code) {
	const def = await definition('PM_OBJ_SETTINGS');
	const {body: action} = await api(
		'POST',
		`/o/object-admin/v1.0/object-definitions/${def.id}/object-actions`,
		{
			active: true,
			conditionExpression: '',
			description: 'Temporary: tools/partner-setup.mjs',
			label: {en_US: 'Partner setup (temporary)'},
			name: `partnerSetup${Date.now()}`,
			objectActionExecutorKey: 'groovy',
			objectActionTriggerKey: 'onAfterUpdate',
			parameters: {script: code},
		}
	);

	try {
		const {body: entries} = await api('GET', '/o/c/partnersettingses?pageSize=1');
		const entry = entries.items[0];

		await api('PATCH', `/o/c/partnersettingses/${entry.id}`, {maxUploadMb: entry.maxUploadMb});
	}
	finally {
		await api('DELETE', `/o/object-admin/v1.0/object-actions/${action.id}`);
	}
}

function readEnv() {
	const env = {};

	if (fs.existsSync(ENV_FILE)) {
		for (const line of fs.readFileSync(ENV_FILE, 'utf8').split(/\r?\n/)) {
			const match = line.match(/^([A-Z_]+)=(.*)$/);

			if (match) {
				env[match[1]] = match[2];
			}
		}
	}

	return env;
}

async function setUpRegistrationAccess() {
	const company = await companyId();

	let role = await findRole(REGISTRATION_ROLE);

	if (!role) {
		({body: role} = await api('POST', '/o/headless-admin-user/v1.0/roles', {
			description: 'Used by the partner registration page (US-002). Can only touch registration objects.',
			externalReferenceCode: 'PM_ROLE_REGISTRATION_CLIENT',
			name: REGISTRATION_ROLE,
			roleType: 'regular',
		}));
	}

	await grant(role.id, company, REGISTRATION_GRANTS);

	const amRole = await findRole('Partner Account Manager');

	if (amRole) {
		await grant(amRole.id, company, AM_GRANTS);
	}

	console.log(`role ${REGISTRATION_ROLE}: permissions added; account managers can resend`);

	const env = readEnv();
	const password =
		env.VITE_REGISTRATION_PASSWORD ||
		crypto.randomBytes(24).toString('base64url') + 'aA1!';

	let user = (
		await api(
			'GET',
			`/o/headless-admin-user/v1.0/user-accounts/by-email-address/${encodeURIComponent(REGISTRATION_USER)}`,
			undefined,
			{allow: [404]}
		)
	).body;

	if (!user?.id) {
		({body: user} = await api('POST', '/o/headless-admin-user/v1.0/user-accounts', {
			alternateName: 'partner-registration',
			emailAddress: REGISTRATION_USER,
			familyName: 'Registration',
			givenName: 'Partner',
		}));
	}

	// A shared credential needs its own password policy: no forced change
	// (an admin-set password would otherwise block basic auth), the user
	// cannot change it, and no lockout (anyone could lock the page out)

	const policyId = await registrationPasswordPolicy(company);

	await jsonws('/user/add-password-policy-users', {
		passwordPolicyId: policyId,
		userIds: JSON.stringify([user.id]),
	});

	// Basic auth is refused for a user whose email address is not verified;
	// DXP 2024.Q1 exposes no API for that flag

	await runGroovy(
		`com.liferay.portal.kernel.service.UserLocalServiceUtil.updateEmailAddressVerified(${user.id}L, true)`
	);

	// Set the password only when the stored one does not sign in. Changes
	// are opened on the policy just for this. DXP 2024.Q1 does not expose
	// /user/update-password over JSON web services; the headless password
	// field works

	if (!(await signsIn(password))) {
		await registrationPasswordPolicy(company, true);

		try {
			await api('PATCH', `/o/headless-admin-user/v1.0/user-accounts/${user.id}`, {password});
		}
		finally {
			await registrationPasswordPolicy(company);
		}
	}

	if (!(await signsIn(password))) {
		throw new Error(`${REGISTRATION_USER} cannot sign in with basic auth`);
	}
	await api(
		'POST',
		`/o/headless-admin-user/v1.0/roles/${role.id}/association/user-account/${user.id}`,
		undefined,
		{allow: [409]}
	);

	console.log(`user ${REGISTRATION_USER}: ready`);

	const {body: site} = await api('GET', '/o/headless-admin-user/v1.0/sites/by-friendly-url-path/guest');
	const {body: folders} = await api(
		'GET',
		`/o/headless-delivery/v1.0/sites/${site.id}/document-folders?search=${encodeURIComponent(UPLOAD_FOLDER)}&pageSize=50`
	);
	let folder = (folders.items || []).find((item) => item.name === UPLOAD_FOLDER);

	if (!folder) {
		({body: folder} = await api('POST', `/o/headless-delivery/v1.0/sites/${site.id}/document-folders`, {
			description: 'Temporary uploads from the partner registration page; attached files are copied to the application and removed here.',
			name: UPLOAD_FOLDER,
			viewableBy: 'Owner',
		}));
	}

	// add-resource-permission answers 404 for an individual folder on DXP
	// 2024.Q1; this sets only this role's actions on this one folder

	await jsonws('/resourcepermission/set-individual-resource-permissions', {
		actionIds: JSON.stringify(['ADD_DOCUMENT', 'VIEW']),
		companyId: company,
		groupId: site.id,
		name: 'com.liferay.document.library.kernel.model.DLFolder',
		primKey: folder.id,
		roleId: role.id,
	});

	console.log(`upload folder ${folder.id}: ready`);

	fs.writeFileSync(
		ENV_FILE,
		[
			'# Written by tools/partner-setup.mjs. Local only, git-ignored. The password is visible to',
			'# anyone who opens the registration page (docs/open-questions.md 29).',
			`VITE_REGISTRATION_USER=${REGISTRATION_USER}`,
			`VITE_REGISTRATION_PASSWORD=${password}`,
			`VITE_UPLOAD_FOLDER_ID=${folder.id}`,
			'',
		].join('\n')
	);

	console.log(`${path.relative(ROOT, ENV_FILE)}: written`);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
	writeBatchFiles();
	await upsertActions();
	await upsertValidations();
	await setUpRegistrationAccess();
}
