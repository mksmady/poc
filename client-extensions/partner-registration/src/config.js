// Wizard steps (US-002 AC2). Keys match the `step` of server validation
// errors.

export const STEPS = [
	{key: 'questionnaire', label: 'Questionnaire'},
	{key: 'company', label: 'Company'},
	{key: 'contacts', label: 'Contacts'},
	{key: 'compliance', label: 'Compliance'},
	{key: 'pos', label: 'POS locations'},
	{key: 'documents', label: 'Documents'},
	{key: 'review', label: 'Review'},
];

// Questionnaire answers are stored as JSON on PartnerApplication.questionnaire
// (open question 24). Only businessType is required.

export const QUESTIONS = [
	{
		id: 'businessType',
		label: 'What kind of business are you?',
		options: [
			['reseller', 'Reseller'],
			['distributor', 'Distributor'],
			['systemIntegrator', 'System integrator'],
			['serviceProvider', 'Service provider'],
			['other', 'Other'],
		],
		required: true,
	},
	{
		id: 'yearsInBusiness',
		label: 'How long has the company been trading?',
		options: [
			['lt2', 'Less than 2 years'],
			['2to5', '2 to 5 years'],
			['5to10', '5 to 10 years'],
			['gt10', 'More than 10 years'],
		],
	},
	{
		id: 'annualRevenue',
		label: 'Annual revenue',
		options: [
			['lt1m', 'Less than 1 million'],
			['1to10m', '1 to 10 million'],
			['10to50m', '10 to 50 million'],
			['gt50m', 'More than 50 million'],
		],
	},
	{
		id: 'customerSegment',
		label: 'Main customer segment',
		options: [
			['smb', 'Small and medium business'],
			['enterprise', 'Enterprise'],
			['publicSector', 'Public sector'],
			['consumer', 'Consumer'],
		],
	},
	{
		id: 'referral',
		label: 'How did you hear about our partner program?',
	},
];

export const EMPLOYEE_BANDS = ['1-9', '10-49', '50-249', '250-999', '1000+'];

export const CONTACT_ROLES = [
	['primary', 'Primary'],
	['finance', 'Finance'],
	['technical', 'Technical'],
	['other', 'Other'],
];

export const POS_TYPES = [
	['store', 'Store'],
	['warehouse', 'Warehouse'],
	['online', 'Online'],
	['other', 'Other'],
];

export const DOCUMENT_TYPES = [
	['tradeRegister', 'Trade register extract', true],
	['vatCertificate', 'VAT certificate', true],
	['bankLetter', 'Bank letter', false],
	['certificate', 'Certificate', false],
	['other', 'Other', false],
];

const COUNTRY_CODES = (
	'AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI ' +
	'BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM CN ' +
	'CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK ' +
	'FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM ' +
	'HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN ' +
	'KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ' +
	'ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP ' +
	'NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW ' +
	'SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF ' +
	'TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI ' +
	'VN VU WF WS YE YT ZA ZM ZW'
).split(' ');

function countryName(displayNames, code) {
	try {
		return displayNames?.of(code) || code;
	}
	catch {
		return code;
	}
}

export function countries() {
	let displayNames;

	try {
		displayNames = new Intl.DisplayNames(['en'], {type: 'region'});
	}
	catch {
		displayNames = null;
	}

	return COUNTRY_CODES.map((code) => [code, countryName(displayNames, code)])
		.sort((a, b) => a[1].localeCompare(b[1]));
}

export function emptyDraft() {
	return {
		company: {},
		compliance: {gdprConsent: false, sanctionsDeclaration: false},
		contacts: [{role: 'primary'}],
		documents: [],
		existingCompany: false,
		posLocations: [{categories: []}],
		questionnaire: {},
	};
}

/**
 * Fills the arrays and objects a draft from the service may leave out, and
 * gives an empty draft one primary contact and one location to start with.
 */
export function normalizeDraft(draft) {
	const base = emptyDraft();

	if (!draft) {
		return base;
	}

	return {
		company: {...(draft.company || {})},
		compliance: {...base.compliance, ...(draft.compliance || {})},
		contacts: draft.contacts?.length ? draft.contacts : base.contacts,
		documents: draft.documents || [],
		existingCompany: Boolean(draft.existingCompany),
		posLocations: draft.posLocations?.length
			? draft.posLocations.map((pos) => ({...pos, categories: pos.categories || []}))
			: base.posLocations,
		questionnaire: {...(draft.questionnaire || {})},
	};
}
