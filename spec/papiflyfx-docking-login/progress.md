# papiflyfx-docking-login — Implementation Progress

## Phase 1 — API Contracts + Scaffold ✅
- [x] Create `papiflyfx-docking-login-idapi` module skeleton (pom.xml)
- [x] Create `papiflyfx-docking-login-session-api` module skeleton (pom.xml)
- [x] Update root `pom.xml` with new modules
- [x] Update `papiflyfx-docking-login/pom.xml` with new dependencies
- [x] `login-idapi`: `IdentityProvider`, `ProviderCapabilities`, `ProviderDescriptor`, `ProviderRegistry`, `ProviderRegistryListener`
- [x] `login-idapi`: `ProviderConfig`, `ProviderConfigValidator`
- [x] `login-idapi`: `TokenResponse`, `UserPrincipal`, `DeviceCodeResponse`, `AuthorizationRequest`, `CodeExchangeRequest`
- [x] `login-session-api`: `SessionManager`, `SessionStore`, `SecretStore`, `SessionPolicy`, `SessionLifecycleListener`
- [x] `login-session-api`: `AuthSession`, `AuthState`, `SessionMetadata`, `SessionEvent`
- [x] ServiceLoader service files
- [x] Build verification

## Phase 2 — Core Implementations ✅
- [x] `login-idapi`: `PkceGenerator`, `OAuthStateStore`, `LoopbackCallbackServer`, `IdTokenValidator`
- [x] `login-idapi`: `GenericOidcProvider`, `GoogleProvider`, `GitHubProvider`
- [x] `login-session-api`: `InMemorySessionStore`, `SettingsSessionStore`, `PreferencesSessionStore`
- [x] `login-session-api`: `InMemorySecretStore`, `SettingsSecretStoreAdapter`
- [x] `login-session-api`: `TokenRefreshScheduler`, `SessionExpiryMonitor`, `SessionStateManager`, `MultiAccountManager`
- [x] Build verification

## Phase 3 — Orchestrator + UI ✅
- [x] `login`: `AuthException`, `AuthErrorCode`
- [x] `login`: `DefaultAuthSessionBroker` (full wiring)
- [x] `login`: `LoginViewModel`
- [x] `login`: `LoginDockPane`, `ProviderSelectionView`, `AuthProgressView`, `AccountStatusWidget`, `DeviceFlowView`, `ErrorView`
- [x] `login`: `LoginFactory`, `LoginStateAdapter`
- [x] `login`: `AuthenticationCategory` (updated settings integration)
- [x] ServiceLoader registrations
- [x] Build verification

## Phase 4 — Hardening + README ✅
- [x] Security hardening pass (PKCE S256, loopback bind to 127.0.0.1, one-shot callback server, state/nonce validation)
- [x] Settings migration support (`LoginSettingsMigrator`)
- [x] GitHub module README (enhanced with architecture, docking/settings integration docs)
- [x] Full build verification (all 14 modules compile successfully)
