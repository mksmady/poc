# Developing in IntelliJ IDEA

## Plugins
- **Claude Code [Beta]** (JetBrains Marketplace). Run `claude` in IntelliJ's terminal at the repo root; the plugin connects automatically, shows diffs in the IDE viewer and shares your open file and selection with Claude Code. Shortcut: Cmd+Esc (macOS) / Ctrl+Esc (Windows/Linux) opens Claude Code.
- **Liferay** plugin (Liferay IntelliJ plugin): workspace project wizard, `client-extension.yaml` support, server run configuration.
- Optional: **Kaleo Designer** is not available for IntelliJ; edit `workflow/partner-application.xml` as XML or use Liferay's web workflow designer and export.

## Open the project
1. Install JDK 17 or later (21 recommended) and Node 20 or later.
2. File > Open > the repository root. Choose **Open as Gradle project**.
3. Settings > Build Tools > Gradle: "Build and run using" and "Run tests using" = **Gradle**; Gradle JVM = your JDK 17+.
4. If the Liferay Workspace files are missing, run `blade init -v <dxp-version>` in the terminal (or File > New > Project > Liferay > Liferay Gradle Workspace) and move the kit's folders in.
5. Run `./gradlew initBundle` once to download the local DXP bundle into `bundles/`. DXP needs a license file in `configs/local/deploy/` or `bundles/deploy/`.

## Run configurations (shared in `.run/`)
| Name | What it runs |
|---|---|
| Deploy all client extensions | `./gradlew deploy` |
| Deploy partner-batch | `./gradlew :client-extensions:partner-batch:deploy` |
| partner-service bootRun | the Spring Boot microservice on port 58081; use the Debug button to set breakpoints |
| partner-service test | its unit tests |
| Start Liferay server | `startDockerContainer` (Docker-based workspace). If you use the Tomcat bundle instead, add a **Liferay Server** run configuration from the Liferay plugin pointing at `bundles/` |

## Custom elements
Open `client-extensions/partner-registration` and `partner-admin-ui` in the same window. Add npm
run configurations (`npm run dev`, `npm test`) from each `package.json` gutter icon once the
elements are generated. Set Settings > Languages > JavaScript > Prettier/ESLint to automatic.

## Debugging an object action end to end
1. Start Liferay and `partner-service bootRun` in Debug.
2. Put a breakpoint in the object action controller.
3. Create a PartnerInvitation in Liferay; the breakpoint hits with the payload Liferay sent.
