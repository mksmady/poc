import {render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {ApiError} from '../api';
import InvitationsView from './InvitationsView';

vi.mock('../api', async (importOriginal) => ({
	...(await importOriginal()),
	createInvitation: vi.fn(),
	listInvitations: vi.fn(),
	resendInvitation: vi.fn(),
}));

const api = await import('../api');

const ROWS = [
	{
		companyName: 'Acme GmbH',
		email: 'ana@acme.test',
		expiresAt: '2026-11-03T10:00:00Z',
		firstName: 'Ana',
		invitationId: 11,
		lastName: 'Lopez',
		reminderCount: 2,
		resendable: true,
		sentAt: '2026-10-04T10:00:00Z',
		status: 'invitationSent',
	},
	{
		email: 'bo@beta.test',
		expiresAt: '2026-09-01T10:00:00Z',
		firstName: 'Bo',
		invitationId: 12,
		lastName: 'Berg',
		reminderCount: 0,
		resendable: false,
		sentAt: '2026-08-02T10:00:00Z',
		status: 'submitted',
	},
];

beforeEach(() => {
	vi.clearAllMocks();
	api.listInvitations.mockResolvedValue(ROWS);
});

describe('InvitationsView', () => {
	it('lists invitations with status, dates and reminders (AC7)', async () => {
		render(<InvitationsView />);

		const table = await screen.findByRole('table');

		expect(api.listInvitations).toHaveBeenCalledWith({search: '', tab: 'all'});

		const rows = within(table).getAllByRole('row');

		expect(rows).toHaveLength(3);
		expect(within(rows[1]).getByText('Ana Lopez')).toBeInTheDocument();
		expect(within(rows[1]).getByText('Acme GmbH')).toBeInTheDocument();
		expect(within(rows[1]).getByText('Invitation sent')).toBeInTheDocument();
		expect(within(rows[1]).getByText('2')).toBeInTheDocument();
		expect(within(rows[2]).getByText('Submitted')).toBeInTheDocument();
	});

	it('only offers resend on resendable rows (AC6)', async () => {
		render(<InvitationsView />);

		await screen.findByRole('table');

		expect(
			screen.getByRole('button', {name: 'Resend invitation to ana@acme.test'})
		).toBeInTheDocument();
		expect(
			screen.queryByRole('button', {name: 'Resend invitation to bo@beta.test'})
		).not.toBeInTheDocument();
	});

	it('loads the selected tab and search (AC7)', async () => {
		const user = userEvent.setup();

		render(<InvitationsView />);

		await screen.findByRole('table');

		await user.click(screen.getByRole('tab', {name: 'Expiring in 7 days'}));

		await waitFor(() =>
			expect(api.listInvitations).toHaveBeenLastCalledWith({
				search: '',
				tab: 'expiring',
			})
		);

		expect(
			screen.getByRole('tab', {name: 'Expiring in 7 days'})
		).toHaveAttribute('aria-selected', 'true');

		await user.type(screen.getByRole('searchbox'), 'acme');
		await user.click(screen.getByRole('button', {name: 'Search'}));

		await waitFor(() =>
			expect(api.listInvitations).toHaveBeenLastCalledWith({
				search: 'acme',
				tab: 'expiring',
			})
		);
	});

	it('shows an empty state', async () => {
		api.listInvitations.mockResolvedValue([]);

		render(<InvitationsView />);

		expect(await screen.findByText('No invitations match.')).toBeInTheDocument();
	});

	it('shows the service error when loading fails', async () => {
		api.listInvitations.mockRejectedValue(
			new ApiError('Not allowed to view invitations', 403)
		);

		render(<InvitationsView />);

		expect(await screen.findByRole('alert')).toHaveTextContent(
			'Not allowed to view invitations'
		);
	});

	it('creates an invitation and shows watchlist warnings (AC1, AC2)', async () => {
		const user = userEvent.setup();

		api.createInvitation.mockResolvedValue({
			applicationId: 21,
			emailSent: true,
			expiresAt: '2026-11-03T10:00:00Z',
			invitationId: 13,
			warnings: ['The domain acme.test is on the terminated watchlist'],
		});

		render(<InvitationsView />);

		await screen.findByRole('table');

		await user.click(screen.getByRole('button', {name: 'Invite partner'}));
		await user.type(screen.getByLabelText(/First name/), 'Cy');
		await user.type(screen.getByLabelText(/Last name/), 'Diaz');
		await user.type(screen.getByLabelText(/Email/), ' cy@acme.test ');
		await user.click(screen.getByRole('button', {name: 'Send invitation'}));

		expect(api.createInvitation).toHaveBeenCalledWith({
			email: 'cy@acme.test',
			firstName: 'Cy',
			lastName: 'Diaz',
		});

		expect(
			await screen.findByText(/Invitation sent to cy@acme.test/)
		).toBeInTheDocument();
		expect(
			screen.getByText('The domain acme.test is on the terminated watchlist')
		).toBeInTheDocument();
		expect(
			screen.queryByRole('button', {name: 'Send invitation'})
		).not.toBeInTheDocument();
		expect(api.listInvitations).toHaveBeenCalledTimes(2);
	});

	it('resends after confirmation and reloads (AC6)', async () => {
		const user = userEvent.setup();

		vi.spyOn(window, 'confirm').mockReturnValue(true);

		api.resendInvitation.mockResolvedValue({
			emailSent: true,
			expiresAt: '2026-11-04T10:00:00Z',
			invitationId: 11,
			warnings: [],
		});

		render(<InvitationsView />);

		await screen.findByRole('table');

		await user.click(
			screen.getByRole('button', {name: 'Resend invitation to ana@acme.test'})
		);

		expect(window.confirm).toHaveBeenCalled();
		expect(api.resendInvitation).toHaveBeenCalledWith(11);
		expect(
			await screen.findByText(/Invitation resent to ana@acme.test/)
		).toBeInTheDocument();
		expect(api.listInvitations).toHaveBeenCalledTimes(2);
	});

	it('does not resend when the confirmation is cancelled', async () => {
		const user = userEvent.setup();

		vi.spyOn(window, 'confirm').mockReturnValue(false);

		render(<InvitationsView />);

		await screen.findByRole('table');

		await user.click(
			screen.getByRole('button', {name: 'Resend invitation to ana@acme.test'})
		);

		expect(api.resendInvitation).not.toHaveBeenCalled();
	});

	it('shows the service error when resend is refused', async () => {
		const user = userEvent.setup();

		vi.spyOn(window, 'confirm').mockReturnValue(true);

		api.resendInvitation.mockRejectedValue(
			new ApiError(
				'Only the inviting account manager or an admin can resend this invitation',
				403
			)
		);

		render(<InvitationsView />);

		await screen.findByRole('table');

		await user.click(
			screen.getByRole('button', {name: 'Resend invitation to ana@acme.test'})
		);

		expect(await screen.findByRole('alert')).toHaveTextContent(
			'Only the inviting account manager'
		);
	});
});
