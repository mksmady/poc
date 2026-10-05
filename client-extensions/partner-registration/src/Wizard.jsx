import {useRef, useState} from 'react';

import {STEPS, normalizeDraft} from './config';
import DocumentsStep from './steps/DocumentsStep';
import ReviewStep from './steps/ReviewStep';
import {
	CompanyStep,
	ComplianceStep,
	ContactsStep,
	PosStep,
	QuestionnaireStep,
} from './steps/Steps';

const STEP_COMPONENTS = {
	company: CompanyStep,
	compliance: ComplianceStep,
	contacts: ContactsStep,
	pos: PosStep,
	questionnaire: QuestionnaireStep,
};

function groupErrors(errors) {
	const grouped = {};

	for (const error of errors || []) {
		const step = error.step || 'review';

		grouped[step] = grouped[step] || {};
		grouped[step][error.field || step] = error.message;
	}

	return grouped;
}

/**
 * US-002 wizard (AC2): seven steps; Back and Next keep the data. Next and
 * Save draft store the draft (AC3); the server's answer replaces the local
 * draft so new rows get their ids. Read-only after submit (AC11).
 */
export default function Wizard({call, initial, onSubmitted}) {
	const [busy, setBusy] = useState(false);
	const [draft, setDraft] = useState(() => normalizeDraft(initial.draft));
	const [errors, setErrors] = useState({});
	const [message, setMessage] = useState(null);
	const [stepIndex, setStepIndex] = useState(0);
	const headingRef = useRef(null);

	const readOnly = initial.readOnly;
	const step = STEPS[stepIndex];

	const goTo = (index) => {
		setStepIndex(index);
		setMessage(null);

		requestAnimationFrame(() => headingRef.current?.focus());
	};

	const update = (mutate) =>
		setDraft((previous) => {
			const next = JSON.parse(JSON.stringify(previous));

			mutate(next);

			return next;
		});

	const apply = (result) => {
		if (result?.draft) {
			setDraft(normalizeDraft(result.draft));
		}
	};

	const showTop = () =>
		requestAnimationFrame(() => headingRef.current?.focus());

	/**
	 * Shows a refused call where the partner looks: field errors open the
	 * first step that has them; the message sits under the step heading,
	 * which gets focus so it scrolls into view even when Next was clicked at
	 * the bottom of a long step.
	 */
	const handleError = (error) => {
		if (error.status === 422 && error.errors?.length) {
			const grouped = groupErrors(error.errors);

			setErrors(grouped);

			const first = STEPS.findIndex((s) => grouped[s.key]);

			if (first >= 0 && first !== stepIndex) {
				setStepIndex(first);
			}

			setMessage({
				text: 'Check the highlighted fields.',
				type: 'danger',
			});
			showTop();

			return grouped;
		}

		setMessage({text: error.message, type: 'danger'});
		showTop();

		return null;
	};

	const save = async () => {
		setBusy(true);
		setMessage(null);

		try {
			apply(await call((api, session) => api.saveDraft(session, draft)));
			setErrors({});

			return true;
		}
		catch (error) {
			handleError(error);

			return false;
		}
		finally {
			setBusy(false);
		}
	};

	const onNext = async () => {
		if (readOnly || (await save())) {
			goTo(stepIndex + 1);
		}
	};

	const onSaveDraft = async () => {
		if (await save()) {
			setMessage({
				text: 'Draft saved. You can come back with the same link until it expires.',
				type: 'success',
			});
		}
	};

	const onSubmit = async () => {
		if (!(await save())) {
			return;
		}

		setBusy(true);

		try {
			const result = await call((api, session) => api.submit(session));

			onSubmitted(result);
		}
		catch (error) {
			handleError(error);
		}
		finally {
			setBusy(false);
		}
	};

	const onUpload = async (upload) => {
		apply(await call((api, session) => api.uploadDocument(session, upload)));

		setErrors((previous) => ({...previous, documents: undefined}));
	};

	const onDelete = async (documentId) => {
		try {
			apply(
				await call((api, session) => api.deleteDocument(session, documentId))
			);
		}
		catch (error) {
			handleError(error);
		}
	};

	const errorCount = Object.values(errors).reduce(
		(count, stepErrors) => count + Object.keys(stepErrors || {}).length,
		0
	);

	const stepErrors = errors[step.key] || {};
	const StepComponent = STEP_COMPONENTS[step.key];

	return (
		<div className="pr-wizard">
			{readOnly && (
				<div className="alert alert-info" role="status">
					Your application has been submitted and is being reviewed.
					You can view it but not change it.
				</div>
			)}

			<nav aria-label="Registration steps">
				<ol className="pr-steps">
					{STEPS.map((item, index) => (
						<li key={item.key}>
							<button
								aria-current={index === stepIndex ? 'step' : undefined}
								className={`btn btn-unstyled pr-step${index === stepIndex ? ' active' : ''}${errors[item.key] ? ' has-error' : ''}`}
								disabled={busy}
								onClick={() => goTo(index)}
								type="button"
							>
								<span className="pr-step-number">{index + 1}</span>
								<span className="pr-step-label">{item.label}</span>
							</button>
						</li>
					))}
				</ol>
			</nav>

			<div className="sheet sheet-lg">
				<h2 className="sheet-title" ref={headingRef} tabIndex={-1}>
					{step.label}
				</h2>

				{message && (
					<div
						className={`alert alert-${message.type}`}
						role={message.type === 'danger' ? 'alert' : 'status'}
					>
						{message.text}
					</div>
				)}

				<fieldset className="pr-plain" disabled={readOnly || busy}>
					{StepComponent && (
						<StepComponent
							categories={initial.categories || {}}
							draft={draft}
							errors={stepErrors}
							readOnly={readOnly}
							update={update}
						/>
					)}
				</fieldset>

				{step.key === 'documents' && (
					<DocumentsStep
						draft={draft}
						errors={stepErrors}
						maxUploadMb={initial.maxUploadMb || 10}
						onDelete={onDelete}
						onUpload={onUpload}
						readOnly={readOnly}
					/>
				)}

				{step.key === 'review' && (
					<ReviewStep
						categories={initial.categories || {}}
						draft={draft}
						errorCount={errorCount}
						onEdit={(key) => goTo(STEPS.findIndex((s) => s.key === key))}
						readOnly={readOnly}
					/>
				)}

				<div className="pr-footer sheet-footer">
					<button
						className="btn btn-secondary"
						disabled={busy || stepIndex === 0}
						onClick={() => goTo(stepIndex - 1)}
						type="button"
					>
						Back
					</button>

					<div className="pr-footer-end">
						{!readOnly && (
							<button
								className="btn btn-secondary"
								disabled={busy}
								onClick={onSaveDraft}
								type="button"
							>
								Save draft
							</button>
						)}

						{stepIndex < STEPS.length - 1 ? (
							<button
								className="btn btn-primary"
								disabled={busy}
								onClick={onNext}
								type="button"
							>
								Next
							</button>
						) : (
							!readOnly && (
								<button
									className="btn btn-primary"
									disabled={busy}
									onClick={onSubmit}
									type="button"
								>
									{busy ? 'Submitting…' : 'Submit application'}
								</button>
							)
						)}
					</div>
				</div>
			</div>
		</div>
	);
}
