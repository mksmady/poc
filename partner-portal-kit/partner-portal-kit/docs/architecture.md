# Architecture

## Components

| Component | Client extension type | Responsibility | Stories |
|---|---|---|---|
| `partner-batch` | `batch` | Picklists, object definitions, relationships, object permissions, notification templates, object actions | all |
| `partner-service` | microservice (Spring Boot) with `oAuthApplicationHeadlessServer`, `oAuthApplicationUserAgent`, `objectAction`, `workflowAction` | Invitation tokens, guest registration API, status transitions, activation and deactivation, daily expiry/reminder/SLA job | 001, 002, 003, 004, 005, 008 |
| `partner-registration` | `customElement` (React) | Guest wizard: questionnaire, company, contacts, compliance, POS, documents, review | 002 |
| `partner-admin-ui` | `customElement` (React) | Invitation dashboard, AM review, legal queue, contract signing, partner dashboard and detail | 001, 003, 004, 005, 006 |
| `partner-catalog-batch` | `batch` | One-time import of categories, specifications and products into Commerce | 007, 009 |
| `workflow/partner-application.xml` | deployed by `partner-service` setup task | Kaleo tasks for AM review, legal review, contract pending | 003, 004 |

Out of the box Liferay covers: login, forgot password (active users only), Accounts and account
roles, Commerce catalog and product search widgets (US-007), My Workflow Tasks.

## Roles

| Role | Liferay type | Notes |
|---|---|---|
| Partner Account Manager | Regular role | Invites, first review, deactivate own partners |
| Partner Legal | Regular role | Legal review, reject, terminate, contract signed |
| Partner Portal Admin | Regular role | Settings, all records, catalog import, admin alerts |
| Partner Admin | Account role | Partner's own admin, manages their users |
| Partner User | Account role | Browses catalog |

## Flows

### Invite (US-001)
1. AM fills the invitation form in `partner-admin-ui` → `POST /o/c/partnerinvitations` (user-agent OAuth, AM's permissions).
2. Object action `PM_ACTION_INVITATION_CREATED` (client extension) → `partner-service /object/action/invitation-created`.
3. Service generates the token, stores `tokenHash` + `expiresAt`, creates the `PartnerApplication` in `invitationSent`, writes `StatusHistory`, sends `PM_INVITE` with the raw token in the link.
4. Resend: `POST partner-service /api/invitations/{id}/resend` → revoke old token, new token and expiry, status back to `invitationSent` if it was `expired`.

### Register (US-002)
1. Guest opens `/web/partners/register?t=<token>`; the page holds the `partner-registration` element.
2. Element → `POST partner-service /public/registration/session {token}` → service validates the hash, expiry and state, returns a short-lived signed session (JWT, 60 min idle) and the draft.
3. First open moves status to `registrationInProgress`.
4. Saves → `PUT /public/registration/draft`; uploads → `POST /public/registration/documents` (service stores in a private Documents and Media folder and links an `ApplicationDocument`).
5. Company lookup by VAT or registration number → existing Authorized account = existing-company flow; Terminated match = flag for AM.
6. Submit → `POST /public/registration/submit` → validation → `submitted` → workflow starts → `approvalPending`.

### Review (US-003, US-004)
Kaleo tasks drive assignment. Task completion calls `workflowAction` endpoints on `partner-service`,
which call `StatusService`. Revision requests store `revisionRequestedBy` so resubmission returns to
the same stage. Contract signed is a form in `partner-admin-ui` that uploads the signed copy and
calls `POST /api/applications/{id}/contract-signed`.

### Activate, deactivate (US-005)
On `authorized`: create or reuse the Account, create the User, assign `Partner Admin`, add to the
partner site, send activation email. On deactivate: Account inactive, users inactive. Reactivate
reverses it. All through `headless-admin-user` with the headless-server OAuth app.

### Daily job
`@Scheduled` in `partner-service` (cron from `PartnerSettings.jobCron`, default 02:00):
expire invitations, send reminders on `reminderDays`, send SLA reminders after `reviewSlaDays`,
alert admins on failures.

## Security

- Two OAuth apps: headless-server (service-to-Liferay, least scopes needed) and user-agent (custom elements acting as the signed-in user).
- Public endpoints rate-limited per IP and per token hash; generic errors for bad tokens.
- Uploads: PDF, JPG, PNG only, 10 MB max, content-type sniffed, antivirus if available.
- CORS on `partner-service` limited to the portal origin.
