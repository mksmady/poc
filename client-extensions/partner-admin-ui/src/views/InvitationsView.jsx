import {useCallback, useEffect, useState} from 'react';

import {listInvitations, resendInvitation} from '../api';
import InviteForm from '../components/InviteForm';
import {formatDate, statusDisplay, statusLabel} from '../format';

// Tab keys match InvitationService.TAB_* in partner-service.

export const TABS = [
	{key: 'all', label: 'All'},
	{key: 'invitationSent', label: 'Invitation sent'},
	{key: 'inProgress', label: 'In progress'},
	{key: 'expiring', label: 'Expiring in 7 days'},
	{key: 'expired', label: 'Expired'},
];

function fullName(row) {
	return [row.firstName, row.lastName].filter(Boolean).join(' ');
}

/**
 * US-001 invitations dashboard (AC7) with create (AC1, AC2) and resend
 * (AC6). Who sees what (AM own, admin all) is decided by partner-service.
 */
export default function InvitationsView() {
	const [error, setError] = useState(null);
	const [loading, setLoading] = useState(true);
	const [messages, setMessages] = useState([]);
	const [resendingId, setResendingId] = useState(null);
	const [rows, setRows] = useState([]);
	const [search, setSearch] = useState('');
	const [searchInput, setSearchInput] = useState('');
	const [showForm, setShowForm] = useState(false);
	const [tab, setTab] = useState('all');

	const load = useCallback(async () => {
		setLoading(true);
		setError(null);

		try {
			setRows(await listInvitations({search, tab}));
		}
		catch (loadError) {
			setError(loadError.message);
			setRows([]);
		}
		finally {
			setLoading(false);
		}
	}, [search, tab]);

	useEffect(() => {
		load();
	}, [load]);

	const onCreated = (result, invitee) => {
		const next = [
			result.emailSent
				? {
						text: `Invitation sent to ${invitee.email}. The link expires on ${formatDate(result.expiresAt)}.`,
						type: 'success',
					}
				: {
						text: `Invitation created for ${invitee.email}, but the email could not be sent. Use Resend to try again.`,
						type: 'warning',
					},
		];

		for (const warning of result.warnings || []) {
			next.push({text: warning, type: 'warning'});
		}

		setMessages(next);
		setShowForm(false);
		load();
	};

	const onResend = async (row) => {
		if (
			!window.confirm(
				`Resend the invitation to ${row.email}? The previous link will stop working.`
			)
		) {
			return;
		}

		setResendingId(row.invitationId);

		try {
			const result = await resendInvitation(row.invitationId);

			setMessages([
				result.emailSent
					? {
							text: `Invitation resent to ${row.email}. The new link expires on ${formatDate(result.expiresAt)}.`,
							type: 'success',
						}
					: {
							text: `A new link was created for ${row.email}, but the email could not be sent. Try again.`,
							type: 'warning',
						},
			]);

			load();
		}
		catch (resendError) {
			setMessages([{text: resendError.message, type: 'danger'}]);
		}
		finally {
			setResendingId(null);
		}
	};

	const onSearch = (event) => {
		event.preventDefault();

		setSearch(searchInput);
	};

	let content;

	if (loading) {
		content = (
			<div className="pa-empty" role="status">
				<span aria-hidden="true" className="loading-animation" />

				<span className="sr-only">Loading invitations</span>
			</div>
		);
	}
	else if (error) {
		content = (
			<div className="alert alert-danger" role="alert">
				{error}
			</div>
		);
	}
	else if (!rows.length) {
		content = (
			<div className="pa-empty text-secondary">No invitations match.</div>
		);
	}
	else {
		content = (
			<div className="table-responsive">
				<table className="table table-autofit table-hover table-list">
					<thead>
						<tr>
							<th>Invitee</th>
							<th>Email</th>
							<th>Status</th>
							<th className="pa-nowrap">Sent</th>
							<th className="pa-nowrap">Expires</th>
							<th>Reminders</th>
							<th>
								<span className="sr-only">Actions</span>
							</th>
						</tr>
					</thead>

					<tbody>
						{rows.map((row) => (
							<tr key={row.invitationId}>
								<td className="table-cell-expand">
									<div className="table-list-title">
										{fullName(row)}
									</div>

									{row.companyName && (
										<div className="small text-secondary">
											{row.companyName}
										</div>
									)}
								</td>
								<td>{row.email}</td>
								<td>
									<span
										className={`label label-${statusDisplay(row.status)}`}
									>
										<span className="label-item label-item-expand">
											{statusLabel(row.status)}
										</span>
									</span>
								</td>
								<td className="pa-nowrap">
									{formatDate(row.sentAt)}
								</td>
								<td className="pa-nowrap">
									{formatDate(row.expiresAt)}
								</td>
								<td>{row.reminderCount ?? 0}</td>
								<td>
									{row.resendable && (
										<button
											aria-label={`Resend invitation to ${row.email}`}
											className="btn btn-secondary btn-sm"
											disabled={resendingId === row.invitationId}
											onClick={() => onResend(row)}
											type="button"
										>
											{resendingId === row.invitationId
												? 'Resending…'
												: 'Resend'}
										</button>
									)}
								</td>
							</tr>
						))}
					</tbody>
				</table>
			</div>
		);
	}

	return (
		<section aria-labelledby="pa-invitations-title">
			<div className="pa-toolbar">
				<h1 className="h2 mb-0" id="pa-invitations-title">
					Invitations
				</h1>

				{!showForm && (
					<button
						className="btn btn-primary"
						onClick={() => {
							setMessages([]);
							setShowForm(true);
						}}
						type="button"
					>
						Invite partner
					</button>
				)}
			</div>

			{messages.map((message, index) => (
				<div
					className={`alert alert-${message.type} alert-dismissible`}
					key={index}
					role={message.type === 'danger' ? 'alert' : 'status'}
				>
					{message.text}

					<button
						aria-label="Close"
						className="close"
						onClick={() =>
							setMessages((previous) =>
								previous.filter((_, i) => i !== index)
							)
						}
						type="button"
					>
						×
					</button>
				</div>
			))}

			{showForm && (
				<InviteForm
					onCancel={() => setShowForm(false)}
					onCreated={onCreated}
				/>
			)}

			<nav aria-label="Invitation status" className="pa-tabs">
				<ul className="nav nav-underline" role="tablist">
					{TABS.map((item) => (
						<li className="nav-item" key={item.key} role="presentation">
							<button
								aria-selected={tab === item.key}
								className={`btn btn-unstyled nav-link${tab === item.key ? ' active' : ''}`}
								onClick={() => setTab(item.key)}
								role="tab"
								type="button"
							>
								{item.label}
							</button>
						</li>
					))}
				</ul>
			</nav>

			<form className="mb-3 pa-search" onSubmit={onSearch} role="search">
				<label className="sr-only" htmlFor="pa-invitations-search">
					Search by name, email or company
				</label>

				<input
					className="form-control"
					id="pa-invitations-search"
					onChange={(event) => setSearchInput(event.target.value)}
					placeholder="Search by name, email or company"
					type="search"
					value={searchInput}
				/>

				<button className="btn btn-secondary" type="submit">
					Search
				</button>
			</form>

			{content}
		</section>
	);
}
