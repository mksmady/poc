# Partner Portal (Liferay DXP) — Claude Code guide

This repository builds a partner onboarding and lifecycle portal on **Liferay DXP** using
**Liferay Objects** for data and **client extensions** for all custom code. No OSGi modules
are deployed into Liferay. The functional scope is user stories US-001 to US-009
(`docs/stories/`).

Read these before changing anything:

| File | What it holds |
|---|---|
| `docs/architecture.md` | Components, which client extension does what, request flows |
| `docs/data-model.md` | Every object, field, picklist and relationship (source of truth) |
| `docs/status-lifecycle.md` | The 12 statuses and the only allowed transitions |
| `docs/notifications.md` | Notification templates and their triggers |
| `docs/build-plan.md` | The order to build things in, with done criteria |
| `docs/stories/US-00x.md` | Acceptance criteria per story |
| `docs/open-questions.md` | Decisions not yet made, with the default in use |
| `docs/intellij-setup.md` | IDE setup and shared run configurations (the team uses IntelliJ IDEA) |

If code and these docs disagree, the docs win. If a doc is wrong, update the doc in the same
change and say so in the commit message.

## Repository layout

```
client-extensions/
  partner-batch/            batch CE: object definitions, picklists, notification templates
  partner-catalog-batch/    batch CE: one-time product catalog import (US-009)
  partner-service/          Spring Boot microservice: token API, object actions, workflow actions, daily job
  partner-registration/     React custom element: guest registration wizard (US-002)
  partner-admin-ui/         React custom element: invitations, reviews, partner dashboard (US-001/3/4/5/6)
workflow/
  partner-application.xml   Kaleo definition for the review workflow
docs/                       specs (see table above)
```

A Liferay Workspace wraps this. If `settings.gradle` or `gradle.properties` is missing, create
the workspace first with `blade init -v <dxp-version>` (ask the user for the version; default
`dxp-2025.q1` or later) and move the folders above into it.

## Commands

```bash
./gradlew :client-extensions:partner-service:build          # build + unit tests for the microservice
./gradlew :client-extensions:partner-service:bootRun        # run the microservice locally (port 58081)
./gradlew :client-extensions:<name>:deploy                   # deploy one CE to the local bundle
./gradlew deploy                                             # deploy everything
(cd client-extensions/partner-registration && npm test)      # custom element tests
(cd client-extensions/partner-admin-ui && npm test)
blade server start                                           # local Liferay bundle (bundles/)
```

Local Liferay runs at `http://localhost:8080` (default admin `test@liferay.com` / `test` on a
fresh bundle). Never commit real credentials.

## Rules

1. **Objects first, code second.** If a requirement can be met with an object field, validation,
   picklist state flow, object action or notification template, do that in `partner-batch`
   instead of writing Java or React.
2. **Object definitions live in batch JSON**, not in the UI. If you prototype in the UI, export
   with the Batch Engine and commit the export to `client-extensions/partner-batch/batch/`.
   Files load in name order: `00-picklists`, `10-object-definitions`, `20-relationships`,
   `30-notification-templates`, `40-object-actions`.
3. **External reference codes are mandatory and stable** on every object definition, field,
   picklist, picklist entry, template and action (`PM_` prefix, see `docs/data-model.md`).
   Code looks things up by ERC, never by numeric id.
4. **Status changes go through one place:** `StatusService` in `partner-service`. It checks the
   transition against `docs/status-lifecycle.md`, writes `StatusHistory`, then updates the
   application. Nothing else writes the `status` field.
5. **Guests never call Liferay APIs.** The registration custom element talks only to
   `partner-service` public endpoints, which validate the invitation token and write to Liferay
   with the headless-server OAuth app.
6. **Tokens:** 32 random bytes, URL-safe base64. Store only the SHA-256 hash. Compare in
   constant time. Resend revokes the previous token.
7. **Never delete partner data.** Rejected, Terminated, Deactivated and Expired records stay.
   Deactivation sets account and users inactive.
8. **Configuration** (expiry days, reminder days, SLA days) comes from the single `PartnerSettings`
   object entry, never from constants.
9. Every endpoint and object action gets a unit test. Every story gets at least one end-to-end
   check listed in its story file.
10. Ask before: changing the status lifecycle, adding an object, changing an ERC, or anything
    that would need a data migration.

## Working on a story

Use `/implement-story US-00x`. It reads the story file, checks `docs/build-plan.md` for
dependencies, implements, tests and updates the story's checklist. Use `/verify` before
committing and the `liferay-reviewer` agent for a review pass.

## Liferay version notes

Features this design relies on: Objects with picklist **state flow**, object actions of type
**client extension**, `objectAction` and `workflowAction` client extension types, notification
templates, Batch Engine import of object definitions. Confirm each exists in the target DXP
version before relying on it; if one is missing, record the workaround in
`docs/open-questions.md`.
