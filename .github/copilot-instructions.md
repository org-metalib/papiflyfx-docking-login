# Copilot instructions for this repository

Purpose
- Give Copilot short, practical context about this split repository so completions stay accurate, auth-aware, and module-scoped.

Repository at a glance
- Multi-module Maven project with the parent POM at the repository root.
- This repository was extracted from the PapiflyFX Docking monorepo; keep changes scoped to this repository only.
- Lead role for most code changes: `@auth-specialist`.
- Supporting roles:
  - `@ops-engineer` for Maven build structure, dependency management, release configuration, and build validation.
  - `@qa-engineer` for test strategy, headless profiles, regression coverage, and deterministic validation.
- Modules:
  - `papiflyfx-docking-login-idapi` - identity-provider SPI, provider registry, OAuth helpers, and built-in Google, GitHub, and generic OIDC providers.
  - `papiflyfx-docking-login-session-api` - auth session SPI, session stores, secret stores, session lifecycle, refresh, expiry, and multi-account helpers.
  - `papiflyfx-docking-login` - login runtime, JavaFX auth UI, settings integration, secure secret adapter, session broker, and docking content-state integration.
- Project docs, plans, and review notes live under `spec/papiflyfx-docking-login/`.

Local maintenance rules
- Do not change Maven `groupId`, module `artifactId`, or Java package names.
- Do not change Java source, public APIs, ServiceLoader descriptors, persistence formats, or theme assets as part of repository split maintenance.
- Same-repository PapiflyFX dependencies may use `${project.version}`.
- Cross-repository PapiflyFX dependencies must use `${papiflyfx.version}` or BOM management.
- Do not push split repositories until remotes are created explicitly by the project owner.

Key configuration
- Java is configured via the shared build parent; repository guidance assumes Java 25.
- Use the Maven Wrapper: `./mvnw` on macOS/Linux, `mvnw.cmd` on Windows.
- For this split workspace, prefer the split-local Maven repository so sibling PapiflyFX snapshots resolve consistently.
- The `papiflyfx-docking-login` module uses JavaFX and TestFX; automated UI validation should run with headless settings.

Frequently used commands
- Validate the whole repository with the preferred local setup:
  - `./mvnw -Dmaven.repo.local=$HOME/github/papiflyfx/.m2-split -Dtestfx.headless=true clean verify`
- Build the whole repository:
  - `./mvnw -Dmaven.repo.local=$HOME/github/papiflyfx/.m2-split clean package`
- Test the whole repository:
  - `./mvnw -Dmaven.repo.local=$HOME/github/papiflyfx/.m2-split -Dtestfx.headless=true test`
- Build or test a single module:
  - `./mvnw -Dmaven.repo.local=$HOME/github/papiflyfx/.m2-split -pl papiflyfx-docking-login-idapi -am test`
  - `./mvnw -Dmaven.repo.local=$HOME/github/papiflyfx/.m2-split -pl papiflyfx-docking-login-session-api -am test`
  - `./mvnw -Dmaven.repo.local=$HOME/github/papiflyfx/.m2-split -pl papiflyfx-docking-login -am -Dtestfx.headless=true test`
- Run a specific test class:
  - `./mvnw -Dmaven.repo.local=$HOME/github/papiflyfx/.m2-split -pl papiflyfx-docking-login -am -Dtest=FullyQualifiedTestName -Dtestfx.headless=true test`

Notes about auth and secrets
- Treat provider client secrets, access tokens, refresh tokens, authorization codes, PKCE verifiers, and session secrets as sensitive.
- Do not log, persist, expose in UI text, or add sample values for real secrets or tokens.
- Prefer existing secret-store abstractions: `SecretStore`, `SecureSecretStore`, `SettingsSecretStoreAdapter`, and `SecretStoreSecureAdapter`.
- Keep provider configuration, active session state, and refresh-token inventory compatible with existing settings and persistence contracts.

ServiceLoader and integration points
- Keep ServiceLoader descriptors synchronized with implementation classes when intentionally adding or removing providers or integrations.
- Current descriptors include:
  - `papiflyfx-docking-login-idapi/src/main/resources/META-INF/services/org.metalib.papifly.fx.login.idapi.IdentityProvider`
  - `papiflyfx-docking-login-session-api/src/main/resources/META-INF/services/org.metalib.papifly.fx.login.session.SecretStore`
  - `papiflyfx-docking-login-session-api/src/main/resources/META-INF/services/org.metalib.papifly.fx.login.session.SessionStore`
  - `papiflyfx-docking-login-session-api/src/main/resources/META-INF/services/org.metalib.papifly.fx.login.session.SessionManager`
  - `papiflyfx-docking-login/src/main/resources/META-INF/services/org.metalib.papifly.fx.settings.api.SettingsCategory`
  - `papiflyfx-docking-login/src/main/resources/META-INF/services/org.metalib.papifly.fx.docking.api.ContentStateAdapter`

Testing and CI guidance
- Tests live under each module's `src/test/java`.
- Check `target/surefire-reports` for JUnit output after runs.
- Prefer small, focused regression tests for provider, session, persistence, and runtime behavior changes.
- When editing JavaFX-heavy code, add non-UI coverage where practical, but keep existing TestFX coverage current when UI behavior changes.
- CI uses Zulu JDK 25 with JavaFX and runs package/test flows with `-Dtestfx.headless=true`.

Useful search keywords for Copilot
- Identity and OAuth:
  - `IdentityProvider`, `ProviderRegistry`, `ProviderConfig`, `ProviderDescriptor`, `GoogleProvider`, `GitHubProvider`, `GenericOidcProvider`, `PkceGenerator`, `OAuthStateStore`, `LoopbackCallbackServer`, `IdTokenValidator`
- Session lifecycle:
  - `AuthSession`, `SessionManager`, `SessionStore`, `SecretStore`, `SessionStateManager`, `TokenRefreshScheduler`, `SessionExpiryMonitor`, `MultiAccountManager`
- Runtime, UI, settings, and docking:
  - `AuthSessionBroker`, `DefaultAuthSessionBroker`, `LoginRuntime`, `LoginViewModel`, `ProviderSettingsResolver`, `AuthenticationCategory`, `LoginProviderSettings`, `SecureSecretStore`, `SecretStoreSecureAdapter`, `LoginStateAdapter`, `LoginFactory`
- Packages:
  - `org.metalib.papifly.fx.login.idapi`
  - `org.metalib.papifly.fx.login.session`
  - `org.metalib.papifly.fx.login`
- File globs:
  - `**/papiflyfx-docking-login-idapi/**`
  - `**/papiflyfx-docking-login-session-api/**`
  - `**/papiflyfx-docking-login/**`
  - `spec/papiflyfx-docking-login/**`

Where to look first
- Root `pom.xml` for module structure.
- `README.md` for repository-level build guidance.
- `papiflyfx-docking-login/README.md` for runtime, settings category, and secret-store integration notes.
- `spec/papiflyfx-docking-login/` for design notes, plans, and review notes.
- ServiceLoader files under each module's `src/main/resources/META-INF/services/` for provider and integration discovery.

Code style and change guidance
- Keep changes module-scoped whenever possible.
- Preserve existing naming, package layout, public contracts, and persistence conventions.
- Use structured APIs and existing auth/session abstractions instead of ad hoc parsing or storage.
- If touching provider behavior, inspect the provider registry, OAuth helpers, built-in providers, and provider tests before editing.
- If touching session behavior, inspect stores, secret-store adapters, lifecycle helpers, and broker/runtime tests before editing.
- If touching settings or docking integration, inspect `AuthenticationCategory`, `LoginProviderSettings`, `LoginStateAdapter`, and the ServiceLoader descriptors before editing.

If something looks wrong
- Use `./mvnw -X ...` for verbose Maven diagnostics.
- Verify whether a failure is environment-related, dependency-resolution-related, JavaFX/headless-related, or an actual regression.
- Prefer fixing the smallest relevant module and validating with the narrowest useful Maven command before running full verification.

Quick checklist for common tasks
- Build/verify the repo with the split-local Maven repo.
- Run `papiflyfx-docking-login` UI/runtime tests with `-Dtestfx.headless=true`.
- Search provider behavior in `idapi/`, `idapi/oauth/`, and `idapi/providers/`.
- Search session behavior in `session/`, `session/store/`, `session/secret/`, and `session/lifecycle/`.
- Search login integration in `core/`, `runtime/`, `settings/`, `security/`, `ui/`, and `docking/`.

End of instructions
