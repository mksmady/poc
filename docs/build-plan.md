# Build plan

Build in this order. Each step lists what "done" means. Do not start a step before its
dependencies are done.

| # | Step | Depends on | Done when |
|---|---|---|---|
| 0 ✓ | Workspace and local bundle | – | `blade server start` runs DXP; `./gradlew deploy` deploys an empty CE |
| 1 ✓ | `partner-batch`: picklists, objects, relationships, permissions, `PartnerSettings` entry | 0 | All objects visible in Control Panel > Objects with ERCs from `data-model.md`; redeploy is idempotent |
| 2 ✓ | `partner-service` skeleton: both OAuth apps, health endpoint, Liferay REST client, `StatusService` + `StatusHistory` | 1 | `StatusServiceTest` covers the full matrix; service reads `PartnerSettings` |
| 3 ✓ | US-001 invitations: object action, token, resend, `PM_INVITE` | 2 | Story checklist in `stories/US-001.md` passes |
| 4 ✓ | Daily job: expiry, reminders, SLA, admin alerts | 3 | Job runs on a fake clock in tests; reminders sent once per day N |
| 5 | US-002 public registration API + `partner-registration` element | 3 | Guest can open link, save draft, upload, submit; status moves to approvalPending |
| 6 | Kaleo workflow + `workflowAction` endpoints | 2 | Workflow deployed, bound to PartnerApplication; tasks appear for AM and Legal |
| 7 | US-003 and US-004 screens in `partner-admin-ui` | 5, 6 | AM and Legal can do every action in their story files |
| 8 | US-005 activation, deactivation, reactivation | 7 | Authorized partner can set password and log in; deactivated cannot |
| 9 | US-006 partner dashboard and detail | 7 | Search, table, detail with history |
| 10 | US-008 remaining templates and object actions | 3–8 | Every row of `notifications.md` fires in a test |
| 11 | US-009 catalog import + US-007 catalog pages | 1 | Import is rerunnable; partner sees only granted categories |
| 12 | Hardening: rate limits, upload checks, CORS, permissions review | all | `liferay-reviewer` agent finds no blocking issues |

Mark a step done here (✓ in the # column) in the same commit that finishes it.
