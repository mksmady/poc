import InvitationsView from './views/InvitationsView';

// One element, one view per widget instance (the `view` property in
// client-extension.yaml). Other views arrive with their stories.

const VIEWS = {
	invitations: InvitationsView,
};

export default function App({view}) {
	const View = VIEWS[view];

	if (!View) {
		return (
			<div className="alert alert-info partner-admin-ui" role="status">
				The &quot;{view}&quot; view is not available yet.
			</div>
		);
	}

	return (
		<div className="partner-admin-ui">
			<View />
		</div>
	);
}
