# Data model

All objects are **company-scoped**, published, and carry the ERCs below. REST paths follow the
object's plural label (`/o/c/partnerapplications`). Field names are the API names.

## Picklists

| ERC | Name | Entries (key) |
|---|---|---|
| `PM_PL_APPLICATION_STATUS` | Partner Application Status | invitationSent, registrationInProgress, submitted, approvalPending, revisionRequested, underLegalReview, contractPending, authorized, rejected, terminated, deactivated, expired |
| `PM_PL_INVITATION_STATE` | Invitation State | active, used, revoked, expired |
| `PM_PL_REVIEW_STAGE` | Review Stage | am, legal |
| `PM_PL_CONTACT_ROLE` | Contact Role | primary, finance, technical, other |
| `PM_PL_DOCUMENT_TYPE` | Document Type | tradeRegister, vatCertificate, bankLetter, certificate, signedContract, other |
| `PM_PL_POS_TYPE` | POS Type | store, warehouse, online, other |

`PM_PL_APPLICATION_STATUS` uses **state flow** with the transitions in `status-lifecycle.md`.

## Objects

### PartnerInvitation — `PM_OBJ_INVITATION`
| Field | Type | Req | Notes |
|---|---|---|---|
| email | Text | ✓ | unique among `active` invitations (validation) |
| firstName, lastName | Text | ✓ | |
| tokenHash | Text | | SHA-256 hex; hidden from UI, no view permission for non-admins |
| expiresAt | DateTime | ✓ | |
| sentAt, lastReminderAt | DateTime | | |
| resendCount, reminderCount | Integer | | default 0 |
| state | Picklist `PM_PL_INVITATION_STATE` | ✓ | |
| invitedBy | Relationship → User | ✓ | the AM |

### PartnerApplication — `PM_OBJ_APPLICATION` (workflow-enabled)
| Field | Type | Req | Notes |
|---|---|---|---|
| applicationStatus | Picklist `PM_PL_APPLICATION_STATUS` (state) | ✓ | written only by `StatusService`; default `invitationSent`. Not called `status`: that name is reserved for the workflow status system field |
| companyName, registrationNumber, vatId | Text | on submit | |
| street, city, postalCode, country, website | Text | on submit | country ISO-3166 alpha-2 |
| employeeBand | Text | | |
| questionnaire | Long Text | | JSON `{questionId: answer}` |
| sanctionsDeclaration, gdprConsent | Boolean | on submit | |
| complianceNotes, certifications | Long Text | | |
| requestedCategories | Text | | comma list of category ERCs |
| existingCompany | Boolean | | set by lookup |
| watchlistMatch | Boolean | | matched a terminated company |
| revisionRequestedBy | Picklist `PM_PL_REVIEW_STAGE` | | |
| submittedAt, authorizedAt | DateTime | | |
| contractSigned | Boolean | | |
| contractSignedDate | Date | | |
| contractReference | Text | | |
| statusReason | Long Text | | reason for reject/terminate/deactivate |
| invitation | Relationship ← PartnerInvitation (1:1) | ✓ | |
| account | Relationship → Account | | set on authorize or existing-company match |
| assignedAM, legalReviewer | Relationship → User | | |

### Children of PartnerApplication (1:N, cascade = prevent delete)
| Object | ERC | Fields |
|---|---|---|
| ApplicationContact | `PM_OBJ_CONTACT` | fullName ✓, email ✓, phone, jobTitle, role (picklist) |
| POSLocation | `PM_OBJ_POS` | name ✓, street, city, postalCode, country, posType, categories |
| ApplicationDocument | `PM_OBJ_DOCUMENT` | documentType ✓, file (Attachment, private folder) ✓, note |
| ReviewComment | `PM_OBJ_COMMENT` | stage ✓, section, body ✓, author (User) |
| StatusHistory | `PM_OBJ_STATUS_HISTORY` | fromStatus, toStatus ✓, actor (User), reason, changedAt ✓ — no update or delete permission for anyone |

### PartnerSettings — `PM_OBJ_SETTINGS` (exactly one entry, ERC `PM_SETTINGS_DEFAULT`)
| Field | Default |
|---|---|
| invitationExpiryDays | 30 |
| reminderDays | `7,21,27` |
| reviewSlaDays | 5 |
| revisionLinkDays | 14 |
| activationLinkHours | 72 |
| maxUploadMb | 10 |
| jobCron | `0 0 2 * * *` |

## ERC conventions

ERCs not listed above follow these patterns. They are generated into
`partner-batch/batch/*.json` and must never change once deployed.

| Item | Pattern | Example |
|---|---|---|
| Picklist entry | `PM_PLE_<list>_<KEY_SNAKE>` | `PM_PLE_APPLICATION_STATUS_INVITATION_SENT` |
| Object field | `PM_FLD_<object>_<FIELD_SNAKE>` | `PM_FLD_INVITATION_TOKEN_HASH` |
| Relationship | `PM_REL_<parent>_<child>` | `PM_REL_APPLICATION_CONTACT` |

`<list>` and `<object>` are the part of the parent ERC after `PM_PL_` / `PM_OBJ_`.

## Relationships (all oneToMany)

| ERC | Name | Parent → child | Deletion |
|---|---|---|---|
| `PM_REL_INVITATION_APPLICATION` | invitationApplication | PartnerInvitation → PartnerApplication | prevent |
| `PM_REL_APPLICATION_CONTACT` | applicationContacts | PartnerApplication → ApplicationContact | prevent |
| `PM_REL_APPLICATION_POS` | applicationPosLocations | PartnerApplication → POSLocation | prevent |
| `PM_REL_APPLICATION_DOCUMENT` | applicationDocuments | PartnerApplication → ApplicationDocument | prevent |
| `PM_REL_APPLICATION_COMMENT` | applicationComments | PartnerApplication → ReviewComment | prevent |
| `PM_REL_APPLICATION_STATUS_HISTORY` | applicationStatusHistory | PartnerApplication → StatusHistory | prevent |
| `PM_REL_ACCOUNT_APPLICATION` | applicationAccount | Account (`L_ACCOUNT`) → PartnerApplication | disassociate |
| `PM_REL_USER_APPLICATION_AM` | applicationAssignedAM | User (`L_USER`) → PartnerApplication | disassociate |
| `PM_REL_USER_APPLICATION_LEGAL` | applicationLegalReviewer | User → PartnerApplication | disassociate |
| `PM_REL_USER_INVITATION` | invitationInvitedBy | User → PartnerInvitation | disassociate |
| `PM_REL_USER_COMMENT` | reviewCommentAuthor | User → ReviewComment | disassociate |
| `PM_REL_USER_STATUS_HISTORY` | statusHistoryActor | User → StatusHistory | disassociate |

Notes for DXP 2024.Q1:
- Liferay has no 1:1 relationship. Invitation → Application is oneToMany; `partner-service`
  creates exactly one application per invitation.
- Relationships with a system parent (User, Account) are in `20-`/`21-relationships-*.json`, not
  nested in our definitions (see `partner-batch/batch/README.md`). REST field names:
  `r_applicationAssignedAM_userId`, `r_applicationLegalReviewer_userId`,
  `r_applicationAccount_accountEntryId`, `r_invitationInvitedBy_userId`,
  `r_reviewCommentAuthor_userId`, `r_statusHistoryActor_userId`,
  `r_invitationApplication_c_partnerInvitationId`, `r_application<Children>_c_partnerApplicationId`.
- Relationship fields cannot be marked required in batch; `partner-service` always sets
  `invitation` and `invitedBy`.
- Integer defaults (`resendCount`, `reminderCount`) are set to 0 by `partner-service`.
- The attachment field caps uploads at 10 MB; `partner-service` enforces the lower
  `PartnerSettings.maxUploadMb`.

## Liferay entities used
- **Account** (type business): one per partner company. Custom fields: `pmPartnerStatus`, `pmApplicationErc`.
- **User**: partner users; linked to Account with account role `Partner Admin` or `Partner User`.
- **Account Group** `PM_AG_<CATEGORY>`: drives product visibility per category (US-007).

## Permissions summary
| Object | Partner (account role) | AM | Legal | Admin |
|---|---|---|---|---|
| PartnerInvitation | – | create, view own | view | create, view, update |
| PartnerApplication | through `partner-service` | view own; changes through `partner-service` | view; changes through `partner-service` | view; changes through `partner-service` |
| Children | through `partner-service` | view, add comments | view, add comments | view, add comments |
| StatusHistory | – | view | view | view |
| PartnerSettings | – | – | – | view, update |

No role can update `PartnerApplication` or delete anything (rules 4 and 7; open question 18).

Guests get **no** object permissions; all guest access goes through `partner-service`.

Object permissions cannot be shipped in batch JSON: the resource names contain the numeric
object definition id. `partner-service` applies them at startup (`RoleBootstrap`): it looks up
each definition by ERC and upserts the regular roles `Partner Account Manager`
(`PM_ROLE_AM`), `Partner Legal` (`PM_ROLE_LEGAL`) and `Partner Portal Admin`
(`PM_ROLE_ADMIN`) with the permissions above. On DXP 2024.Q1 the role name is the effective
ERC (open question 17). "Own" means the AM is the entry's
`invitedBy` / `assignedAM`; `partner-service` and the admin UI filter on that.
