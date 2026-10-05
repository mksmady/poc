import {render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {ApiError} from './api';
import Wizard from './Wizard';

const CATEGORIES = {networking: 'Networking', security: 'Security'};

function opened(draft = {}, extra = {}) {
	return {
		categories: CATEGORIES,
		draft,
		maxUploadMb: 10,
		readOnly: false,
		session: 's1',
		status: 'registrationInProgress',
		...extra,
	};
}

let api;
let call;

beforeEach(() => {
	api = {
		deleteDocument: vi.fn(),
		saveDraft: vi.fn((session, draft) =>
			Promise.resolve({draft, session: 's2'})
		),
		submit: vi.fn(),
		uploadDocument: vi.fn(),
	};

	call = vi.fn((request) => request(api, 's1'));
});

function renderWizard(initial = opened(), onSubmitted = vi.fn()) {
	render(<Wizard call={call} initial={initial} onSubmitted={onSubmitted} />);

	return onSubmitted;
}

async function next(user) {
	await user.click(screen.getByRole('button', {name: 'Next'}));
}

describe('Wizard', () => {
	it('shows the seven steps and starts with the questionnaire (AC2)', () => {
		renderWizard();

		const steps = within(
			screen.getByRole('navigation', {name: 'Registration steps'})
		).getAllByRole('button');

		expect(steps.map((step) => step.textContent)).toEqual([
			'1Questionnaire',
			'2Company',
			'3Contacts',
			'4Compliance',
			'5POS locations',
			'6Documents',
			'7Review',
		]);
		expect(
			screen.getByRole('heading', {level: 2, name: 'Questionnaire'})
		).toBeInTheDocument();
	});

	it('saves on Next and keeps data on Back (AC2, AC3)', async () => {
		const user = userEvent.setup();

		renderWizard();

		await user.selectOptions(
			screen.getByLabelText(/What kind of business/),
			'reseller'
		);

		await next(user);

		expect(api.saveDraft).toHaveBeenCalledTimes(1);
		expect(api.saveDraft.mock.calls[0][1].questionnaire).toEqual({
			businessType: 'reseller',
		});

		await screen.findByRole('heading', {level: 2, name: 'Company'});

		await user.type(screen.getByLabelText(/Legal company name/), 'Acme GmbH');
		await user.click(screen.getByRole('button', {name: 'Back'}));

		expect(screen.getByLabelText(/What kind of business/)).toHaveValue(
			'reseller'
		);

		await next(user);

		expect(
			await screen.findByLabelText(/Legal company name/)
		).toHaveValue('Acme GmbH');
	});

	it('opens the step with errors when Next is refused', async () => {
		const user = userEvent.setup();

		api.saveDraft.mockRejectedValue(
			new ApiError('Some information is missing or not valid', 422, [
				{
					field: 'contacts[1].fullName',
					message: "Enter the contact's full name, or remove the contact.",
					step: 'contacts',
				},
			])
		);

		renderWizard(
			opened({
				contacts: [
					{email: 'a@b.test', fullName: 'Ana', id: 1, role: 'primary'},
					{phone: '+49 1', role: 'finance'},
				],
			})
		);

		await next(user);

		expect(
			await screen.findByRole('heading', {level: 2, name: 'Contacts'})
		).toBeInTheDocument();
		expect(screen.getByRole('alert')).toHaveTextContent(
			'Check the highlighted fields.'
		);
		expect(screen.getAllByLabelText(/Full name/)[1]).toHaveAttribute(
			'aria-invalid',
			'true'
		);
		expect(screen.getAllByLabelText(/Full name/)[0]).not.toHaveAttribute(
			'aria-invalid'
		);
	});

	it('shows a failed save under the step heading', async () => {
		const user = userEvent.setup();

		api.saveDraft.mockRejectedValue(
			new ApiError('The portal could not complete the request', 502)
		);

		renderWizard();

		await next(user);

		expect(await screen.findByRole('alert')).toHaveTextContent(
			'The portal could not complete the request'
		);
		expect(
			screen.getByRole('heading', {level: 2, name: 'Questionnaire'})
		).toBeInTheDocument();
	});

	it('restores a saved draft (AC3)', async () => {
		const user = userEvent.setup();

		renderWizard(
			opened({
				company: {companyName: 'Acme GmbH', country: 'DE'},
				contacts: [
					{email: 'ana@acme.test', fullName: 'Ana Lopez', id: 7, role: 'primary'},
				],
			})
		);

		await user.click(screen.getByRole('button', {name: /Company/}));

		expect(screen.getByLabelText(/Legal company name/)).toHaveValue(
			'Acme GmbH'
		);
		expect(screen.getByLabelText(/^Country/)).toHaveValue('DE');

		await user.click(screen.getByRole('button', {name: /Contacts/}));

		expect(screen.getByLabelText(/Full name/)).toHaveValue('Ana Lopez');
	});

	it('shows a confirmation when the draft is saved', async () => {
		const user = userEvent.setup();

		renderWizard();

		await user.click(screen.getByRole('button', {name: 'Save draft'}));

		expect(await screen.findByRole('status')).toHaveTextContent(
			'Draft saved'
		);
	});

	it('adds and removes contacts (AC5)', async () => {
		const user = userEvent.setup();

		renderWizard();

		await user.click(screen.getByRole('button', {name: /Contacts/}));

		expect(screen.getAllByLabelText(/Full name/)).toHaveLength(1);
		expect(
			screen.queryByRole('button', {name: /Remove contact/})
		).not.toBeInTheDocument();

		await user.click(screen.getByRole('button', {name: 'Add contact'}));

		const names = screen.getAllByLabelText(/Full name/);

		expect(names).toHaveLength(2);

		await user.type(names[1], 'Second');
		await user.click(screen.getByRole('button', {name: 'Remove contact 1'}));

		expect(screen.getAllByLabelText(/Full name/)).toHaveLength(1);
		expect(screen.getByLabelText(/Full name/)).toHaveValue('Second');
	});

	it('locks the company details in existing-company mode (AC9)', async () => {
		const user = userEvent.setup();

		renderWizard(
			opened({
				company: {companyName: 'Acme Holding', vatId: 'DE1'},
				existingCompany: true,
			})
		);

		await user.click(screen.getByRole('button', {name: /Company/}));

		expect(screen.getByText(/already one of our partners/)).toBeInTheDocument();
		expect(screen.getByLabelText(/Legal company name/)).toBeDisabled();
		expect(screen.getByLabelText(/VAT ID/)).toBeEnabled();
	});

	it('shows server validation errors next to the fields and opens the first step with errors (AC10)', async () => {
		const user = userEvent.setup();

		api.submit.mockRejectedValue(
			new ApiError('Some information is missing or not valid', 422, [
				{field: 'companyName', message: 'Enter the legal company name.', step: 'company'},
				{field: 'vatCertificate', message: 'Upload the VAT certificate.', step: 'documents'},
			])
		);

		renderWizard();

		await user.click(screen.getByRole('button', {name: /Review/}));
		await user.click(
			screen.getByRole('button', {name: 'Submit application'})
		);

		expect(
			await screen.findByRole('heading', {level: 2, name: 'Company'})
		).toBeInTheDocument();
		expect(screen.getByLabelText(/Legal company name/)).toHaveAttribute(
			'aria-invalid',
			'true'
		);
		expect(
			screen.getByText('Enter the legal company name.')
		).toBeInTheDocument();

		await user.click(screen.getByRole('button', {name: /Review/}));

		expect(screen.getByRole('alert')).toHaveTextContent(
			'2 items need your attention'
		);
	});

	it('submits after saving and reports the result (AC10)', async () => {
		const user = userEvent.setup();
		const result = {readOnly: true, session: 's3', status: 'approvalPending'};

		api.submit.mockResolvedValue(result);

		const onSubmitted = renderWizard();

		await user.click(screen.getByRole('button', {name: /Review/}));
		await user.click(
			screen.getByRole('button', {name: 'Submit application'})
		);

		await waitFor(() => expect(onSubmitted).toHaveBeenCalledWith(result));
		expect(api.saveDraft).toHaveBeenCalledBefore(api.submit);
	});

	it('is read-only after submit (AC11)', async () => {
		const user = userEvent.setup();

		renderWizard(
			opened(
				{company: {companyName: 'Acme GmbH'}},
				{readOnly: true, status: 'approvalPending'}
			)
		);

		expect(screen.getByText(/has been submitted and is being reviewed/)).toBeInTheDocument();
		expect(
			screen.queryByRole('button', {name: 'Save draft'})
		).not.toBeInTheDocument();

		await user.click(screen.getByRole('button', {name: /Company/}));

		expect(screen.getByLabelText(/Legal company name/)).toBeDisabled();

		await user.click(screen.getByRole('button', {name: /Review/}));

		expect(
			screen.queryByRole('button', {name: 'Submit application'})
		).not.toBeInTheDocument();
		expect(
			screen.queryByRole('button', {name: /Edit/})
		).not.toBeInTheDocument();
		expect(api.saveDraft).not.toHaveBeenCalled();
	});

	it('picks POS categories (AC7)', async () => {
		const user = userEvent.setup();

		renderWizard();

		await user.click(screen.getByRole('button', {name: /POS locations/}));
		await user.click(screen.getByLabelText('Security'));
		await user.click(screen.getByRole('button', {name: 'Save draft'}));

		expect(api.saveDraft.mock.calls[0][1].posLocations[0].categories).toEqual([
			'security',
		]);
	});
});
