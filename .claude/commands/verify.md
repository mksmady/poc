---
description: Run all local checks before committing
---

Run, in order, and stop at the first failure with the failing output:

1. `./gradlew :client-extensions:partner-service:build`
2. `(cd client-extensions/partner-registration && npm run lint && npm test)`
3. `(cd client-extensions/partner-admin-ui && npm run lint && npm test)`
4. Validate every JSON file under `client-extensions/*/batch/` parses and every item has an `externalReferenceCode`.
5. Check no status is written outside `StatusService`: search `partner-service/src/main` for writes to the `status` field.
6. Check no file contains a raw token, password or OAuth secret.

Report each check as passed or failed.
