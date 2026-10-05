import {
	CONTACT_ROLES,
	EMPLOYEE_BANDS,
	POS_TYPES,
	QUESTIONS,
	countries,
} from '../config';
import {CheckboxField, SelectField, TextField} from '../components/Fields';

const COUNTRIES = countries();

/**
 * Each step gets the draft, an update function, the field errors of that
 * step ({field: message}) and readOnly. Inputs are disabled through the
 * wizard's fieldset when readOnly.
 */

export function QuestionnaireStep({draft, errors, update}) {
	return (
		<>
			{QUESTIONS.map((question) =>
				question.options ? (
					<SelectField
						error={errors[question.id]}
						key={question.id}
						label={question.label}
						onChange={(value) =>
							update((d) => {
								d.questionnaire[question.id] = value;
							})
						}
						options={question.options}
						required={question.required}
						value={draft.questionnaire[question.id]}
					/>
				) : (
					<TextField
						error={errors[question.id]}
						key={question.id}
						label={question.label}
						onChange={(value) =>
							update((d) => {
								d.questionnaire[question.id] = value;
							})
						}
						required={question.required}
						value={draft.questionnaire[question.id]}
					/>
				)
			)}
		</>
	);
}

export function CompanyStep({draft, errors, readOnly, update}) {
	const company = draft.company;
	const locked = readOnly || draft.existingCompany;

	const set = (field) => (value) =>
		update((d) => {
			d.company[field] = value;
		});

	const text = (field, label, type) => (
		<TextField
			error={errors[field]}
			label={label}
			onChange={set(field)}
			required
			type={type}
			value={company[field]}
		/>
	);

	return (
		<>
			{draft.existingCompany && (
				<div className="alert alert-info" role="status">
					This company is already one of our partners. Its details
					are shown as we have them; you are registering as a new
					contact. To register a different company, change the VAT ID
					or registration number.
				</div>
			)}

			<div className="pr-grid">
				{text('vatId', 'VAT ID')}

				{text('registrationNumber', 'Registration number')}
			</div>

			<fieldset className="pr-plain" disabled={locked}>
				{text('companyName', 'Legal company name')}

				{text('street', 'Street and number')}

				<div className="pr-grid">
					{text('postalCode', 'Postal code')}

					{text('city', 'City')}
				</div>

				<SelectField
					error={errors.country}
					label="Country"
					onChange={set('country')}
					options={COUNTRIES}
					required
					value={company.country}
				/>

				{text('website', 'Website', 'url')}

				<SelectField
					error={errors.employeeBand}
					label="Number of employees"
					onChange={set('employeeBand')}
					options={EMPLOYEE_BANDS.map((band) => [band, band])}
					required
					value={company.employeeBand}
				/>
			</fieldset>
		</>
	);
}

export function ContactsStep({draft, errors, update}) {
	return (
		<>
			{errors.contacts && (
				<div className="alert alert-danger" role="alert">
					{errors.contacts}
				</div>
			)}

			{draft.contacts.map((contact, index) => {
				const set = (field) => (value) =>
					update((d) => {
						d.contacts[index][field] = value;
					});

				const error = (field) => errors[`contacts[${index}].${field}`];

				return (
					<fieldset className="pr-item" key={contact.id ?? `new-${index}`}>
						<legend className="h5">Contact {index + 1}</legend>

						<div className="pr-grid">
							<TextField
								error={error('fullName')}
								label="Full name"
								onChange={set('fullName')}
								required
								value={contact.fullName}
							/>

							<TextField
								error={error('email')}
								label="Email"
								onChange={set('email')}
								required
								type="email"
								value={contact.email}
							/>

							<TextField
								error={error('phone')}
								label="Phone"
								onChange={set('phone')}
								type="tel"
								value={contact.phone}
							/>

							<TextField
								error={error('jobTitle')}
								label="Job title"
								onChange={set('jobTitle')}
								value={contact.jobTitle}
							/>

							<SelectField
								error={error('role')}
								label="Role"
								onChange={set('role')}
								options={CONTACT_ROLES}
								value={contact.role}
							/>
						</div>

						{draft.contacts.length > 1 && (
							<button
								className="btn btn-secondary btn-sm"
								onClick={() =>
									update((d) => {
										d.contacts.splice(index, 1);
									})
								}
								type="button"
							>
								Remove contact {index + 1}
							</button>
						)}
					</fieldset>
				);
			})}

			<button
				className="btn btn-secondary"
				onClick={() =>
					update((d) => {
						d.contacts.push({role: 'other'});
					})
				}
				type="button"
			>
				Add contact
			</button>
		</>
	);
}

export function ComplianceStep({draft, errors, update}) {
	const set = (field) => (value) =>
		update((d) => {
			d.compliance[field] = value;
		});

	return (
		<>
			<CheckboxField
				checked={draft.compliance.sanctionsDeclaration}
				error={errors.sanctionsDeclaration}
				label="We declare that the company, its owners and directors are not subject to sanctions."
				onChange={set('sanctionsDeclaration')}
				required
			/>

			<CheckboxField
				checked={draft.compliance.gdprConsent}
				error={errors.gdprConsent}
				label="We consent to the processing of the data in this application for the partner program (GDPR)."
				onChange={set('gdprConsent')}
				required
			/>

			<TextField
				error={errors.certifications}
				label="Certifications"
				multiline
				onChange={set('certifications')}
				value={draft.compliance.certifications}
			/>

			<TextField
				error={errors.complianceNotes}
				label="Notes"
				multiline
				onChange={set('complianceNotes')}
				value={draft.compliance.complianceNotes}
			/>
		</>
	);
}

export function PosStep({categories, draft, errors, update}) {
	return (
		<>
			{errors.posLocations && (
				<div className="alert alert-danger" role="alert">
					{errors.posLocations}
				</div>
			)}

			{draft.posLocations.map((pos, index) => {
				const set = (field) => (value) =>
					update((d) => {
						d.posLocations[index][field] = value;
					});

				const error = (field) =>
					errors[`posLocations[${index}].${field}`];

				const toggle = (key, on) =>
					update((d) => {
						const list = d.posLocations[index].categories.filter(
							(item) => item !== key
						);

						if (on) {
							list.push(key);
						}

						d.posLocations[index].categories = list;
					});

				return (
					<fieldset className="pr-item" key={pos.id ?? `new-${index}`}>
						<legend className="h5">Location {index + 1}</legend>

						<TextField
							error={error('name')}
							label="Location name"
							onChange={set('name')}
							required
							value={pos.name}
						/>

						<TextField
							error={error('street')}
							label="Street and number"
							onChange={set('street')}
							required
							value={pos.street}
						/>

						<div className="pr-grid">
							<TextField
								error={error('postalCode')}
								label="Postal code"
								onChange={set('postalCode')}
								value={pos.postalCode}
							/>

							<TextField
								error={error('city')}
								label="City"
								onChange={set('city')}
								required
								value={pos.city}
							/>

							<SelectField
								error={error('country')}
								label="Country"
								onChange={set('country')}
								options={COUNTRIES}
								required
								value={pos.country}
							/>

							<SelectField
								error={error('posType')}
								label="Type"
								onChange={set('posType')}
								options={POS_TYPES}
								required
								value={pos.posType}
							/>
						</div>

						<fieldset
							className={`form-group pr-plain${error('categories') ? ' has-error' : ''}`}
						>
							<legend className="pr-legend">
								Product categories
								<span
									aria-hidden="true"
									className="reference-mark text-warning"
								>
									{' *'}
								</span>
							</legend>

							{Object.entries(categories).map(([key, label]) => (
								<CheckboxField
									checked={pos.categories.includes(key)}
									key={key}
									label={label}
									onChange={(on) => toggle(key, on)}
								/>
							))}

							{error('categories') && (
								<div className="form-feedback-item">
									{error('categories')}
								</div>
							)}
						</fieldset>

						{draft.posLocations.length > 1 && (
							<button
								className="btn btn-secondary btn-sm"
								onClick={() =>
									update((d) => {
										d.posLocations.splice(index, 1);
									})
								}
								type="button"
							>
								Remove location {index + 1}
							</button>
						)}
					</fieldset>
				);
			})}

			<button
				className="btn btn-secondary"
				onClick={() =>
					update((d) => {
						d.posLocations.push({categories: []});
					})
				}
				type="button"
			>
				Add location
			</button>
		</>
	);
}
