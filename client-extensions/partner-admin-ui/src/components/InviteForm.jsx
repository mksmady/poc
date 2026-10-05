import {useState} from 'react';

import {createInvitation} from '../api';
import {isEmail} from '../format';

const EMPTY = {email: '', firstName: '', lastName: ''};

const LABELS = {
	email: 'Email',
	firstName: 'First name',
	lastName: 'Last name',
};

export function validate(values) {
	const errors = {};

	for (const name of Object.keys(LABELS)) {
		if (!values[name].trim()) {
			errors[name] = `${LABELS[name]} is required.`;
		}
	}

	if (!errors.email && !isEmail(values.email)) {
		errors.email = 'Enter a valid email address.';
	}

	return errors;
}

/**
 * US-001 AC1. Duplicate active invitations (AC2) are refused by
 * partner-service; its message is shown as the form error.
 */
export default function InviteForm({onCancel, onCreated}) {
	const [errors, setErrors] = useState({});
	const [serverError, setServerError] = useState(null);
	const [submitting, setSubmitting] = useState(false);
	const [values, setValues] = useState(EMPTY);

	const onChange = (event) => {
		const {name, value} = event.target;

		setValues((previous) => ({...previous, [name]: value}));
		setErrors((previous) => ({...previous, [name]: undefined}));
	};

	const onSubmit = async (event) => {
		event.preventDefault();

		const nextErrors = validate(values);

		setErrors(nextErrors);
		setServerError(null);

		if (Object.keys(nextErrors).length) {
			return;
		}

		const invitee = {
			email: values.email.trim(),
			firstName: values.firstName.trim(),
			lastName: values.lastName.trim(),
		};

		setSubmitting(true);

		try {
			const result = await createInvitation(invitee);

			setValues(EMPTY);
			onCreated(result, invitee);
		}
		catch (error) {
			setServerError(error.message);
		}
		finally {
			setSubmitting(false);
		}
	};

	const field = (name, type = 'text') => {
		const id = `pa-invite-${name}`;

		return (
			<div className={`form-group${errors[name] ? ' has-error' : ''}`}>
				<label htmlFor={id}>
					{LABELS[name]}

					<span aria-hidden="true" className="reference-mark text-warning">
						{' *'}
					</span>
				</label>

				<input
					aria-describedby={errors[name] ? `${id}-error` : undefined}
					aria-invalid={errors[name] ? 'true' : undefined}
					aria-required="true"
					autoComplete="off"
					className="form-control"
					disabled={submitting}
					id={id}
					name={name}
					onChange={onChange}
					type={type}
					value={values[name]}
				/>

				{errors[name] && (
					<div className="form-feedback-group">
						<div className="form-feedback-item" id={`${id}-error`}>
							{errors[name]}
						</div>
					</div>
				)}
			</div>
		);
	};

	return (
		<form
			aria-labelledby="pa-invite-title"
			className="sheet sheet-lg mb-4"
			noValidate
			onSubmit={onSubmit}
		>
			<h2 className="sheet-title" id="pa-invite-title">
				Invite a partner
			</h2>

			<p className="text-secondary">
				The invitee gets an email with a secure registration link.
			</p>

			{serverError && (
				<div className="alert alert-danger" role="alert">
					{serverError}
				</div>
			)}

			<div className="pa-form-row">
				{field('firstName')}

				{field('lastName')}
			</div>

			{field('email', 'email')}

			<div className="sheet-footer">
				<button
					className="btn btn-primary"
					disabled={submitting}
					type="submit"
				>
					{submitting ? 'Sending…' : 'Send invitation'}
				</button>

				<button
					className="btn btn-secondary ml-2"
					disabled={submitting}
					onClick={onCancel}
					type="button"
				>
					Cancel
				</button>
			</div>
		</form>
	);
}
