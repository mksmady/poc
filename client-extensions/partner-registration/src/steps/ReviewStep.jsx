import {
	CONTACT_ROLES,
	DOCUMENT_TYPES,
	POS_TYPES,
	QUESTIONS,
	STEPS,
	countries,
} from '../config';

const COUNTRY_NAMES = Object.fromEntries(countries());

function label(pairs, key) {
	return pairs.find(([k]) => k === key)?.[1] || key || '—';
}

function Row({name, value}) {
	return (
		<div className="pr-row">
			<dt>{name}</dt>
			<dd>{value === undefined || value === null || value === '' ? '—' : value}</dd>
		</div>
	);
}

function Section({children, onEdit, readOnly, stepKey}) {
	const step = STEPS.find((s) => s.key === stepKey);

	return (
		<section className="pr-review-section">
			<div className="pr-review-head">
				<h3 className="h5">{step.label}</h3>

				{!readOnly && (
					<button
						aria-label={`Edit ${step.label}`}
						className="btn btn-link btn-sm"
						onClick={() => onEdit(stepKey)}
						type="button"
					>
						Edit
					</button>
				)}
			</div>

			<dl>{children}</dl>
		</section>
	);
}

export default function ReviewStep({categories, draft, errorCount, onEdit, readOnly}) {
	const {company, compliance} = draft;

	return (
		<>
			{errorCount > 0 && (
				<div className="alert alert-danger" role="alert">
					{errorCount === 1
						? '1 item needs your attention before you can submit.'
						: `${errorCount} items need your attention before you can submit.`}
				</div>
			)}

			<Section onEdit={onEdit} readOnly={readOnly} stepKey="questionnaire">
				{QUESTIONS.map((question) => (
					<Row
						key={question.id}
						name={question.label}
						value={
							question.options
								? draft.questionnaire[question.id] &&
									label(question.options, draft.questionnaire[question.id])
								: draft.questionnaire[question.id]
						}
					/>
				))}
			</Section>

			<Section onEdit={onEdit} readOnly={readOnly} stepKey="company">
				<Row name="Legal name" value={company.companyName} />
				<Row name="Registration number" value={company.registrationNumber} />
				<Row name="VAT ID" value={company.vatId} />
				<Row
					name="Address"
					value={[company.street, company.postalCode, company.city]
						.filter(Boolean)
						.join(', ')}
				/>
				<Row
					name="Country"
					value={company.country && (COUNTRY_NAMES[company.country] || company.country)}
				/>
				<Row name="Website" value={company.website} />
				<Row name="Employees" value={company.employeeBand} />
			</Section>

			<Section onEdit={onEdit} readOnly={readOnly} stepKey="contacts">
				{draft.contacts.map((contact, index) => (
					<Row
						key={contact.id ?? index}
						name={label(CONTACT_ROLES, contact.role)}
						value={[contact.fullName, contact.email, contact.phone]
							.filter(Boolean)
							.join(' · ')}
					/>
				))}
			</Section>

			<Section onEdit={onEdit} readOnly={readOnly} stepKey="compliance">
				<Row
					name="Sanctions declaration"
					value={compliance.sanctionsDeclaration ? 'Confirmed' : 'Not confirmed'}
				/>
				<Row
					name="GDPR consent"
					value={compliance.gdprConsent ? 'Given' : 'Not given'}
				/>
				<Row name="Certifications" value={compliance.certifications} />
				<Row name="Notes" value={compliance.complianceNotes} />
			</Section>

			<Section onEdit={onEdit} readOnly={readOnly} stepKey="pos">
				{draft.posLocations.map((pos, index) => (
					<Row
						key={pos.id ?? index}
						name={pos.name || `Location ${index + 1}`}
						value={[
							label(POS_TYPES, pos.posType),
							[pos.street, pos.city, pos.country].filter(Boolean).join(', '),
							pos.categories.map((key) => categories[key] || key).join(', '),
						]
							.filter(Boolean)
							.join(' · ')}
					/>
				))}
			</Section>

			<Section onEdit={onEdit} readOnly={readOnly} stepKey="documents">
				{draft.documents.length ? (
					draft.documents.map((document) => (
						<Row
							key={document.id}
							name={label(DOCUMENT_TYPES, document.documentType)}
							value={document.fileName}
						/>
					))
				) : (
					<Row name="Documents" value="None uploaded" />
				)}
			</Section>
		</>
	);
}
