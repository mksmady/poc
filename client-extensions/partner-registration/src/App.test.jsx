import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {ApiError} from './api';
import App, {INVALID_LINK, resetOpenRequestsForTests, tokenFromUrl} from './App';

const OPENED = {
	categories: {networking: 'Networking'},
	draft: {},
	invitee: {email: 'ana@acme.test', firstName: 'Ana', lastName: 'Lopez'},
	maxUploadMb: 10,
	readOnly: false,
	session: {applicationId: 20, invitationId: 10, tokenHash: 'h'},
	status: 'registrationInProgress',
};

function fakeApi(overrides = {}) {
	return {
		deleteDocument: vi.fn(),
		open: vi.fn(() => Promise.resolve(OPENED)),
		saveDraft: vi.fn((session, draft) => Promise.resolve({...OPENED, draft, session})),
		submit: vi.fn(() =>
			Promise.resolve({...OPENED, readOnly: true, status: 'approvalPending'})
		),
		uploadDocument: vi.fn(),
		...overrides,
	};
}

beforeEach(() => {
	resetOpenRequestsForTests();
});

describe('tokenFromUrl', () => {
	it('reads the t parameter', () => {
		expect(tokenFromUrl('https://p.test/web/guest/partner-registration?t=abc_-1')).toBe('abc_-1');
		expect(tokenFromUrl('https://p.test/web/guest/partner-registration')).toBeNull();
	});
});

describe('App (AC1)', () => {
	it('shows the invalid-link message without a token', () => {
		const api = fakeApi();

		render(<App api={api} token={null} />);

		expect(screen.getByRole('alert')).toHaveTextContent(INVALID_LINK);
		expect(api.open).not.toHaveBeenCalled();
	});

	it('shows the invalid-link message for a refused token', async () => {
		const api = fakeApi({open: vi.fn(() => Promise.reject(new ApiError('x', 404)))});

		render(<App api={api} token="bad" />);

		expect(await screen.findByRole('alert')).toHaveTextContent(INVALID_LINK);
		expect(api.open).toHaveBeenCalledWith('bad');
	});

	it('opens the wizard for a valid token', async () => {
		render(<App api={fakeApi()} token="good" />);

		expect(await screen.findByText(/Welcome, Ana/)).toBeInTheDocument();
		expect(screen.getByRole('heading', {level: 2, name: 'Questionnaire'})).toBeInTheDocument();
	});

	it('opens once when the wizard mounts twice', async () => {
		const api = fakeApi();

		render(
			<>
				<App api={api} token="good" />
				<App api={api} token="good" />
			</>
		);

		expect(await screen.findAllByText(/Welcome, Ana/)).toHaveLength(2);
		expect(api.open).toHaveBeenCalledTimes(1);
	});

	it('passes the session from open to the next call', async () => {
		const user = userEvent.setup();
		const api = fakeApi();

		render(<App api={api} token="good" />);

		await screen.findByText(/Welcome, Ana/);
		await user.click(screen.getByRole('button', {name: 'Save draft'}));

		expect(await screen.findByText(/Draft saved/)).toBeInTheDocument();
		expect(api.saveDraft.mock.calls[0][0]).toEqual(OPENED.session);
	});

	it('shows the invalid-link message when the link stops working mid-way', async () => {
		const user = userEvent.setup();
		const api = fakeApi({saveDraft: vi.fn(() => Promise.reject(new ApiError(INVALID_LINK, 404)))});

		render(<App api={api} token="good" />);

		await screen.findByText(/Welcome, Ana/);
		await user.click(screen.getByRole('button', {name: 'Save draft'}));

		expect(await screen.findByRole('alert')).toHaveTextContent(INVALID_LINK);
		expect(screen.queryByText(/Welcome, Ana/)).not.toBeInTheDocument();
	});

	it('shows the confirmation page after submit and can show the application read-only (AC10, AC11)', async () => {
		const user = userEvent.setup();

		render(<App api={fakeApi()} token="good" />);

		await screen.findByText(/Welcome, Ana/);
		await user.click(screen.getByRole('button', {name: /Review/}));
		await user.click(screen.getByRole('button', {name: 'Submit application'}));

		expect(await screen.findByText(/your application has been submitted/)).toBeInTheDocument();
		expect(screen.getByText(/ana@acme.test/)).toBeInTheDocument();

		await user.click(screen.getByRole('button', {name: 'View your application'}));

		expect(screen.getByText(/has been submitted and is being reviewed/)).toBeInTheDocument();
		expect(screen.queryByRole('button', {name: 'Save draft'})).not.toBeInTheDocument();
	});
});
