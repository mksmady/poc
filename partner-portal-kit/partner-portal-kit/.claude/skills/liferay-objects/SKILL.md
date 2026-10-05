---
name: liferay-objects
description: How to define, change and use Liferay Objects in this repo (batch JSON, ERCs, picklist state flow, object actions, REST). Use whenever adding or changing an object, field, picklist, relationship, validation or object action.
---

# Liferay Objects in this repo

## Where definitions live
`client-extensions/partner-batch/batch/`, loaded in file-name order:

| File | Batch class |
|---|---|
| `00-picklists.batch-engine-data.json` | `com.liferay.headless.admin.list.type.dto.v1_0.ListTypeDefinition` |
| `10-object-definitions.batch-engine-data.json` | `com.liferay.object.admin.rest.dto.v1_0.ObjectDefinition` |
| `20-relationships.batch-engine-data.json` | relationships, if not nested in the definitions |
| `30-notification-templates.batch-engine-data.json` | `com.liferay.notification.rest.dto.v1_0.NotificationTemplate` |
| `40-object-actions.batch-engine-data.json` | object actions, if not nested in the definitions |
| `50-settings-entry.batch-engine-data.json` | the single `PartnerSettings` entry |

Each file has the shape:
```json
{
  "configuration": {
    "className": "<batch class>",
    "companyId": 0,
    "parameters": { "createStrategy": "UPSERT", "updateStrategy": "UPDATE", "onErrorFail": "true" },
    "userId": 0,
    "version": "v1.0"
  },
  "items": [ ... ]
}
```
Class names and parameters can differ between DXP versions. The safest source of truth is an
export from the target version: build the object once in the UI, export it with the Batch Engine,
compare, and commit the export.

## Rules
- Every item has `externalReferenceCode` from `docs/data-model.md`. Never rename one.
- Object definitions: `scope: "company"`, `status: published` after creation, `enableComments: false`, `portlet: false` unless a story needs it.
- `PartnerApplication` has workflow enabled; bind the Kaleo definition `partner-application` to it.
- The status picklist uses **state flow** (field `state: true`, with `objectStateFlow` transitions). Keep it identical to `docs/status-lifecycle.md`.
- Relationships to children use `deletionType: "prevent"`.
- `tokenHash` must not be readable by AM or Legal roles.
- Adding a field: update `docs/data-model.md` in the same change.

## Using objects from code
- REST: `/o/c/<pluralname>` with `?filter=`, `?search=`, `?nestedFields=`, `?fields=`. Look up by ERC with `/o/c/<plural>/by-external-reference-code/{erc}`.
- In `partner-service`, use the `LiferayClient` wrapper (headless-server OAuth) and DTO records. Never hard-code numeric ids.
- Object actions of type client extension call `partner-service` with a JSON payload holding `objectEntry`, `originalObjectEntry` (on update) and `userId`. Verify the JWT from Liferay on every call.
