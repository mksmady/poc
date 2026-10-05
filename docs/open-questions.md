# Open questions

Each has a default the code uses until someone decides otherwise. When one is answered, update
the default, the affected docs and the code in one change.

| # | Question | Default in use |
|---|---|---|
| 1 | Target Liferay DXP version? | **Answered: DXP 2024.Q1** (`gradle.properties`: `dxp-2024.q1.31`; installed bundle in `D:/hsdsdxp/liferay-dxp` is from the 2024.q1.1 archive — align the two). Feature check below |
| 2 | Who can send invitations? | AMs and admins; sender becomes assigned AM |
| 3 | Can an AM reject, or only legal? | Legal only |
| 4 | Keep both Submitted and Approval Pending? | Yes; Submitted is momentary |
| 5 | What is "existing company onboarding"? | New contact joining an authorized company: company data read-only, legal review skipped |
| 6 | US-008 lists product hierarchy and criteria items. Real notifications? | No, treated as copied from US-007 |
| 7 | Contract signing: e-signature or offline? | Offline; legal uploads the signed copy |
| 8 | Retention for expired, rejected, terminated records? | Keep indefinitely |
| 9 | What decides which categories a partner sees? | Categories on approved POS locations, mapped to account groups |
| 10 | Does a revision request reset expiry? | No; a new revision link valid `revisionLinkDays` |
| 11 | Source system and format for catalog import? | CSV or JSON extract |
| 12 | Languages? | English only for release 1 |
| 13 | How are link-bearing emails sent? (see notifications.md) | **Decided:** every template is content-only in `30-notification-templates`; `partner-service` renders it with `[%PLACEHOLDER%]` values and queues it with `POST /o/notification/v1.0/notification-queue-entries`. Tokens only ever appear in the queued email |
| 14 | How does the partner set the first password? | Liferay password-reset ticket: 2024.Q1 has `GET /o/headless-admin-user/v1.0/user-accounts/{id}/password-reset-ticket`; details in build step 8 |
| 15 | `tokenHash` hidden from AM/Legal: 2024.Q1 has no field-level permissions, and AMs need view on PartnerInvitation | Keep `tokenHash` on PartnerInvitation. It is a SHA-256 of 32 random bytes and cannot be reversed. `partner-service` and the admin UI never return or show it (`fields=` excludes it). Moving it to an admin-only object would mean adding an object (ask first) |
| 16 | Partner "view own account" on PartnerApplication needs `accountEntryRestricted`, which needs the account link from creation, but the account exists only after authorize | Not account-restricted; partners read their application through `partner-service` (decide in build step 9) |

| 17 | Role ERCs `PM_ROLE_*` | DXP 2024.Q1's `PUT /roles/by-external-reference-code/{erc}` looks roles up by **name** and renames the role to the path value on update, so the role name is the stable key: `Partner Account Manager`, `Partner Legal`, `Partner Portal Admin`. `PM_ROLE_*` stay as documentation ids |
| 18 | Who may update `PartnerApplication` directly? | Nobody (AM, Legal and Admin get VIEW only). Every change goes through `partner-service` so `StatusService` stays the single writer of the status (rule 4) |
| 19 | Java runtime for `partner-service` | Java 17 (compiled with a JDK 21 toolchain, `release 17`); the DXP 2024.Q1 BOM resolves Liferay util jars built for Java 17. Spring Boot 2.7.18 as in the 2024.Q1 samples |
| 20 | Reminder emails and the registration link | Only the token hash is stored, so `PM_INVITE_REMINDER` cannot repeat the link. It points the partner to the link in the invitation email and to the AM for a resend. Re-issuing a token per reminder would revoke the link they already have |
| 21 | SLA reminder frequency | `PM_SLA_REMINDER` goes out on every daily run while an application has waited more than `reviewSlaDays` since its last status change |
| 22 | Invitations screen before build step 7 | The US-001 `invitations` view of `partner-admin-ui` is built with step 3/4 because it only needs the US-001 API; step 7 still covers the US-003/US-004 views. The element bundles its own React 18 and uses the theme's Clay CSS classes instead of `@clayui` packages |
| 23 | What does the link open after submit? | Read-only view while the application is submitted, approvalPending, underLegalReview or contractPending (invitation `used`). Editing again needs the revision link from US-003 (open question 10) |
| 24 | Questionnaire content | Five questions in `partner-registration/src/config.js` (business type, years trading, revenue, customer segment, referral). Only business type is required. Stored as JSON in `PartnerApplication.questionnaire` |
| 25 | POS product categories before the catalog import (US-009) | Fixed list `networking, security, storage, servers, software, services`, overridable with `VITE_CATEGORIES` at build time. Replace with taxonomy category ERCs in build step 11 |
| 26 | Existing-company match (US-002 AC9) | Matched against our own applications: an authorized application with an Account (existing company) or a terminated one (watchlist), by VAT ID or registration number ignoring spaces, dots, dashes and case. Accounts have no registration number field. The matched company's details are shown to the registering partner |
| 27 | Registration page URL locally | `/web/guest/partner-registration` on the Guest site (the only site on the local bundle; 2024.Q1 cannot create content pages over headless, so it is a widget page). Invitation links are the portal URL + that path (`invitation-issue.groovy`). The script would use a `PartnerSettings.registrationUrl` field if one existed; adding it is a data-model change (rule 10). Move to the partners site when it exists |
| 28 | Uploads on 2024.Q1 | Attachment fields do not accept `fileBase64` on this bundle, so files go to the "Partner registration uploads" folder (registration role: add and view there only), are attached by id, and the folder copy is deleted. Liferay may add " (n)" to repeated file names |
| 29 | **Registration page credential (decided 2026-10-05: Object API with basic auth, no microservice)** | The registration user's password is in the page script, so anyone can use it. Mitigations: role limited to registration objects, own password policy (no forced change, user cannot change it, no lockout so others cannot lock the page out), validation rules `PM_VAL_APPLICATION_REGISTRATION` and `PM_VAL_{CONTACT,POS,DOCUMENT}_REGISTRATION` (edits only while editable, statuses only registrationInProgress/submitted). Remaining exposure: with the credential, anyone can read every invitation (including hashes) and application, change any application that is still editable, and delete contacts, POS rows and documents of any application (Liferay does not validate deletes). Accepted for now; revisit before production (OAuth client credentials with object scopes, or a backend again) |
| 30 | **Without `partner-service` (decided 2026-10-05; local Java is 11, the service needs 17)** | US-001 and US-002 run on Groovy object actions and validation rules (`partner-batch/scripts`). Not covered without the service: the daily job (expiry, reminders, SLA), admin alerts. `RoleBootstrap` in the service replaces role permissions when it starts; it would remove the AM UPDATE on invitations that resend needs, so rerun `tools/partner-setup.mjs` after starting it |

## DXP 2024.Q1 feature check (build step 1)

Checked against the jars of the installed bundle (`D:/hsdsdxp/liferay-dxp/osgi/portal`).

| Feature the design relies on | Present | Evidence / note |
|---|---|---|
| Picklist state flow | Yes | `ObjectField.state`; transitions imported through the `stateFlow` object field setting (`{objectStates:[{key, objectStateTransitions:[{key}]}]}`) |
| Object action of type client extension (`objectAction` CE) | Yes | executor key `function#<ce-id>` (`FunctionObjectActionExecutorImpl`) |
| `workflowAction` CE | Yes | Kaleo `FunctionActionExecutorImpl` |
| `objectValidationRule` CE | Yes | `FunctionObjectValidationRuleEngineImpl` |
| Notification templates over REST / batch | Yes | `com.liferay.notification.rest.dto.v1_0.NotificationTemplate` |
| Send ad-hoc mail through Liferay (open question 13) | Yes | `POST /o/notification/v1.0/notification-queue-entries` takes subject, body and recipients, so `partner-service` can render link-bearing templates itself and still use Liferay's mail queue |
| Batch import of object definitions, picklists | Yes | `ObjectDefinition`, `ListTypeDefinition` DTOs; `createStrategy` UPSERT/INSERT, `importStrategy` ON_ERROR_FAIL/ON_ERROR_CONTINUE |
| Object permissions in batch | No | resource names hold numeric ids; `partner-service` `RoleBootstrap` applies them by ERC lookup |
| Field-level permissions | No | see question 15 |
| Field named `status` | No | reserved (workflow status); field is `applicationStatus` |
| Object OAuth scopes | Yes, lower case | scope is `c_<objectname>.everything` (e.g. `c_partnerinvitation.everything`); `C_PartnerInvitation.everything` is silently dropped |
| DateTime values over REST | Yes, whole seconds | fractional seconds (`...52.356Z`) fail with `DateTimeParseException`; `partner-service` writes `Entries.dateTime()` |
| Object action description | Max 75 chars | longer fails the insert with `Data too long for column 'description'` |
| Object definition UPSERT on redeploy | Yes, with a catch | every object field needs `"system": false`, otherwise `putObjectDefinition` throws NPE |
| `timeStorage` values | — | `convertToUTC` or `useInputAsEntered` (not `useInputAsFormatted`) |
