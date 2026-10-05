# Open questions

Each has a default the code uses until someone decides otherwise. When one is answered, update
the default, the affected docs and the code in one change.

| # | Question | Default in use |
|---|---|---|
| 1 | Target Liferay DXP version? | 2025.Q1 or later |
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
| 13 | How are link-bearing emails sent? (see notifications.md) | To decide in build step 2 |
| 14 | How does the partner set the first password? | To decide in build step 8: Liferay password-reset ticket if reachable from headless APIs, else link to the Forgot Password page after activation |
