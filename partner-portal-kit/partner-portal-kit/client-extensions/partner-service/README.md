# partner-service

Spring Boot 3 microservice. Not generated yet: build plan step 2 creates it from Liferay's
`liferay-sample-etc-spring-boot` sample (Gradle, Java 17+), keeping the `client-extension.yaml`
here. See `.claude/skills/liferay-client-extensions/SKILL.md` for the package layout.

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
