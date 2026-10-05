Batch files go here, loaded in name order (see `.claude/skills/liferay-objects/SKILL.md`):

- 00-picklists.batch-engine-data.json
- 10-object-definitions.batch-engine-data.json
- 30-notification-templates.batch-engine-data.json
- 40-object-actions.batch-engine-data.json
- 50-settings-entry.batch-engine-data.json

Generate them from `docs/data-model.md` and `docs/notifications.md` (build plan steps 1 and 10).

Relationships whose parent is one of our objects are nested in that parent's definition
(`objectRelationships`). DXP 2024.Q1 always uses the containing definition as the parent of a
nested relationship (it ignores `objectDefinitionExternalReferenceCode1`), so relationships whose
parent is a system object live in their own files, with the parent named by the
`externalReferenceCode` batch parameter:

- 20-relationships-user.batch-engine-data.json (parent `L_USER`)
- 21-relationships-account.batch-engine-data.json (parent `L_ACCOUNT`)

The object-definition UPSERT deletes nested relationships that are no longer listed, but it does
not touch these, because their parent is the system object.

## Redeploy behaviour (DXP 2024.Q1)

- Every object field must carry `"system": false`. On redeploy the UPSERT goes through
  `putObjectDefinition`, which unboxes `system` and throws `NullPointerException` when it is absent.
- `20-`/`21-relationships-*` use `INSERT` + `ON_ERROR_CONTINUE` (UPSERT needs the numeric parent
  id). On later deploys these tasks report `FAILED` with
  `DuplicateObjectRelationshipExternalReferenceCodeException`; this is expected.
- `50-settings-entry` uses `INSERT` + `ON_ERROR_CONTINUE` on purpose: the entry is created on
  the first deploy and admin edits are never overwritten. On every later deploy that import
  task reports `FAILED` with `DuplicateObjectEntryExternalReferenceCodeException`; this is expected.
