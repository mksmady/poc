---
description: Deploy all client extensions to the local Liferay bundle and smoke-test
---

1. Make sure Liferay is up (`curl -s http://localhost:8080/o/headless-admin-user/v1.0/my-user-account` with basic auth `test@liferay.com:test` returns 200). If not, run `blade server start` and wait for it.
2. `./gradlew deploy`
3. Watch `bundles/logs` (or `bundles/tomcat/logs/catalina.out`) for batch import errors and client extension registration.
4. Smoke test: `GET /o/c/partnersettings` returns one entry; `GET http://localhost:58081/actuator/health` returns UP.
5. Report what deployed and any errors.
