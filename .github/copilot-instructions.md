# Copilot instructions for this repository

Purpose
- Give Copilot short, practical context about this split repository so completions stay accurate and module-scoped.

Repository at a glance
- Multi-module Maven project with the parent POM at the repository root.
- This repository was extracted from the PapiflyFX Docking monorepo; keep changes scoped to this repository only.
- Modules:
  - `papiflyfx-docking-api` — public docking API, theme/content-state contracts, ribbon provider API, shared UI primitives.
  - `papiflyfx-docking-docks` — JavaFX docking runtime, floating/minimize/maximize behavior, session persistence, ribbon host/runtime, demo/tests.
- Project docs, plans, and design notes live under `spec/`.

Local maintenance rules
- Do not change Maven `groupId`, module `artifactId`, or Java package names.
- Do not change public APIs, ServiceLoader descriptors, persistence formats, or theme assets unless the task explicitly requires it.
- Same-repository PapiflyFX dependencies may use `${project.version}`; cross-repository PapiflyFX dependencies should use shared version management.

Key configuration
- Java is configured via the shared build parent; repository guidance assumes Java 25.
- JavaFX is used heavily in `papiflyfx-docking-docks`; UI tests may require headless-friendly settings.
- Use the Maven Wrapper: `./mvnw` on macOS/Linux, `mvnw.cmd` on Windows.
- For this split workspace, prefer the split-local Maven repository so sibling PapiflyFX snapshots resolve consistently.

Frequently used commands
- Validate the whole repository with the preferred local setup:
  - `./mvnw -Dmaven.repo.local=$HOME/github/papiflyfx/.m2-split -Dtestfx.headless=true clean verify`
- Build the whole repository:
  - `./mvnw -Dmaven.repo.local=$HOME/github/papiflyfx/.m2-split clean package`
- Test the whole repository:
  - `./mvnw -Dmaven.repo.local=$HOME/github/papiflyfx/.m2-split -Dtestfx.headless=true test`
- Build or test a single module:
  - `./mvnw -Dmaven.repo.local=$HOME/github/papiflyfx/.m2-split -pl papiflyfx-docking-api -am clean package`
  - `./mvnw -Dmaven.repo.local=$HOME/github/papiflyfx/.m2-split -pl papiflyfx-docking-docks -am -Dtestfx.headless=true test`
- Run a specific test class:
  - `./mvnw -Dmaven.repo.local=$HOME/github/papiflyfx/.m2-split -pl papiflyfx-docking-docks -am -Dtest=FullyQualifiedTestName test`
- Run the JavaFX demo module:
  - `./mvnw -Dmaven.repo.local=$HOME/github/papiflyfx/.m2-split -pl papiflyfx-docking-docks javafx:run`

Notes about Java / JavaFX runtime
- Confirm toolchain details with `java -version` and `./mvnw -v` when diagnosing environment issues.
- JavaFX apps may need IDE module-path/runtime configuration, but Maven-based test and demo flows are already set up in the module POM.
- `papiflyfx-docking-docks` uses TestFX with Monocle-style headless support; prefer `-Dtestfx.headless=true` for automated validation.

Testing and CI guidance
- Tests live under each module’s `src/test/java`.
- Check `target/surefire-reports` for JUnit output after runs.
- Prefer small, focused regression tests for behavior changes.
- When editing JavaFX-heavy code, add non-UI coverage where practical, but keep existing UI-focused tests up to date when behavior changes.

Useful search keywords for Copilot
- `DockManager`, `DockLeaf`, `DockTabGroup`, `DockSessionSerializer`, `DockSessionStateContributor`, `RibbonDockHost`, `RibbonManager`, `ContentStateRegistry`, `LeafContentData`, `Theme`, `org.metalib.papifly.fx.docks`, `org.metalib.papifly.fx.docking.api`
- File globs:
  - `**/papiflyfx-docking-api/**`
  - `**/papiflyfx-docking-docks/**`
  - `spec/**`

Where to look first
- Root `pom.xml` for module structure.
- `README.md` for repository-level build guidance.
- `papiflyfx-docking-docks/README.md` for runtime, session persistence, ribbon, and demo usage.
- `spec/papiflyfx-docking-api/` and `spec/papiflyfx-docking-docks/` for design notes and implementation plans.

Code style and change guidance
- Keep changes module-scoped whenever possible.
- Prefer the clearest current contract over preserving legacy behavior unless the task explicitly requires compatibility.
- Preserve existing naming, package layout, and persistence conventions.
- If touching docking/session behavior, search for corresponding serializer, layout-data, and FX test coverage before editing.
- If touching ribbon behavior, inspect both `papiflyfx-docking-api` ribbon specs and `papiflyfx-docking-docks` ribbon runtime classes.

If something looks wrong
- Use `./mvnw -X ...` for verbose Maven diagnostics.
- Verify whether a failure is environment-related (missing JavaFX/headless setup) or an actual regression.
- Prefer fixing the smallest relevant module and validating with the narrowest useful Maven command before running full verification.

Quick checklist for common tasks
- Build/verify the repo with the split-local Maven repo.
- Run `papiflyfx-docking-docks` tests with `-Dtestfx.headless=true`.
- Search docking behavior in `DockManager`, `core/`, `layout/`, `serial/`, and `floating/`.
- Search ribbon behavior in `org.metalib.papifly.fx.api.ribbon` and `org.metalib.papifly.fx.docks.ribbon`.

End of instructions
