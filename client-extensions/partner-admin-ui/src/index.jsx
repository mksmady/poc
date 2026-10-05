import {StrictMode} from 'react';
import {createRoot} from 'react-dom/client';

import App from './App';
import './style.css';

export const ELEMENT_NAME = 'partner-admin-ui';

class PartnerAdminUiElement extends HTMLElement {
	connectedCallback() {
		if (!this._root) {
			this._root = createRoot(this);
		}

		this._root.render(
			<StrictMode>
				<App view={this.getAttribute('view') || 'invitations'} />
			</StrictMode>
		);
	}

	disconnectedCallback() {
		this._root?.unmount();
		this._root = undefined;
	}
}

if (!customElements.get(ELEMENT_NAME)) {
	customElements.define(ELEMENT_NAME, PartnerAdminUiElement);
}
