# Status lifecycle

Picklist `PM_PL_APPLICATION_STATUS`. `StatusService.transition(app, to, actor, reason)` must
reject anything not in this table. The same table is configured as the picklist state flow.

| From | To | Who / trigger | Side effects |
|---|---|---|---|
| (new) | invitationSent | system, invitation created | send `PM_INVITE` |
| invitationSent | registrationInProgress | partner opens link | – |
| invitationSent | expired | daily job, `expiresAt` passed | `PM_INVITE_EXPIRED` to AM |
| registrationInProgress | expired | daily job, `expiresAt` passed | `PM_INVITE_EXPIRED` to AM |
| expired | invitationSent | AM resend | new token, `PM_INVITE` |
| registrationInProgress | submitted | partner submit | `PM_SUBMITTED`, start workflow |
| submitted | approvalPending | workflow, after first submit or AM revision | AM task created |
| submitted | underLegalReview | workflow, when `revisionRequestedBy = legal` | legal task created |
| approvalPending | revisionRequested | AM | `PM_REVISION`, `revisionRequestedBy = am` |
| approvalPending | underLegalReview | AM approve | `PM_TO_LEGAL` |
| approvalPending | authorized | AM approve, only when `existingCompany = true` (open question 5) | activation into the existing Account, `PM_APPROVED` |
| underLegalReview | revisionRequested | Legal | `PM_REVISION`, `revisionRequestedBy = legal` |
| revisionRequested | submitted | partner resubmit | `PM_SUBMITTED` |
| underLegalReview | contractPending | Legal approve | `PM_CONTRACT_PENDING` |
| underLegalReview | rejected | Legal reject (reason required) | `PM_REJECTED` |
| contractPending | authorized | Legal marks contract signed | activation, `PM_APPROVED` |
| contractPending | terminated | Legal terminate (reason required) | `PM_TERMINATED` |
| authorized | deactivated | AM or Admin (reason required) | account + users inactive, `PM_DEACTIVATED` |
| deactivated | authorized | AM or Admin | account + users active, `PM_DEACTIVATED` (reactivated variant) |
| authorized | terminated | Legal (reason required) | account + users inactive, `PM_TERMINATED` |
| deactivated | terminated | Legal (reason required) | `PM_TERMINATED` |

Terminal: `rejected`, `terminated`. `expired` is terminal unless resent.

Every transition writes one `StatusHistory` row in the same unit of work. If the history write
fails, the status must not change.

## Test matrix
`StatusServiceTest` must cover every row above as allowed and at least these as refused:
`invitationSent → authorized`, `approvalPending → contractPending`, `rejected → anything`,
`terminated → anything`, `submitted → rejected`.
