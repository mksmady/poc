import {useId} from 'react';

function Feedback({error, id}) {
	if (!error) {
		return null;
	}

	return (
		<div className="form-feedback-group">
			<div className="form-feedback-item" id={id}>
				{error}
			</div>
		</div>
	);
}

function Label({htmlFor, label, required}) {
	return (
		<label htmlFor={htmlFor}>
			{label}

			{required && (
				<span aria-hidden="true" className="reference-mark text-warning">
					{' *'}
				</span>
			)}
		</label>
	);
}

export function TextField({
	error,
	label,
	multiline,
	onChange,
	required,
	type = 'text',
	value,
}) {
	const id = useId();
	const Input = multiline ? 'textarea' : 'input';

	return (
		<div className={`form-group${error ? ' has-error' : ''}`}>
			<Label htmlFor={id} label={label} required={required} />

			<Input
				aria-describedby={error ? `${id}-error` : undefined}
				aria-invalid={error ? 'true' : undefined}
				aria-required={required ? 'true' : undefined}
				className="form-control"
				id={id}
				onChange={(event) => onChange(event.target.value)}
				type={multiline ? undefined : type}
				value={value ?? ''}
			/>

			<Feedback error={error} id={`${id}-error`} />
		</div>
	);
}

export function SelectField({error, label, onChange, options, required, value}) {
	const id = useId();

	return (
		<div className={`form-group${error ? ' has-error' : ''}`}>
			<Label htmlFor={id} label={label} required={required} />

			<select
				aria-describedby={error ? `${id}-error` : undefined}
				aria-invalid={error ? 'true' : undefined}
				aria-required={required ? 'true' : undefined}
				className="form-control"
				id={id}
				onChange={(event) => onChange(event.target.value || null)}
				value={value ?? ''}
			>
				<option value="">Choose…</option>

				{options.map(([key, text]) => (
					<option key={key} value={key}>
						{text}
					</option>
				))}
			</select>

			<Feedback error={error} id={`${id}-error`} />
		</div>
	);
}

export function CheckboxField({checked, error, label, onChange, required}) {
	const id = useId();

	return (
		<div className={`form-group${error ? ' has-error' : ''}`}>
			<div className="custom-checkbox custom-control">
				<label htmlFor={id}>
					<input
						aria-describedby={error ? `${id}-error` : undefined}
						aria-invalid={error ? 'true' : undefined}
						aria-required={required ? 'true' : undefined}
						checked={Boolean(checked)}
						className="custom-control-input"
						id={id}
						onChange={(event) => onChange(event.target.checked)}
						type="checkbox"
					/>

					<span className="custom-control-label">
						<span className="custom-control-label-text">
							{label}

							{required && (
								<span
									aria-hidden="true"
									className="reference-mark text-warning"
								>
									{' *'}
								</span>
							)}
						</span>
					</span>
				</label>
			</div>

			<Feedback error={error} id={`${id}-error`} />
		</div>
	);
}

export function StepError({error}) {
	if (!error) {
		return null;
	}

	return (
		<div className="alert alert-danger" role="alert">
			{error}
		</div>
	);
}
