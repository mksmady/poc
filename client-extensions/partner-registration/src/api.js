// US-002 registration over Liferay's Object API, signed in with basic auth as
// the "Partner Registration Client" user (docs/architecture.md, "Register").
// That user's password is in this bundle, so anyone can read it: the Groovy
// validation rules in partner-batch are what keep it to registration work
// (docs/open-questions.md 29). The interface matches what the wizard used
// with partner-service: open, saveDraft, uploadDocument, deleteDocument,
// submit, each answering {session, readOnly, status, draft, ...}.

import {validateDraft, validateForSubmit, isBlankContact, isBlankPos} from './rules';

export class ApiError extends Error {
	constructor(message, status, errors = []) {
		super(message);

		this.errors = errors;
		this.status = status;
	}
}

export const INVALID_LINK =
	'This link is no longer valid. Ask your account manager for a new invitation.';

const NOT_EDITABLE = 'This application has been submitted and can no longer be changed.';

const EDITABLE = ['invitationSent', 'registrationInProgress', 'revisionRequested'];

const READ_ONLY = ['submitted', 'approvalPending', 'underLegalReview', 'contractPending'];

const PATHS = {
	applications: '/o/c/partnerapplications',
	contacts: '/o/c/applicationcontacts',
	documents: '/o/c/applicationdocuments',
	invitations: '/o/c/partnerinvitations',
	pos: '/o/c/poslocations',
	settings: '/o/c/partnersettingses',
};

const PARENT = {
	contacts: 'r_applicationContacts_c_partnerApplicationId',
	documents: 'r_applicationDocuments_c_partnerApplicationId',
	pos: 'r_applicationPosLocations_c_partnerApplicationId',
};

const COMPANY_FIELDS = [
	'city',
	'companyName',
	'country',
	'employeeBand',
	'postalCode',
	'registrationNumber',
	'street',
	'vatId',
	'website',
];

const FILE_TYPES = [
	{extension: 'pdf', magic: [0x25, 0x50, 0x44, 0x46, 0x2d], type: 'application/pdf'},
	{extension: 'png', magic: [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a], type: 'image/png'},
	{extension: 'jpg', magic: [0xff, 0xd8, 0xff], type: 'image/jpeg'},
];

export async function sha256Hex(text) {
	const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));

	return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/**
 * The file type from its first bytes, never from the name (AC8).
 */
export async function sniff(file) {
	const head = new Uint8Array(await readBytes(file.slice(0, 8)));

	return FILE_TYPES.find(({magic}) => magic.every((byte, i) => head[i] === byte)) || null;
}

function readBytes(blob) {
	if (typeof blob.arrayBuffer === 'function') {
		return blob.arrayBuffer();
	}

	return new Promise((resolve, reject) => {
		const reader = new FileReader();

		reader.onerror = () => reject(reader.error);
		reader.onload = () => resolve(reader.result);
		reader.readAsArrayBuffer(blob);
	});
}

function key(value) {
	return value && typeof value === 'object' ? value.key : value ?? null;
}

function normalizeId(value) {
	const normalized = (value || '').replace(/[\s.\-/]/g, '').toUpperCase();

	return normalized || null;
}

function trimOrNull(value) {
	const trimmed = typeof value === 'string' ? value.trim() : value;

	return trimmed === '' || trimmed === undefined ? null : trimmed;
}

function wholeSeconds(date = new Date()) {
	return new Date(Math.floor(date.getTime() / 1000) * 1000).toISOString();
}

export function createApi({baseUrl = '', categories = {}, folderId, password, user}) {
	const authorization = 'Basic ' + btoa(`${user}:${password}`);

	async function request(path, {body, form, method = 'GET'} = {}) {
		let response;

		try {
			response = await fetch(baseUrl + path, {
				body: form || (body === undefined ? undefined : JSON.stringify(body)),

				// No cookies: a portal session would take precedence over the
				// registration user

				credentials: 'omit',
				headers: {
					Accept: 'application/json',
					Authorization: authorization,
					...(body !== undefined && !form ? {'Content-Type': 'application/json'} : {}),
				},
				method,
			});
		}
		catch {
			throw new ApiError(
				'We could not reach the portal. Check your connection and try again.',
				0
			);
		}

		if (response.status === 204) {
			return null;
		}

		let payload = null;

		try {
			payload = await response.json();
		}
		catch {
			// Handled below.
		}

		if (!response.ok) {
			throw new ApiError(liferayMessage(payload), response.status);
		}

		return payload;
	}

	const list = async (collection, filter) => {
		const params = new URLSearchParams({pageSize: '-1'});

		if (filter) {
			params.set('filter', filter);
		}

		return (await request(`${PATHS[collection]}?${params}`)).items || [];
	};

	const children = (collection, applicationId) =>
		list(collection, `${PARENT[collection]} eq '${applicationId}'`);

	async function load(context) {
		const application = await request(`${PATHS.applications}/${context.applicationId}`);
		const status = key(application.applicationStatus);
		const [contacts, pos, documents] = await Promise.all([
			children('contacts', context.applicationId),
			children('pos', context.applicationId),
			children('documents', context.applicationId),
		]);

		let questionnaire = {};

		try {
			questionnaire = application.questionnaire ? JSON.parse(application.questionnaire) : {};
		}
		catch {
			questionnaire = {};
		}

		return {
			application,
			draft: {
				company: Object.fromEntries(COMPANY_FIELDS.map((field) => [field, application[field] ?? null])),
				compliance: {
					certifications: application.certifications ?? null,
					complianceNotes: application.complianceNotes ?? null,
					gdprConsent: Boolean(application.gdprConsent),
					sanctionsDeclaration: Boolean(application.sanctionsDeclaration),
				},
				contacts: contacts.map((contact) => ({
					email: contact.email ?? null,
					fullName: contact.fullName ?? null,
					id: contact.id,
					jobTitle: contact.jobTitle ?? null,
					phone: contact.phone ?? null,
					role: key(contact.role),
				})),
				documents: documents.map((document) => ({
					documentType: key(document.documentType),
					fileName: document.file?.name || `Document ${document.id}`,
					id: document.id,
					note: document.note ?? null,
				})),
				existingCompany: Boolean(application.existingCompany),
				posLocations: pos.map((location) => ({
					categories: (location.categories || '').split(',').map((c) => c.trim()).filter(Boolean),
					city: location.city ?? null,
					country: location.country ?? null,
					id: location.id,
					name: location.name ?? null,
					posType: key(location.posType),
					postalCode: location.postalCode ?? null,
					street: location.street ?? null,
				})),
				questionnaire,
			},
			status,
		};
	}

	async function state(context) {
		const invitation = await request(`${PATHS.invitations}/${context.invitationId}`);

		if (invitation.tokenHash !== context.tokenHash) {

			// Resent since this page opened: the old link no longer works

			throw new ApiError(INVALID_LINK, 404);
		}

		const loaded = await load(context);
		const mode = modeOf(invitation, loaded.status);

		if (mode === 'invalid') {
			throw new ApiError(INVALID_LINK, 404);
		}

		return {...loaded, invitation, readOnly: mode !== 'edit'};
	}

	function response(context, loaded) {
		return {
			draft: loaded.draft,
			readOnly: loaded.readOnly,
			session: context,
			status: loaded.status,
		};
	}

	async function editable(context) {
		const loaded = await state(context);

		if (loaded.readOnly) {
			throw new ApiError(NOT_EDITABLE, 409);
		}

		return loaded;
	}

	/**
	 * AC9: a VAT or registration number of an authorized partner means
	 * existing-company mode; one of a terminated partner sets the
	 * watchlist flag, without telling the partner.
	 */
	async function lookupCompany(applicationId, company) {
		const vatId = normalizeId(company.vatId);
		const registrationNumber = normalizeId(company.registrationNumber);

		if (!vatId && !registrationNumber) {
			return {};
		}

		const others = await list(
			'applications',
			"applicationStatus eq 'authorized' or applicationStatus eq 'terminated'"
		);

		const result = {};

		for (const other of others) {
			if (other.id === applicationId) {
				continue;
			}

			const match =
				(vatId && vatId === normalizeId(other.vatId)) ||
				(registrationNumber && registrationNumber === normalizeId(other.registrationNumber));

			if (!match) {
				continue;
			}

			if (key(other.applicationStatus) === 'terminated') {
				result.terminated = true;
			}
			else if (!result.existing && other.r_applicationAccount_accountEntryId) {
				result.existing = other;
			}
		}

		return result;
	}

	async function syncChildren(collection, applicationId, rows, fieldsOf) {
		const existing = new Set((await children(collection, applicationId)).map((child) => child.id));
		const kept = new Set();

		for (const row of rows) {
			const fields = fieldsOf(row);

			if (row.id && existing.has(row.id) && !kept.has(row.id)) {
				kept.add(row.id);

				await request(`${PATHS[collection]}/${row.id}`, {body: fields, method: 'PATCH'});
			}
			else {
				await request(PATHS[collection], {
					body: {...fields, [PARENT[collection]]: applicationId},
					method: 'POST',
				});
			}
		}

		for (const id of existing) {
			if (!kept.has(id)) {
				await request(`${PATHS[collection]}/${id}`, {method: 'DELETE'});
			}
		}
	}

	return {
		async deleteDocument(context, documentId) {
			const loaded = await editable(context);

			if (!loaded.draft.documents.some((document) => document.id === documentId)) {
				throw new ApiError('Not found', 404);
			}

			await request(`${PATHS.documents}/${documentId}`, {method: 'DELETE'});

			return response(context, await state(context));
		},

		/**
		 * AC1: checks the link and opens the wizard. The first valid open
		 * moves the application to registrationInProgress; Liferay records
		 * the change once even when two tabs open at the same time.
		 */
		async open(token) {
			if (!token || token.length > 100) {
				throw new ApiError(INVALID_LINK, 404);
			}

			const tokenHash = await sha256Hex(token);
			const [invitation] = await list('invitations', `tokenHash eq '${tokenHash}'`);

			if (!invitation) {
				throw new ApiError(INVALID_LINK, 404);
			}

			const [application] = await list(
				'applications',
				`r_invitationApplication_c_partnerInvitationId eq '${invitation.id}'`
			);

			if (!application || modeOf(invitation, key(application.applicationStatus)) === 'invalid') {
				throw new ApiError(INVALID_LINK, 404);
			}

			const context = {applicationId: application.id, invitationId: invitation.id, tokenHash};

			if (key(application.applicationStatus) === 'invitationSent') {
				await request(`${PATHS.applications}/${application.id}`, {
					body: {applicationStatus: 'registrationInProgress'},
					method: 'PATCH',
				});
			}

			const loaded = await state(context);
			const [settings] = await list('settings');

			return {
				...response(context, loaded),
				categories,
				expiresAt: invitation.expiresAt,
				invitee: {
					email: invitation.email,
					firstName: invitation.firstName,
					lastName: invitation.lastName,
				},
				maxUploadMb: settings?.maxUploadMb || 10,
			};
		},

		/**
		 * AC3. Checked before the first write so a refused save changes
		 * nothing; empty contact and location rows are not stored.
		 */
		async saveDraft(context, draft) {
			const loaded = await editable(context);
			const errors = validateDraft(draft, categories);

			if (errors.length) {
				throw new ApiError('Some information is missing or not valid', 422, errors);
			}

			const contacts = draft.contacts.filter((contact) => !isBlankContact(contact));
			const posLocations = draft.posLocations.filter((pos) => !isBlankPos(pos));
			const company = draft.company || {};
			const match = await lookupCompany(context.applicationId, company);

			const fields = {
				certifications: trimOrNull(draft.compliance.certifications),
				complianceNotes: trimOrNull(draft.compliance.complianceNotes),
				gdprConsent: Boolean(draft.compliance.gdprConsent),
				questionnaire: JSON.stringify(draft.questionnaire || {}),
				requestedCategories: [...new Set(posLocations.flatMap((pos) => pos.categories || []))].join(','),
				sanctionsDeclaration: Boolean(draft.compliance.sanctionsDeclaration),
			};

			if (match.existing) {
				for (const field of COMPANY_FIELDS) {
					fields[field] = match.existing[field] ?? null;
				}

				fields.existingCompany = true;
				fields.r_applicationAccount_accountEntryId = match.existing.r_applicationAccount_accountEntryId;
			}
			else {
				for (const field of COMPANY_FIELDS) {
					fields[field] = trimOrNull(company[field]);
				}

				if (fields.country) {
					fields.country = fields.country.toUpperCase();
				}

				if (loaded.application.existingCompany) {
					fields.existingCompany = false;
				}
			}

			if (match.terminated) {
				fields.watchlistMatch = true;
			}

			await request(`${PATHS.applications}/${context.applicationId}`, {body: fields, method: 'PATCH'});

			await syncChildren('contacts', context.applicationId, contacts, (contact) => ({
				email: trimOrNull(contact.email),
				fullName: trimOrNull(contact.fullName),
				jobTitle: trimOrNull(contact.jobTitle),
				phone: trimOrNull(contact.phone),
				role: contact.role || null,
			}));

			await syncChildren('pos', context.applicationId, posLocations, (pos) => ({
				categories: [...new Set(pos.categories || [])].join(','),
				city: trimOrNull(pos.city),
				country: trimOrNull(pos.country)?.toUpperCase() ?? null,
				name: trimOrNull(pos.name),
				posType: pos.posType || null,
				postalCode: trimOrNull(pos.postalCode),
				street: trimOrNull(pos.street),
			}));

			return response(context, await state(context));
		},

		/**
		 * AC10: the same checks the portal runs (PM_VAL_APPLICATION_SUBMIT),
		 * shown field by field; the portal then routes the application and
		 * sends PM_SUBMITTED.
		 */
		async submit(context) {
			const loaded = await editable(context);
			const errors = validateForSubmit(loaded.draft, categories);

			if (errors.length) {
				throw new ApiError('Some information is missing or not valid', 422, errors);
			}

			await request(`${PATHS.applications}/${context.applicationId}`, {
				body: {applicationStatus: 'submitted', submittedAt: wholeSeconds()},
				method: 'PATCH',
			});

			return response(context, await state(context));
		},

		/**
		 * AC8: type from the content, size from PartnerSettings. The file
		 * goes to the upload folder, is attached (Liferay copies it into the
		 * application's private storage), then the folder copy is removed.
		 */
		async uploadDocument(context, {documentType, file, note}) {
			await editable(context);

			const [settings] = await list('settings');
			const maxUploadMb = settings?.maxUploadMb || 10;

			if (file.size > maxUploadMb * 1024 * 1024) {
				throw new ApiError(`The file is larger than ${maxUploadMb} MB`, 413);
			}

			const fileType = await sniff(file);

			if (!fileType) {
				throw new ApiError('Upload a PDF, JPG or PNG file', 415);
			}

			const base = (file.name || 'document').replace(/^.*[\\/]/, '').replace(/\.[^.]*$/, '').replace(/[^A-Za-z0-9._ -]/g, '_').slice(0, 100) || 'document';
			const fileName = `${base}.${fileType.extension}`;

			const form = new FormData();

			form.append('file', new File([file], fileName, {type: fileType.type}));
			form.append(
				'document',
				JSON.stringify({title: `pm-upload-${crypto.randomUUID()}-${fileName}`, viewableBy: 'Owner'})
			);

			const uploaded = await request(`/o/headless-delivery/v1.0/document-folders/${folderId}/documents`, {
				form,
				method: 'POST',
			});

			try {
				await request(PATHS.documents, {
					body: {
						documentType,
						file: uploaded.id,
						note: trimOrNull(note),
						[PARENT.documents]: context.applicationId,
					},
					method: 'POST',
				});
			}
			finally {
				await request(`/o/headless-delivery/v1.0/documents/${uploaded.id}`, {method: 'DELETE'}).catch(() => {});
			}

			return response(context, await state(context));
		},
	};
}

/**
 * Edit while the link is active and unexpired and the application is open;
 * read-only after submit while it is in review (AC11).
 */
export function modeOf(invitation, status, now = Date.now()) {
	const state = key(invitation.state);

	if (state === 'active') {
		if (!invitation.expiresAt || Date.parse(invitation.expiresAt) <= now) {
			return 'invalid';
		}

		if (EDITABLE.includes(status)) {
			return 'edit';
		}

		return READ_ONLY.includes(status) ? 'readOnly' : 'invalid';
	}

	return state === 'used' && READ_ONLY.includes(status) ? 'readOnly' : 'invalid';
}

/**
 * Liferay answers validation rules with
 * {detail: '[{"errorMessage": "..."}]'} and other errors with {title}.
 */
export function liferayMessage(payload) {
	if (payload?.detail) {
		try {
			const details = JSON.parse(payload.detail);

			if (Array.isArray(details) && details[0]?.errorMessage) {
				return details.map((d) => d.errorMessage).join(' ');
			}
		}
		catch {
			// Not JSON.
		}
	}

	return payload?.title || 'Something went wrong. Try again in a moment.';
}

export function apiFromEnv() {
	return createApi({
		categories: Object.fromEntries(
			(import.meta.env.VITE_CATEGORIES ||
				'networking=Networking,security=Security,storage=Storage,servers=Servers,software=Software,services=Services')
				.split(',')
				.map((pair) => pair.split('='))
				.filter(([k]) => k)
				.map(([k, label]) => [k.trim(), (label || k).trim()])
		),
		folderId: import.meta.env.VITE_UPLOAD_FOLDER_ID,
		password: import.meta.env.VITE_REGISTRATION_PASSWORD || '',
		user: import.meta.env.VITE_REGISTRATION_USER || '',
	});
}
