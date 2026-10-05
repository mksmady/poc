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
| status | Picklist `PM_PL_APPLICATION_STATUS` (state) | ✓ | written only by `StatusService` |
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

## Liferay entities used
- **Account** (type business): one per partner company. Custom fields: `pmPartnerStatus`, `pmApplicationErc`.
- **User**: partner users; linked to Account with account role `Partner Admin` or `Partner User`.
- **Account Group** `PM_AG_<CATEGORY>`: drives product visibility per category (US-007).

## Permissions summary
| Object | Partner (account role) | AM | Legal | Admin |
|---|---|---|---|---|
| PartnerInvitation | – | create, view own | view | all |
| PartnerApplication | view own account | view/update own | view/update | all |
| Children | view own account | view, add comments | view, add comments | all |
| StatusHistory | – | view | view | view |
| PartnerSettings | – | – | – | all |

Guests get **no** object permissions; all guest access goes through `partner-service`.
