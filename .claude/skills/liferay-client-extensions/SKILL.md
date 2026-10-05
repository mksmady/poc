---
name: liferay-client-extensions
description: How the client extensions in this repo are structured, configured, built and deployed (client-extension.yaml, OAuth apps, objectAction, workflowAction, customElement, batch, Spring Boot microservice). Use when creating or changing any client extension.
---

# Client extensions in this repo

## Types used
| CE | Types in `client-extension.yaml` |
|---|---|
| partner-batch, partner-catalog-batch | `batch` + `oAuthApplicationHeadlessServer` |
| partner-service | `oAuthApplicationHeadlessServer`, `oAuthApplicationUserAgent`, `objectAction`, `workflowAction` |
| partner-registration, partner-admin-ui | `customElement` |

## partner-service (Spring Boot)
- Base it on Liferay's sample `liferay-sample-etc-spring-boot` pattern: `com.liferay.client.extension.util.spring.boot` dependency for the OAuth2 resource server and JWT checks on object/workflow action calls.
- Java 17+, Spring Boot 3. Port 58081 locally (`server.port` in `application-default.properties`).
- Packages: `api` (REST controllers), `actions` (object and workflow action handlers), `domain` (StatusService, TokenService, ActivationService), `liferay` (LiferayClient), `job` (DailyJob).
- Public guest endpoints live under `/public/**` and are the only routes without Liferay JWT auth; they use the registration session token instead.
- Read Liferay URL and OAuth credentials from the CE's generated config (`/etc/liferay/lxc/...` in Liferay Cloud, `dxp-metadata` / `ext-init-metadata` locally) through the util library; never hard-code.

## Custom elements (React)
- Created from Liferay's React custom element template (Vite or CRA build). Output in `build/static` with hashed `main.*.js`/`main.*.css`, `useESM: true`.
- Register the HTML element name in `client-extension.yaml` and in `customElements.define`.
- Signed-in calls to Liferay use `Liferay.authToken` / `Liferay.Util.fetch` (session) for `/o/c/...`. Calls to `partner-service` use the user-agent OAuth app (`Liferay.OAuth2Client.FromUserAgentApplication('<erc>')`).
- `partner-registration` is used by guests: it calls only `partner-service /public/**`.
- UI copy and layout follow the canvas mockups (Partner Portal Screens). Use Clay components where they exist.

## Build and deploy
`./gradlew :client-extensions:<name>:deploy` builds a `.zip` into `bundles/osgi/client-extensions`. For Liferay Cloud, the same zips deploy through the CI pipeline.

## When adding a CE
1. New folder under `client-extensions/` with `client-extension.yaml`.
2. Add it to the table in `docs/architecture.md`.
3. Least-privilege OAuth scopes; list why in a YAML comment.
