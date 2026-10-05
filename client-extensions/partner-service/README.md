# partner-service

Spring Boot 2.7 microservice (Java 17), based on DXP 2024.Q1's `liferay-sample-etc-spring-boot`
with `com.liferay.client.extension.util.spring.boot` for OAuth (open question 19).

## Run locally

1. Deploy the CE once so Liferay creates both OAuth apps and writes their credentials to
   `<liferay home>/routes/default/partner-service`: `./gradlew :client-extensions:partner-service:deploy`
2. `./gradlew :client-extensions:partner-service:bootRun` (port 58081). `bootRun` points
   `LIFERAY_ROUTES_*` at the bundle in `liferay.workspace.home.dir`.
3. `GET http://localhost:58081/actuator/health` → `{"status":"UP"}`.

On start, `RoleBootstrap` upserts the three regular roles and their object permissions. Set
`partner.bootstrap.enabled=false` to skip it.

## Packages (`com.partnerportal.service`)

| Package | Holds |
|---|---|
| `api` | REST controllers for the admin UI and the public registration API, error mapping |
| `actions` | object action and workflow action endpoints (Liferay JWT) |
| `domain` | `StatusService`, `ApplicationStatus`, `SettingsService`, `PartnerRepository` and the services built on them |
| `liferay` | `LiferayClient` (headless-server OAuth), `LiferayPartnerRepository`, object paths and field names |
| `job` | the daily job |
| `bootstrap` | start-up tasks (roles and permissions) |

Tests use `InMemoryPartnerRepository` and `FakeLiferayClient` (`src/test/.../support`).

Endpoints to build:

| Method | Path | Auth | Story |
|---|---|---|---|
| POST | /object/action/invitation-created | Liferay JWT | 001 |
| POST | /object/action/application-updated | Liferay JWT | 008 |
| POST | /workflow/action/review | Liferay JWT | 003, 004 |
| POST | /api/invitations/{id}/resend | user-agent OAuth | 001 |
| POST | /api/applications/{id}/contract-signed | user-agent OAuth | 004 |
| POST | /api/applications/{id}/deactivate, /reactivate, /terminate | user-agent OAuth | 004, 005 |
| POST | /public/registration/session | invitation token | 002 |
| GET/PUT | /public/registration/draft | registration session | 002 |
| POST | /public/registration/documents | registration session | 002 |
| POST | /public/registration/submit | registration session | 002 |
| GET | /actuator/health | none | – |
