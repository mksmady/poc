import {act} from '@testing-library/react';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

const open = vi.fn();

vi.mock('./api', async (importOriginal) => ({
	...(await importOriginal()),
	apiFromEnv: () => ({
		deleteDocument: vi.fn(),
		open,
		saveDraft: vi.fn(),
		submit: vi.fn(),
		uploadDocument: vi.fn(),
	}),
}));

const {resetOpenRequestsForTests} = await import('./App');
const {ELEMENT_NAME} = await import('./index');

beforeEach(() => {
	resetOpenRequestsForTests();
	window.history.replaceState(null, '', '/web/guest/partner-registration?t=tok');
	open.mockReset();
	open.mockResolvedValue({
		categories: {},
		draft: {},
		invitee: {email: 'a@b.test', firstName: 'Ana'},
		readOnly: false,
		session: {applicationId: 1, invitationId: 1, tokenHash: 'h'},
		status: 'registrationInProgress',
	});
});

afterEach(() => {
	document.body.innerHTML = '';
});

describe('<partner-registration>', () => {
	it('is registered', () => {
		expect(customElements.get(ELEMENT_NAME)).toBeDefined();
	});

	it('keeps one wizard when the portal re-attaches the element', async () => {
		const element = document.createElement(ELEMENT_NAME);
		const first = document.createElement('div');
		const second = document.createElement('div');

		document.body.append(first, second);

		await act(async () => {
			first.append(element);
			second.append(element);
		});

		await vi.waitFor(() => expect(element.textContent).toContain('Welcome, Ana'));

		expect(open).toHaveBeenCalledTimes(1);
		expect(open).toHaveBeenCalledWith('tok');
		expect(element.querySelectorAll('.partner-registration')).toHaveLength(1);
	});

	it('unmounts when removed for good', async () => {
		const element = document.createElement(ELEMENT_NAME);

		await act(async () => {
			document.body.append(element);
		});

		await vi.waitFor(() => expect(element.textContent).toContain('Welcome'));

		await act(async () => {
			element.remove();
			await new Promise((resolve) => setTimeout(resolve, 5));
		});

		expect(element.textContent).toBe('');
	});
});
