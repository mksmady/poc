import {useCallback, useEffect, useMemo, useRef, useState} from 'react';

import {INVALID_LINK, apiFromEnv} from './api';
import Wizard from './Wizard';

export {INVALID_LINK};

// One open per link and page load, however often the wizard mounts (React
// StrictMode in development, or the element re-attached by the portal).

const openRequests = new Map();

export function openOnce(api, token) {
	const key = token;

	if (!openRequests.has(key)) {
		openRequests.set(
			key,
			api.open(token).catch((error) => {
				openRequests.delete(key);

				throw error;
			})
		);
	}

	return openRequests.get(key);
}

export function resetOpenRequestsForTests() {
	openRequests.clear();
}

export function tokenFromUrl(href = window.location.href) {
	try {
		return new URL(href).searchParams.get('t');
	}
	catch {
		return null;
	}
}

/**
 * Opens the registration session from the link token (AC1) and hosts the
 * wizard. The portal calls go through the Object API as the registration
 * user (api.js); tests pass their own api.
 */
export default function App({api: givenApi, token}) {
	const api = useMemo(() => givenApi || apiFromEnv(), [givenApi]);
	const [opened, setOpened] = useState(null);
	const [problem, setProblem] = useState(null);
	const [submitted, setSubmitted] = useState(null);
	const [viewSubmitted, setViewSubmitted] = useState(false);
	const sessionRef = useRef(null);

	useEffect(() => {
		let cancelled = false;

		if (!token) {
			setProblem(INVALID_LINK);

			return undefined;
		}

		openOnce(api, token).then(
			(result) => {
				if (!cancelled) {
					sessionRef.current = result.session;
					setOpened(result);
				}
			},
			(error) => {
				if (!cancelled) {
					setProblem(
						error.status === 404 ? INVALID_LINK : error.message
					);
				}
			}
		);

		return () => {
			cancelled = true;
		};
	}, [api, token]);

	const call = useCallback(
		async (request) => {
			let result;

			try {
				result = await request(api, sessionRef.current);
			}
			catch (error) {
				if (error.status !== 401) {
					if (error.status === 404) {
						setProblem(INVALID_LINK);
					}

					throw error;
				}

				const reopened = await api.open(token);

				sessionRef.current = reopened.session;
				result = await request(api, sessionRef.current);
			}

			if (result?.session) {
				sessionRef.current = result.session;
			}

			return result;
		},
		[api, token]
	);

	if (problem) {
		return (
			<div className="alert alert-danger" role="alert">
				{problem}
			</div>
		);
	}

	if (!opened) {
		return (
			<div className="pr-loading" role="status">
				<span aria-hidden="true" className="loading-animation" />
				<span className="sr-only">Opening your registration</span>
			</div>
		);
	}

	if (submitted && !viewSubmitted) {
		return (
			<div className="sheet sheet-lg" role="status">
				<h2 className="sheet-title">Thank you, your application has been submitted</h2>

				<p>
					We sent a confirmation to {opened.invitee?.email}. Your
					account manager will review the application and contact you
					by email. You do not need a portal account yet; you will
					get one once the application is approved.
				</p>

				<button
					className="btn btn-secondary"
					onClick={() => setViewSubmitted(true)}
					type="button"
				>
					View your application
				</button>
			</div>
		);
	}

	const current = submitted ? {...opened, ...submitted} : opened;

	return (
		<>
			{opened.invitee && (
				<p className="pr-welcome">
					Welcome, {opened.invitee.firstName}. Register your company
					to apply for the partner program.
				</p>
			)}

			<Wizard
				call={call}
				initial={current}
				key={current.readOnly ? 'read-only' : 'edit'}
				onSubmitted={setSubmitted}
			/>
		</>
	);
}
