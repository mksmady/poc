# partner-admin-ui

React 18 custom element (`<partner-admin-ui>`), built with Vite into `build/static`
(`js/main.*.js`, `css/main.*.css`). Markup uses the Clay CSS classes the portal theme already
loads. All data comes from `partner-service` through the user-agent OAuth app
`partner-service-oauth-user-agent` (`src/api.js`).

One element, several views chosen by the `view` property on the widget instance:
`invitations` (US-001, built), `review` (US-003), `legal` (US-004), `partners` (US-006),
`partner-detail` (US-005, US-006).

```bash
npm install
npm run lint
npm test          # Vitest + Testing Library, api mocked
npm run build
```

Deploy with `./gradlew :client-extensions:partner-admin-ui:deploy`, then add the **Partner Admin**
widget (Client Extensions category) to a page that only staff can see. Set the widget property
`view` (default `invitations`).
