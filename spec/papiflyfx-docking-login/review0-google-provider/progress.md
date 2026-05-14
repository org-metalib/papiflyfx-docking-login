# Progress: Review0 Google Provider Fixes

Date: 2026-03-27
Scope: `spec/papiflyfx-docking-login/review0-google-provider/plan.md`

## Overall status

- Shared settings bootstrap seam: `completed`
- Default login runtime and factory wiring: `completed`
- Google authorization request fix: `completed`
- Conditional Google consent retry: `completed`
- SamplesApp auth flow integration: `completed`
- Test plan execution: `completed`

## Completed work

### Shared settings bootstrap seam

- Added `SettingsServicesProvider` to `papiflyfx-docking-settings-api`.
- Added `DefaultSettingsServicesProvider` in `papiflyfx-docking-settings`.
- Registered the settings services provider through `META-INF/services`.

### Default login runtime and factory wiring

- Extended `LoginRuntime` with a lazily discovered default `ProviderRegistry`.
- Extended `LoginRuntime` with `configure(AuthSessionBroker, ProviderRegistry)` while keeping `setBroker(...)` for broker-only overrides.
- Updated the default runtime path to create a real `DefaultAuthSessionBroker` when `SettingsServicesProvider` is available, with stub fallback preserved when it is not.
- Updated the no-arg `LoginFactory` to use `LoginRuntime.broker()` and `LoginRuntime.providerRegistry()`.
- Preserved `LoginStateAdapter.restore()` behavior so restored login docks inherit the repaired no-arg runtime path.
- Updated `SamplesRuntimeSupport.initialize(...)` to configure `LoginRuntime` with the sample broker and registry.
- Updated `LoginSample` to read its provider registry from `LoginRuntime`.

### Google authorization request fix

- Extended `ProviderConfig` with immutable `authorizationParameters`.
- Updated `GenericOidcProvider.buildAuthorizationRequest(...)` to append `authorizationParameters` after the standard OAuth/OIDC query parameters.
- Updated `ProviderSettingsResolver.googleConfig(...)` to request Google offline access with `access_type=offline`.
- Kept the first Google authorization attempt free of `prompt=consent`.

### Conditional Google consent retry

- Added a one-time Google auth-code retry path in `DefaultAuthSessionBroker`.
- Retried with `prompt=consent` only when Google omitted a refresh token and no stored refresh token existed for the resolved subject.
- Reused the previously stored refresh token when one already existed for the same Google subject.
- Added an explicit Google-specific failure when offline access was still not granted after the consent retry.
- Left existing Google Workspace domain validation unchanged.

### SamplesApp auth flow integration

- Updated `SamplesApp` so the sample navigation tree is truly hierarchical, with samples nested under their category nodes instead of being rendered as flat siblings.
- Added top-bar quick actions for `Auth Settings` and `Login Demo` so the authentication path is directly reachable from the sample shell.
- Added an inline auth hint in the top bar and updated the placeholder copy in the content area to explain the Google configuration and login-demo flow.
- Added programmatic sample selection helpers so those quick actions expand the navigation tree, focus the correct sample, and display it through the same content-loading path as normal navigation.

## Added and updated tests

- Added `ProviderSettingsResolverTest` to verify Google authorization URLs include `access_type=offline` by default and `prompt=consent` only when explicitly configured.
- Extended `DefaultAuthSessionBrokerTest` with Google refresh-token retry, reuse, and hard-failure coverage.
- Added `LoginRuntimeFxTest` to verify runtime-discovered providers are exposed through `LoginFactory()` and `LoginStateAdapter.restore()`.
- Extended `SamplesSmokeTest` with a real `SamplesRuntimeSupport.initialize(...)` path assertion so Google login no longer relies on the stub broker in the validation path.
- Re-ran the full samples reactor after the `SamplesApp` navigation and top-bar changes to confirm the app-level follow-up did not introduce regressions.

## Validation

- `./mvnw -pl papiflyfx-docking-samples -am test -Dtestfx.headless=true`
- Result: `success`
- Reactor summary: all `14` modules passed
- Login module tests: `11` passed, `0` failed, `0` errored
- Samples smoke tests: `10` passed, `0` failed, `0` errored

## Outcome

- The default no-arg login path is now real-OAuth capable when the settings module contributes `SettingsServicesProvider`.
- Google sign-in now requests offline access on the first attempt and escalates to a one-time consent retry only when required to obtain a refreshable session.
- Restored login docks now rebuild against the runtime-managed provider registry instead of dropping back to an empty default registry.
- `SamplesApp` now surfaces the Google-auth path directly through the shell UI instead of leaving the login/settings flow buried in the sample catalog.
