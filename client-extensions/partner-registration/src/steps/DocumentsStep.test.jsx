import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {describe, expect, it, vi} from 'vitest';

import DocumentsStep from './DocumentsStep';

function renderStep(props = {}) {
	const onUpload = vi.fn(() => Promise.resolve());
	const onDelete = vi.fn();

	render(
		<DocumentsStep
			draft={{documents: []}}
			errors={{}}
			maxUploadMb={1}
			onDelete={onDelete}
			onUpload={onUpload}
			readOnly={false}
			{...props}
		/>
	);

	return {onDelete, onUpload};
}

describe('DocumentsStep (AC8)', () => {
	it('lists the required documents still missing', () => {
		renderStep({
			draft: {
				documents: [{documentType: 'tradeRegister', fileName: 'tr.pdf', id: 1}],
			},
		});

		expect(screen.getByRole('status')).toHaveTextContent(
			'Required: VAT certificate.'
		);
	});

	it('refuses other file types and large files before uploading', async () => {
		const user = userEvent.setup({applyAccept: false});
		const {onUpload} = renderStep();

		await user.selectOptions(screen.getByLabelText('Document type'), 'tradeRegister');
		await user.upload(
			screen.getByLabelText(/^File/),
			new File(['x'], 'run.exe', {type: 'application/octet-stream'})
		);
		await user.click(screen.getByRole('button', {name: 'Upload'}));

		expect(screen.getByRole('alert')).toHaveTextContent(
			'Upload a PDF, JPG or PNG file.'
		);

		const big = new File(['x'], 'big.pdf', {type: 'application/pdf'});

		Object.defineProperty(big, 'size', {value: 1024 * 1024 + 1});

		await user.upload(screen.getByLabelText(/^File/), big);
		await user.click(screen.getByRole('button', {name: 'Upload'}));

		expect(screen.getByRole('alert')).toHaveTextContent(
			'The file is larger than 1 MB.'
		);
		expect(onUpload).not.toHaveBeenCalled();
	});

	it('uploads with type and note, and shows service errors', async () => {
		const user = userEvent.setup();
		const {onUpload} = renderStep();

		const file = new File(['%PDF-'], 'vat.pdf', {type: 'application/pdf'});

		await user.selectOptions(screen.getByLabelText('Document type'), 'vatCertificate');
		await user.upload(screen.getByLabelText(/^File/), file);
		await user.type(screen.getByLabelText('Note'), 'scan');
		await user.click(screen.getByRole('button', {name: 'Upload'}));

		expect(onUpload).toHaveBeenCalledWith({
			documentType: 'vatCertificate',
			file,
			note: 'scan',
		});

		onUpload.mockRejectedValueOnce(new Error('Upload a PDF, JPG or PNG file'));

		await user.selectOptions(screen.getByLabelText('Document type'), 'other');
		await user.upload(screen.getByLabelText(/^File/), file);
		await user.click(screen.getByRole('button', {name: 'Upload'}));

		expect(await screen.findByRole('alert')).toHaveTextContent(
			'Upload a PDF, JPG or PNG file'
		);
	});

	it('removes an uploaded document', async () => {
		const user = userEvent.setup();
		const {onDelete} = renderStep({
			draft: {documents: [{documentType: 'other', fileName: 'a.pdf', id: 9}]},
		});

		await user.click(screen.getByRole('button', {name: 'Remove a.pdf'}));

		expect(onDelete).toHaveBeenCalledWith(9);
	});

	it('has no upload form when read-only', () => {
		renderStep({readOnly: true});

		expect(
			screen.queryByRole('form', {name: 'Upload a document'})
		).not.toBeInTheDocument();
	});
});
