// US-002 field rules, shared by save and submit. The portal enforces the
// submit rules again (partner-batch/scripts/application-validate-submit.groovy);
// these give the partner a message next to each field.

export const MAX_TEXT_LENGTH = 280;

const CONTACT_ROLES = ['primary', 'finance', 'technical', 'other'];
const POS_TYPES = ['store', 'warehouse', 'online', 'other'];
const REQUIRED_DOCUMENTS = [
	['tradeRegister', 'Upload the trade register extract.'],
	['vatCertificate', 'Upload the VAT certificate.'],
];

const blank = (value) => value === null || value === undefined || String(value).trim() === '';
const isCountry = (value) => /^[A-Za-z]{2}$/.test(value || '');
const isEmail = (value) => /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test((value || '').trim());

const error = (step, field, message) => ({field, message, step});

/**
 * A row the partner has not started; the preselected role does not count.
 */
export function isBlankContact(contact) {
	return ['email', 'fullName', 'jobTitle', 'phone'].every((field) => blank(contact[field]));
}

export function isBlankPos(pos) {
	return (
		['city', 'country', 'name', 'posType', 'postalCode', 'street'].every((field) => blank(pos[field])) &&
		!(pos.categories || []).length
	);
}

/**
 * Light checks for a draft save: lengths, list values, and the fields the
 * child objects require for a row the partner has started.
 */
export function validateDraft(draft, categories = {}) {
	const errors = [];
	const length = (step, field, value) => {
		if (typeof value === 'string' && value.length > MAX_TEXT_LENGTH) {
			errors.push(error(step, field, `Use at most ${MAX_TEXT_LENGTH} characters.`));
		}
	};

	const company = draft.company || {};

	for (const field of ['companyName', 'registrationNumber', 'vatId', 'street', 'city', 'postalCode', 'website', 'employeeBand']) {
		length('company', field, company[field]);
	}

	if (!blank(company.country) && !isCountry(company.country)) {
		errors.push(error('company', 'country', 'Choose the country.'));
	}

	for (const [id, answer] of Object.entries(draft.questionnaire || {})) {
		length('questionnaire', id, answer);
	}

	(draft.contacts || []).forEach((contact, i) => {
		const prefix = `contacts[${i}].`;

		for (const field of ['fullName', 'email', 'phone', 'jobTitle']) {
			length('contacts', prefix + field, contact[field]);
		}

		if (contact.role && !CONTACT_ROLES.includes(contact.role)) {
			errors.push(error('contacts', `${prefix}role`, 'Choose a role.'));
		}
	});

	(draft.posLocations || []).forEach((pos, i) => {
		const prefix = `posLocations[${i}].`;

		for (const field of ['name', 'street', 'city', 'postalCode']) {
			length('pos', prefix + field, pos[field]);
		}

		if (!blank(pos.country) && !isCountry(pos.country)) {
			errors.push(error('pos', `${prefix}country`, 'Choose the country.'));
		}

		if (pos.posType && !POS_TYPES.includes(pos.posType)) {
			errors.push(error('pos', `${prefix}posType`, 'Choose the location type.'));
		}

		if ((pos.categories || []).some((category) => !(category in categories))) {
			errors.push(error('pos', `${prefix}categories`, 'Choose categories from the list.'));
		}
	});

	for (const field of ['certifications', 'complianceNotes']) {
		const value = draft.compliance?.[field];

		if (typeof value === 'string' && value.length > 4000) {
			errors.push(error('compliance', field, 'This text is too long.'));
		}
	}

	if (errors.length) {
		return errors;
	}

	// Rows as the partner sees them, empty ones included in the numbering

	(draft.contacts || []).forEach((contact, i) => {
		if (isBlankContact(contact)) {
			return;
		}

		if (blank(contact.fullName)) {
			errors.push(error('contacts', `contacts[${i}].fullName`, "Enter the contact's full name, or remove the contact."));
		}

		if (blank(contact.email)) {
			errors.push(error('contacts', `contacts[${i}].email`, "Enter the contact's email, or remove the contact."));
		}
	});

	(draft.posLocations || []).forEach((pos, i) => {
		if (!isBlankPos(pos) && blank(pos.name)) {
			errors.push(error('pos', `posLocations[${i}].name`, 'Enter the location name, or remove the location.'));
		}
	});

	return errors;
}

/**
 * Everything a submitted application needs (AC4 to AC8).
 */
export function validateForSubmit(draft, categories = {}) {
	const errors = validateDraft(draft, categories);
	const company = draft.company || {};
	const require = (step, field, value, message) => {
		if (blank(value)) {
			errors.push(error(step, field, message));
		}
	};

	require('questionnaire', 'businessType', draft.questionnaire?.businessType, 'Tell us what kind of business you are.');

	require('company', 'companyName', company.companyName, 'Enter the legal company name.');
	require('company', 'registrationNumber', company.registrationNumber, 'Enter the company registration number.');
	require('company', 'vatId', company.vatId, 'Enter the VAT ID.');
	require('company', 'street', company.street, 'Enter the street address.');
	require('company', 'city', company.city, 'Enter the city.');
	require('company', 'postalCode', company.postalCode, 'Enter the postal code.');
	require('company', 'website', company.website, 'Enter the company website.');
	require('company', 'employeeBand', company.employeeBand, 'Choose the number of employees.');

	if (!isCountry(company.country)) {
		errors.push(error('company', 'country', 'Choose the country.'));
	}

	const contacts = draft.contacts || [];

	contacts.forEach((contact, i) => {
		require('contacts', `contacts[${i}].fullName`, contact.fullName, "Enter the contact's full name.");

		if (!isEmail(contact.email)) {
			errors.push(error('contacts', `contacts[${i}].email`, 'Enter a valid email address.'));
		}
	});

	if (!contacts.some((contact) => contact.role === 'primary')) {
		errors.push(error('contacts', 'contacts', 'Add at least one primary contact.'));
	}

	if (!draft.compliance?.sanctionsDeclaration) {
		errors.push(error('compliance', 'sanctionsDeclaration', 'Confirm the sanctions declaration.'));
	}

	if (!draft.compliance?.gdprConsent) {
		errors.push(error('compliance', 'gdprConsent', 'Give your consent to the processing of your data.'));
	}

	const locations = draft.posLocations || [];

	if (!locations.length) {
		errors.push(error('pos', 'posLocations', 'Add at least one location.'));
	}

	locations.forEach((pos, i) => {
		const prefix = `posLocations[${i}].`;

		require('pos', `${prefix}name`, pos.name, 'Enter the location name.');
		require('pos', `${prefix}street`, pos.street, 'Enter the street address.');
		require('pos', `${prefix}city`, pos.city, 'Enter the city.');

		if (!isCountry(pos.country)) {
			errors.push(error('pos', `${prefix}country`, 'Choose the country.'));
		}

		require('pos', `${prefix}posType`, pos.posType, 'Choose the location type.');

		if (!(pos.categories || []).length) {
			errors.push(error('pos', `${prefix}categories`, 'Choose at least one product category.'));
		}
	});

	const uploaded = new Set((draft.documents || []).map((document) => document.documentType));

	for (const [type, message] of REQUIRED_DOCUMENTS) {
		if (!uploaded.has(type)) {
			errors.push(error('documents', type, message));
		}
	}

	// Duplicate field messages (draft check and submit check) once each

	const seen = new Set();

	return errors.filter((e) => {
		const id = `${e.step}|${e.field}`;

		return seen.has(id) ? false : seen.add(id);
	});
}
