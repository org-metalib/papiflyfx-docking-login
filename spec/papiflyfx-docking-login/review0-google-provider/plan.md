# Plan: Fix Google Provider Refresh Tokens and Default Login Wiring

## Summary

Fix the Google login flow so a personal Gmail or Google account produces a durable, refreshable session by default, and repair the default login bootstrap so the no-arg login factory and restored login docks use the same discovered providers and configured broker as the first-load sample path.

This plan intentionally covers:
- Google OAuth refresh-token reliability
- Default login runtime/factory wiring
- Restored login dock behavior

This plan does not cover:
- New end-user settings for Google consent behavior
- Google `hd` auth hint changes
- Broader non-Google provider refactors

## Public API and SPI Changes

- Add a new `SettingsServicesProvider` ServiceLoader SPI in `papiflyfx-docking-settings-api`.
  - Methods: `SettingsStorage storage()` and `SecretStore secretStore()`.
  - Purpose: give optional consumers like login a shared way to obtain the default settings-backed storage without a direct compile-time dependency on `papiflyfx-docking-settings`.

- Extend `ProviderConfig` in `papiflyfx-docking-login-idapi` with `Map<String, String> authorizationParameters`.
  - Default to an immutable empty map.
  - Keep existing constructors/factory methods updated so callers do not need to supply the map explicitly.

- Extend `LoginRuntime` in `papiflyfx-docking-login` with:
  - `ProviderRegistry providerRegistry()`
  - `void configure(AuthSessionBroker broker, ProviderRegistry registry)`
- Keep `setBroker(...)` for compatibility, but define it as a broker-only override. New production wiring should use `configure(...)`.

## Implementation Changes

### 1. Shared Settings Bootstrap Seam

- Add `SettingsServicesProvider` to `papiflyfx-docking-settings-api`.
- Implement it in `papiflyfx-docking-settings` as a thin adapter over `SettingsRuntime.getDefault()`.
- Register that implementation with `META-INF/services`.

### 2. Default Login Runtime and Factory Wiring

- Make `LoginRuntime` lazily own a single default `ProviderRegistry`.
  - On first access, create the registry and call `discoverProviders()`.
- Make `LoginRuntime` lazily own a default broker.
  - If a `SettingsServicesProvider` is available, create a real `DefaultAuthSessionBroker` using the discovered registry plus the provider’s `storage()` and `secretStore()`.
  - If no `SettingsServicesProvider` is present, keep the existing stub fallback behavior for compatibility.
- Change the no-arg `LoginFactory` to use `LoginRuntime.broker()` and `LoginRuntime.providerRegistry()` instead of allocating an empty registry.
- Keep `LoginStateAdapter.restore()` using the no-arg factory so restored docks automatically inherit the repaired runtime behavior.
- Update sample wiring so `SamplesRuntimeSupport.initialize(...)` calls `LoginRuntime.configure(...)` with its sample registry and broker.
- Update `LoginSample` to read the provider registry from `LoginRuntime` instead of a parallel sample-only source of truth.

### 3. Google Authorization Request Fix

- Update `ProviderSettingsResolver.googleConfig(...)` to always set Google authorization parameters with `access_type=offline`.
- Update `GenericOidcProvider.buildAuthorizationRequest(...)` to append `authorizationParameters` after the standard OAuth/OIDC query parameters.
- Do not force `prompt=consent` on the first Google attempt.

### 4. Conditional Google Consent Retry

- Add a single-retry path in `DefaultAuthSessionBroker` for Google auth-code sign-in.
- After Google token exchange and principal fetch:
  - If the response includes a refresh token, persist it and continue normally.
  - If the response omits a refresh token but the subject already has one stored, reuse the stored token and continue normally.
  - If the response omits a refresh token and the subject has no stored token, immediately restart the Google auth-code flow once with `prompt=consent` added to the Google authorization parameters.
- Guard the retry so it happens at most once per sign-in attempt.
- If the consent retry still yields no refresh token, fail the sign-in with a clear Google-specific error stating that Google did not grant offline access and the session cannot be refreshed.
- Leave existing Google workspace-domain validation unchanged.

## Test Plan

- Add `GoogleProvider` or `GenericOidcProvider` URL-construction tests to verify:
  - Google requests include `access_type=offline` by default.
  - Consent-retry requests include `prompt=consent`.
- Add `DefaultAuthSessionBroker` tests for:
  - First Google exchange returns no refresh token, consent retry succeeds, refresh token is persisted.
  - First Google exchange returns no refresh token, stored token already exists, no retry is triggered.
  - Consent retry still returns no refresh token, broker fails with the new explicit error.
- Add login runtime/factory tests for:
  - `LoginFactory()` exposes discovered providers through the runtime-managed registry.
  - `LoginStateAdapter.restore()` rebuilds a login pane with the same discovered providers instead of an empty registry.
- Update sample/login smoke coverage so at least one test uses the real `SamplesRuntimeSupport.initialize(...)` path instead of replacing the runtime with the stub broker before asserting Google/login behavior.

## Assumptions and Defaults

- The default no-arg login path is considered “real OAuth capable” only when `papiflyfx-docking-settings` is on the classpath and contributes `SettingsServicesProvider`.
- Conditional consent is implemented as an automatic one-time retry, not as a new UI setting.
- Existing public behavior for GitHub and generic OIDC stays unchanged.
- The output document belongs at `spec/papiflyfx-docking-login/review0-google-provider/plan.md`.
