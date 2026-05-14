# papiflyfx-docking-login

Extracted from the PapiflyFX Docking monorepo.

## Modules

- `papiflyfx-docking-login-idapi`
- `papiflyfx-docking-login-session-api`
- `papiflyfx-docking-login`

## Build

Use the split-local Maven repository so cross-repo snapshots resolve from the extraction workspace:

```bash
./mvnw -Dmaven.repo.local=$HOME/github/papiflyfx/.m2-split -Dtestfx.headless=true clean verify
```

Lead agent: `@auth-specialist`.

## Notes

- Core and settings API dependencies resolve from the split-local Maven repository through `${papiflyfx.version}`.
