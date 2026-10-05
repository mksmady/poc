import {useId, useState} from 'react';

import {DOCUMENT_TYPES} from '../config';

const ACCEPTED = /\.(pdf|jpe?g|png)$/i;

const TYPE_LABELS = Object.fromEntries(
	DOCUMENT_TYPES.map(([key, label]) => [key, label])
);

/**
 * AC8. The browser checks extension and size for a quick answer; the service
 * sniffs the content and enforces the limit again.
 */
export default function DocumentsStep({
	draft,
	errors,
	maxUploadMb,
	onDelete,
	onUpload,
	readOnly,
}) {
	const [documentType, setDocumentType] = useState('');
	const [file, setFile] = useState(null);
	const [fileKey, setFileKey] = useState(0);
	const [localError, setLocalError] = useState(null);
	const [note, setNote] = useState('');
	const [uploading, setUploading] = useState(false);
	const id = useId();

	const uploadedTypes = new Set(draft.documents.map((d) => d.documentType));

	const missing = DOCUMENT_TYPES.filter(
		([key, , required]) => required && !uploadedTypes.has(key)
	);

	const onSubmit = async (event) => {
		event.preventDefault();

		setLocalError(null);

		if (!documentType) {
			setLocalError('Choose a document type.');

			return;
		}

		if (!file) {
			setLocalError('Choose a file.');

			return;
		}

		if (!ACCEPTED.test(file.name)) {
			setLocalError('Upload a PDF, JPG or PNG file.');

			return;
		}

		if (file.size > maxUploadMb * 1024 * 1024) {
			setLocalError(`The file is larger than ${maxUploadMb} MB.`);

			return;
		}

		setUploading(true);

		try {
			await onUpload({documentType, file, note});

			setDocumentType('');
			setFile(null);
			setFileKey((key) => key + 1);
			setNote('');
		}
		catch (error) {
			setLocalError(error.message);
		}
		finally {
			setUploading(false);
		}
	};

	return (
		<>
			{missing.length > 0 && (
				<div
					className={`alert ${Object.keys(errors).length ? 'alert-danger' : 'alert-info'}`}
					role={Object.keys(errors).length ? 'alert' : 'status'}
				>
					Required:{' '}
					{missing.map(([, label]) => label).join(' and ')}.
				</div>
			)}

			{draft.documents.length > 0 ? (
				<ul className="list-group mb-4">
					{draft.documents.map((document) => (
						<li
							className="list-group-item pr-document"
							key={document.id}
						>
							<div>
								<div className="list-group-title">
									{TYPE_LABELS[document.documentType] ||
										document.documentType}
								</div>

								<div className="list-group-subtext">
									{document.fileName}

									{document.note ? ` — ${document.note}` : ''}
								</div>
							</div>

							{!readOnly && (
								<button
									aria-label={`Remove ${document.fileName}`}
									className="btn btn-secondary btn-sm"
									onClick={() => onDelete(document.id)}
									type="button"
								>
									Remove
								</button>
							)}
						</li>
					))}
				</ul>
			) : (
				<p className="text-secondary">No documents uploaded yet.</p>
			)}

			{!readOnly && (
				<form
					aria-label="Upload a document"
					className="pr-upload"
					onSubmit={onSubmit}
				>
					<h3 className="h5">Upload a document</h3>

					{localError && (
						<div className="alert alert-danger" role="alert">
							{localError}
						</div>
					)}

					<div className="pr-grid">
						<div className="form-group">
							<label htmlFor={`${id}-type`}>Document type</label>

							<select
								className="form-control"
								id={`${id}-type`}
								onChange={(event) =>
									setDocumentType(event.target.value)
								}
								value={documentType}
							>
								<option value="">Choose…</option>

								{DOCUMENT_TYPES.map(([key, label]) => (
									<option key={key} value={key}>
										{label}
									</option>
								))}
							</select>
						</div>

						<div className="form-group">
							<label htmlFor={`${id}-file`}>
								File (PDF, JPG or PNG, up to {maxUploadMb} MB)
							</label>

							<input
								accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
								className="form-control"
								id={`${id}-file`}
								key={fileKey}
								onChange={(event) =>
									setFile(event.target.files?.[0] || null)
								}
								type="file"
							/>
						</div>
					</div>

					<div className="form-group">
						<label htmlFor={`${id}-note`}>Note</label>

						<input
							className="form-control"
							id={`${id}-note`}
							onChange={(event) => setNote(event.target.value)}
							value={note}
						/>
					</div>

					<button
						className="btn btn-secondary"
						disabled={uploading}
						type="submit"
					>
						{uploading ? 'Uploading…' : 'Upload'}
					</button>
				</form>
			)}
		</>
	);
}
