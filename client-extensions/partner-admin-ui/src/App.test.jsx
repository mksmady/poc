import {render, screen} from '@testing-library/react';
import {describe, expect, it, vi} from 'vitest';

import App from './App';

vi.mock('./views/InvitationsView', () => ({
	default: () => <div>invitations view</div>,
}));

describe('App', () => {
	it('renders the invitations view', () => {
		render(<App view="invitations" />);

		expect(screen.getByText('invitations view')).toBeInTheDocument();
	});

	it('says when a view is not built yet', () => {
		render(<App view="review" />);

		expect(screen.getByRole('status')).toHaveTextContent(
			'The "review" view is not available yet.'
		);
	});
});
