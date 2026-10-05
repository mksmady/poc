import {StrictMode} from 'react';
import {createRoot} from 'react-dom/client';

import App, {tokenFromUrl} from './App';
import './style.css';

export const ELEMENT_NAME = 'partner-registration';

/**
 * Liferay can detach and re-attach the element while it builds the page.
 * The React root survives a re-attach in the same task, so the wizard is not
 * mounted twice (which used to open the registration session twice).
 */
export class PartnerRegistrationElement extends HTMLElement {
	connectedCallback() {
		if (this._unmountTimer) {
			clearTimeout(this._unmountTimer);
			this._unmountTimer = undefined;
		}

		if (this._root) {
			return;
		}

		this._root = createRoot(this);

		this._root.render(
			<StrictMode>
				<div className="partner-registration">
					<App
						token={tokenFromUrl()}
					/>
				</div>
			</StrictMode>
		);
	}

	disconnectedCallback() {
		this._unmountTimer = setTimeout(() => {
			this._unmountTimer = undefined;
			this._root?.unmount();
			this._root = undefined;
		});
	}
}

if (!customElements.get(ELEMENT_NAME)) {
	customElements.define(ELEMENT_NAME, PartnerRegistrationElement);
}
