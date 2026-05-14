# papiflyfx-docking-login Agent Guidance

This repository was extracted from the PapiflyFX Docking monorepo. Keep changes scoped to this repository's modules and preserve artifact coordinates and package names.

## Lead Roles

- `@auth-specialist` - Owns identity providers, session lifecycle, authentication UI, secret handling, and login integration.
- `@ops-engineer` - Owns Maven build structure, dependency management, settings runtime, samples, archetypes, release configuration, and build validation.
- `@qa-engineer` - Owns test strategy, headless profiles, regression coverage, and deterministic validation.

## Local Rules

- Do not change Maven `groupId`, module `artifactId`, or Java package names.
- Do not change Java source, public APIs, ServiceLoader descriptors, persistence formats, or theme assets as part of repository split maintenance.
- Use `./mvnw -Dmaven.repo.local=$HOME/github/papiflyfx/.m2-split -Dtestfx.headless=true clean verify` for validation.
- Same-repository PapiflyFX dependencies may use `${project.version}`; cross-repository PapiflyFX dependencies must use `${papiflyfx.version}` or BOM management.
- Do not push split repositories until remotes are created explicitly by the project owner.
