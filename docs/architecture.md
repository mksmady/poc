# Architecture

## Components

| Component | Client extension type | Responsibility | Stories |
|---|---|---|---|
| `partner-batch` | `batch` | Picklists, object definitions, relationships, notification templates, Groovy object actions and validation rules (`scripts/`) | all |
| `tools/partner-setup.mjs` | (setup script) | Applies the Groovy scripts to a running portal; registration role, user, password policy and upload folder; AM resend permission | 001, 002 |
| `partner-service` | microservice (Spring Boot) with `oAuthApplicationHeadlessServer`, `oAuthApplicationUserAgent`, `objectAction`, `workflowAction` | Activation and deactivation, workflow actions, daily expiry/reminder/SLA job. Not used for US-001/US-002 since 2026-10-05 (open question 30) | 003, 004, 005, 008 |
| `partner-registration` | `customElement` (React) | Guest wizard over the Object API as the registration user: questionnaire, company, contacts, compliance, POS, documents, review | 002 |
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
| Partner Registration Client | Regular role | Only the registration page user `partner-registration@partnerportal.local`; view invitations, view/update applications, manage contacts, POS and documents, upload to one folder. Limited further by `PM_VAL_*_REGISTRATION` |

## Flows

### Invite (US-001)
1. AM fills the invitation form in `partner-admin-ui` (view `invitations`) → `POST /o/c/partnerinvitations`
   with the AM's session (`Liferay.Util.fetch`). Validation `PM_VAL_INVITATION_ACTIVE_UNIQUE` refuses
   a second active invitation for the email; the UI adds watchlist warnings from terminated
   applications.
2. Groovy action `PM_ACTION_INVITATION_CREATED` (onAfterAdd, `scripts/invitation-issue.groovy`):
   generates the token, stores `tokenHash` and `expiresAt` (from `PartnerSettings`), creates the
   `PartnerApplication` in `invitationSent` with its `StatusHistory` row, and queues `PM_INVITE`
   with the raw token in the link (values passed at send time, never stored).
3. Resend: the UI raises `resendCount` (AMs have UPDATE on invitations). `PM_VAL_INVITATION_RESEND`
   allows it for the inviting or assigned AM or an admin while the application is invitationSent,
   registrationInProgress or expired; `PM_ACTION_INVITATION_RESEND` runs the same script: new hash
   (old link revoked), new expiry, `expired` back to `invitationSent`, new `PM_INVITE`.

### Register (US-002)
1. Guest opens the link (`/web/guest/partner-registration?t=<token>` locally); the page holds the
   `partner-registration` element, which calls the Object API with basic auth as the registration
   user (no cookies).
2. The element hashes the token (SHA-256), finds the invitation by `tokenHash`, checks state and
   expiry, and loads the application and its children. Every later call re-reads the invitation, so
   a resend or expiry ends the open page.
3. First open PATCHes `applicationStatus` to `registrationInProgress`. The state flow checks it and
   `PM_ACTION_APPLICATION_STATUS_CHANGED` writes history only when the status really changed, so two
   tabs opening at once record one change.
4. Saves PATCH the application and create, update or delete contacts and POS rows (empty rows are
   not stored; all checks run before the first write). Uploads go to the
   "Partner registration uploads" folder, are attached to a new `ApplicationDocument` (Liferay
   copies the file into the object's storage) and the folder copy is deleted.
5. Company lookup in the element: a VAT or registration number of an authorized application with an
   Account = existing-company mode (company data copied, account linked); a terminated match sets
   `watchlistMatch`.
6. Submit PATCHes `submitted`. `PM_VAL_APPLICATION_SUBMIT` refuses an incomplete application; then
   the status action writes history, marks the invitation `used`, sends `PM_SUBMITTED` (partner, AM,
   and legal after a legal revision) and routes to `approvalPending` or `underLegalReview`.
7. After submit the link opens read-only; `PM_VAL_APPLICATION_REGISTRATION` and the
   `PM_VAL_*_REGISTRATION` child rules refuse changes by the registration user.

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
