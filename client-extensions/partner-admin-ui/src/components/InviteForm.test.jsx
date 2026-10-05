import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {ApiError} from '../api';
import InviteForm, {validate} from './InviteForm';

vi.mock('../api', async (importOriginal) => ({
	...(await importOriginal()),
	createInvitation: vi.fn(),
}));

const api = await import('../api');

beforeEach(() => {
	vi.clearAllMocks();
});

describe('validate', () => {
	it('requires every field (AC1)', () => {
		expect(validate({email: ' ', firstName: '', lastName: ''})).toEqual({
			email: 'Email is required.',
			firstName: 'First name is required.',
			lastName: 'Last name is required.',
		});
	});

	it('checks the email format (AC1)', () => {
		expect(
			validate({email: 'ana@acme', firstName: 'A', lastName: 'B'})
		).toEqual({email: 'Enter a valid email address.'});
		expect(
			validate({email: 'ana@acme.test', firstName: 'A', lastName: 'B'})
		).toEqual({});
	});
});

describe('InviteForm', () => {
	it('shows field errors and does not call the service', async () => {
		const user = userEvent.setup();

		render(<InviteForm onCancel={vi.fn()} onCreated={vi.fn()} />);

		await user.type(screen.getByLabelText(/Email/), 'not-an-email');
		await user.click(screen.getByRole('button', {name: 'Send invitation'}));

		expect(screen.getByText('First name is required.')).toBeInTheDocument();
		expect(screen.getByText('Last name is required.')).toBeInTheDocument();
		expect(screen.getByText('Enter a valid email address.')).toBeInTheDocument();
		expect(screen.getByLabelText(/Email/)).toHaveAttribute('aria-invalid', 'true');
		expect(api.createInvitation).not.toHaveBeenCalled();
	});

	it('shows the duplicate refusal from the service (AC2)', async () => {
		const user = userEvent.setup();
		const onCreated = vi.fn();

		api.createInvitation.mockRejectedValue(
			new ApiError('An active invitation already exists for this email', 409)
		);

		render(<InviteForm onCancel={vi.fn()} onCreated={onCreated} />);

		await user.type(screen.getByLabelText(/First name/), 'Ana');
		await user.type(screen.getByLabelText(/Last name/), 'Lopez');
		await user.type(screen.getByLabelText(/Email/), 'ana@acme.test');
		await user.click(screen.getByRole('button', {name: 'Send invitation'}));

		expect(await screen.findByRole('alert')).toHaveTextContent(
			'An active invitation already exists for this email'
		);
		expect(onCreated).not.toHaveBeenCalled();
		expect(screen.getByLabelText(/Email/)).toHaveValue('ana@acme.test');
	});

	it('cancels', async () => {
		const user = userEvent.setup();
		const onCancel = vi.fn();

		render(<InviteForm onCancel={onCancel} onCreated={vi.fn()} />);

		await user.click(screen.getByRole('button', {name: 'Cancel'}));

		expect(onCancel).toHaveBeenCalled();
	});
});
