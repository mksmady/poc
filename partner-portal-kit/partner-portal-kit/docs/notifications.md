# Notifications (US-008)

Liferay Notification Templates, ERC = template code. Type `email` for partners, `email` plus
`userNotification` for internal users. Fired by object actions (`On After Update` with a
condition on `status`) unless the "Fired by" column says `partner-service`.

| Template | Event | Recipients | Fired by |
|---|---|---|---|
| `PM_INVITE` | invitation created or resent | invitee | partner-service (needs raw token) |
| `PM_INVITE_REMINDER` | day N of `reminderDays` | invitee | partner-service daily job |
| `PM_INVITE_EXPIRED` | status → expired | assigned AM | object action |
| `PM_SUBMITTED` | status → submitted | partner (confirmation), assigned AM; legal if `revisionRequestedBy = legal` | object action |
| `PM_REVISION` | status → revisionRequested | partner, with latest ReviewComment bodies | partner-service (needs revision link) |
| `PM_TO_LEGAL` | status → underLegalReview | Partner Legal role; partner (progress) | object action |
| `PM_CONTRACT_PENDING` | status → contractPending | partner, AM | object action |
| `PM_APPROVED` | status → authorized | partner (with set-password link), AM | partner-service (needs link) |
| `PM_WELCOME` | partner sets password / first login | partner | partner-service (login event or first-login check) |
| `PM_REJECTED` | status → rejected | partner, AM | object action |
| `PM_TERMINATED` | status → terminated | partner, AM, admins | object action |
| `PM_DEACTIVATED` | status → deactivated or reactivated | partner, AM | object action |
| `PM_SLA_REMINDER` | task older than `reviewSlaDays` | current assignee, admins | daily job |
| `PM_ADMIN_ALERT` | job failure, import errors, mail bounce | Partner Portal Admin role | partner-service |

Placeholders use object field terms, e.g. `[%PARTNERAPPLICATION_COMPANYNAME%]`.

Templates fired by `partner-service` carry a link containing a raw token, which must never be
stored on an object. Check whether the target DXP version's notification REST API can send a
template with extra transient values. If it can, use it. If not, `partner-service` renders the
same subject and body itself and sends over SMTP. Record the choice in `open-questions.md`
during build step 2.

Each template: English first; keep subject and body in batch JSON under
`partner-batch/batch/30-notification-templates.batch-engine-data.json`.

> US-008 also lists "Product hierarchy visibility" and "Display additional product criteria".
> Treated as copied from US-007 and not implemented as notifications (open question 6).
