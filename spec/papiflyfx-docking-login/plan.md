# papiflyfx-docking-login — Unified Implementation Plan

This plan merges:
- `spec/papiflyfx-docking-login/plan-codex.md`
- `spec/papiflyfx-docking-login/plan-copilot-gemini.md` *(used as the Gemini implementation; `plan-gemini.md` is not present in this repo)*
- `spec/papiflyfx-docking-login/plan-copilot-sonnet.md`

## 1. Objective

Implement `papiflyfx-docking-login` as a reusable docking-native authentication module that provides:

1. A dockable login UI that integrates with existing `DockManager` and content restore patterns.
2. OAuth 2.0 / OIDC authentication using Authorization Code + PKCE via system browser + loopback callback.
3. Optional device flow support where provider capability allows (initially GitHub).
4. Session lifecycle management (sign-in, refresh, expiration handling, logout, account switching).
5. Strict secret/non-secret persistence boundaries.

## 2. Scope

### 2.1 In Scope (v1)

1. New Maven module: `papiflyfx-docking-login`.
2. Public API + default runtime implementation.
3. UI states: unauthenticated, auth in progress, authenticated summary, refresh/error feedback.
4. Provider SPI + registry.
5. Built-in providers: Generic OIDC, Google, GitHub.
6. GitHub device flow as optional provider-specific path.
7. Secure token store abstraction with:
   - in-memory implementation (tests)
   - OS keychain implementation (primary)
   - AES-GCM file-vault fallback
8. Docking content integration via `ContentFactory` + `ContentStateAdapter`.
9. Unit, integration, and targeted TestFX coverage.

### 2.2 Out of Scope (v1)

1. Embedded WebView login as primary auth mechanism.
2. Full passkey/WebAuthn implementation.
3. Full enterprise IAM/policy engine.
4. Centralized remote identity broker.
5. Cross-application shared token cache.

## 3. Architectural Decisions

1. **Four-layer architecture**:
   - UI dock content
   - View-model / orchestration
   - Provider adapters (SPI)
   - Secret + metadata persistence
2. **Default flow**: Authorization Code + PKCE with system browser and loopback callback (`127.0.0.1` + ephemeral port).
3. **Optional flow**: Device flow for providers with explicit capability flags.
4. **State model**: explicit auth state machine controlled only by broker.
5. **UI reactivity**: JavaFX properties and listeners, not a required global event bus in v1.
6. **Persistence split**:
   - secrets in `SecureTokenStore`
   - non-secrets in `SessionMetadataStore` and content state maps

## 4. Module and Package Layout

```text
papiflyfx-docking-login/
  src/main/java/org/metalib/papifly/fx/login/
    api/
      AuthSessionBroker.java
      AuthSession.java
      AuthState.java
      IdentityProvider.java
      ProviderCapabilities.java
      UserPrincipal.java
      TokenResponse.java
      DeviceCodeResponse.java
      SecureTokenStore.java
      SessionMetadataStore.java

    core/
      DefaultAuthSessionBroker.java
      LoginViewModel.java
      AuthException.java
      AuthErrorCode.java
      TokenRefreshScheduler.java
      ProviderRegistry.java

    oauth/
      PkceGenerator.java
      OAuthStateStore.java
      LoopbackCallbackServer.java
      IdTokenValidator.java

    providers/
      GenericOidcProvider.java
      GoogleProvider.java
      GitHubProvider.java

    security/
      InMemorySecureTokenStore.java
      OsKeychainSecureTokenStore.java
      FileVaultSecureTokenStore.java
      SecretCipher.java

    persistence/
      PreferencesSessionMetadataStore.java
      LoginStateCodec.java

    ui/
      LoginDockPane.java
      LoginFactory.java
      LoginStateAdapter.java
      AccountStatusWidget.java
      DeviceFlowView.java

  src/main/resources/META-INF/services/
    org.metalib.papifly.fx.docking.api.ContentStateAdapter

  src/test/java/org/metalib/papifly/fx/login/
    ... unit/integration/TestFX tests ...
```

### Naming Decision

Use package root `org.metalib.papifly.fx.login` to stay consistent with existing modules (`fx.code`, `fx.tree`, `fx.media`).

## 5. Maven and Dependency Plan

### 5.1 Root `pom.xml`

1. Add module:
   - `<module>papiflyfx-docking-login</module>`
2. Manage versions in parent properties/dependencyManagement (per repo guidelines).

### 5.2 Parent Properties (new)

1. `nimbus.oauth2.oidc.sdk.version`
2. `jackson.version`
3. `java.keyring.version`

### 5.3 Parent `dependencyManagement` additions

1. `com.nimbusds:oauth2-oidc-sdk`
2. `com.fasterxml.jackson.core:jackson-databind`
3. `com.github.javakeyring:java-keyring`

### 5.4 Module `papiflyfx-docking-login/pom.xml`

Dependencies:

1. `papiflyfx-docking-api`
2. `papiflyfx-docking-docks`
3. `org.openjfx:javafx-controls`
4. `com.nimbusds:oauth2-oidc-sdk`
5. `com.fasterxml.jackson.core:jackson-databind`
6. `com.github.javakeyring:java-keyring`
7. JUnit 5 + TestFX test dependencies

## 6. Core API Contracts (Merged)

### 6.1 `AuthState`

```java
UNAUTHENTICATED,
INITIATING_AUTH,
AWAITING_CALLBACK,
POLLING_DEVICE,
EXCHANGING_CODE,
AUTHENTICATED,
REFRESHING,
EXPIRED,
SIGNED_OUT,
ERROR
```

### 6.2 `AuthSessionBroker`

```java
CompletableFuture<AuthSession> signIn(String providerId);
CompletableFuture<AuthSession> signInWithDeviceFlow(String providerId);
CompletableFuture<AuthSession> refresh(boolean force);
CompletableFuture<Void> logout(boolean revokeRemote);

Optional<AuthSession> activeSession();
List<AuthSession> allSessions();
void setActiveSession(String providerId, String subject);

ReadOnlyObjectProperty<AuthState> authStateProperty();
ReadOnlyObjectProperty<AuthSession> sessionProperty();
```

### 6.3 `IdentityProvider` SPI

Required:

1. provider metadata (`providerId`, `displayName`, `capabilities`, `defaultScopes`)
2. auth URL build for PKCE flow
3. code-to-token exchange
4. user principal fetch/derive

Optional defaults:

1. start/poll device flow
2. token revocation

### 6.4 Token and Session Modeling

1. `TokenResponse` stores protocol token payload returned by provider.
2. `AuthSession` stores non-secret identity/session metadata and secret references.
3. Access token remains in-memory runtime state; refresh token persists only in `SecureTokenStore`.

## 7. Authentication Flow Design

### 7.1 Authorization Code + PKCE (default)

1. Generate `state`, `nonce`, `code_verifier`, `code_challenge`.
2. Start loopback callback server on `127.0.0.1:0`.
3. Open provider auth URL in system browser.
4. Await callback with timeout.
5. Validate `state` (and `nonce` for OIDC ID token).
6. Exchange code for tokens.
7. Persist refresh token in secure store.
8. Build session and transition to `AUTHENTICATED`.

### 7.2 Device Flow (optional)

1. Call provider `requestDeviceCode`.
2. Show verification URL + user code + expiry countdown.
3. Poll token endpoint per provider interval.
4. On success, finalize session using same persistence/state path.

## 8. Provider Strategy

### 8.1 v1 Built-ins

1. `GenericOidcProvider` (configurable discovery + endpoints)
2. `GoogleProvider`
3. `GitHubProvider` (auth code, optional device flow)

### 8.2 Deferred Presets

1. Facebook
2. Amazon
3. Apple (requires HTTPS relay strategy)

### 8.3 Capabilities Flags

Provider capabilities include at least:

1. `supportsAuthCodePkce`
2. `supportsDeviceFlow`
3. `supportsTokenRevocation`
4. `providesOidcIdToken`
5. `requiresHttpsRedirect` (for Apple-style constraints)

## 9. Security and Persistence Plan

### 9.1 Secret vs Non-Secret split

**Secrets**:

1. refresh token
2. vault master key (if fallback vault is used)

**Non-secrets**:

1. provider ID
2. subject
3. display metadata (name/email/avatar)
4. scopes
5. expiry metadata
6. UI state (last selected provider, mode)

### 9.2 `SecureTokenStore` implementations

1. `InMemorySecureTokenStore` (tests)
2. `OsKeychainSecureTokenStore` (primary)
3. `FileVaultSecureTokenStore` (AES-GCM fallback)

### 9.3 Security hardening checklist

1. PKCE S256 and secure randomness.
2. Strict CSRF state validation.
3. Loopback bind only to `127.0.0.1`.
4. Callback timeout and one-shot server shutdown.
5. HTTPS-only token/userinfo endpoints.
6. OIDC token claim validation (`iss`, `aud`, `exp`, `nonce`).
7. No secrets in logs, serialized state, or plaintext files.
8. In-memory secret buffer zeroing where feasible.

## 10. Docking Integration

1. `LoginFactory` implements `ContentFactory` with stable `FACTORY_ID`.
2. `LoginStateAdapter` implements `ContentStateAdapter` and persists non-secret UI/session pointers only.
3. Adapter registered via:
   - `ContentStateRegistry.register(...)` in app startup, or
   - `ServiceLoader` entry under `META-INF/services`.
4. `LoginDockPane` implements `DisposableContent` and releases listeners/schedulers on dispose.

## 11. Error Taxonomy and UX

`AuthErrorCode` includes:

1. `NETWORK_ERROR`
2. `USER_CANCELLED`
3. `CALLBACK_TIMEOUT`
4. `STATE_MISMATCH`
5. `TOKEN_EXCHANGE_FAILED`
6. `TOKEN_VALIDATION_FAILED`
7. `REFRESH_FAILED`
8. `PROVIDER_NOT_REGISTERED`
9. `DEVICE_FLOW_NOT_SUPPORTED`
10. `DEVICE_FLOW_TIMEOUT`
11. `SECRET_STORE_FAILURE`

UI mapping:

1. Recoverable errors show retry action.
2. Security failures force full restart of login flow.
3. Refresh failures degrade to re-auth state cleanly.

## 12. Testing Strategy

### 12.1 Unit tests

1. PKCE generation correctness.
2. Loopback callback parsing and timeout behavior.
3. State/nonce validation.
4. Secret store roundtrip and error behavior.
5. Session state transitions.

### 12.2 Integration tests

1. Mock OIDC auth-code flow.
2. Mock refresh flow.
3. Device flow polling behavior.
4. Metadata restore across broker restart.

### 12.3 TestFX tests

1. Login provider selection and progress states.
2. Device flow code/countdown rendering.
3. Authenticated status widget and logout path.
4. Error state rendering and retry.

## 13. Delivery Phases

### Phase 1: Scaffold + Generic OIDC

1. Module skeleton, API/SPI contracts, basic `LoginDockPane`.
2. PKCE + loopback flow working with mock OIDC provider.
3. `FileVaultSecureTokenStore` baseline.

### Phase 2: Provider Presets + UI completion

1. Google and GitHub providers.
2. Device flow UI and broker path.
3. Dynamic provider registry rendering.

### Phase 3: Secure Store and Refresh

1. OS keychain integration with controlled fallback.
2. Refresh scheduler and forced refresh API.
3. Metadata restore + multi-account switching.

### Phase 4: Hardening and polish

1. Extended negative/security tests.
2. Diagnostics and error panel polish.
3. Sample app integration and docs.

## 14. Conflict Resolution Matrix

| Topic | Codex | Gemini | Sonnet | Merged Decision |
|---|---|---|---|---|
| Package root | `fx.login` | `fx.login` | `fx.docking.login` | Use `fx.login` for consistency with repo modules |
| Auth flow default | System browser + PKCE | System browser + PKCE | System browser + PKCE | Keep system browser + PKCE as mandatory default |
| Device flow | Optional GitHub | Optional | Optional GitHub | Include optional device flow (GitHub first) |
| Event model | JavaFX properties + listeners | JavaFX reactive state | Adds event-bus section | Use properties/listeners in v1; no mandatory global event bus |
| Provider scope v1 | Generic + Google + GitHub | Generic + Google + GitHub | Lists additional presets too | v1 built-ins: Generic/Google/GitHub; others deferred |
| Secret store impl | JNA bridge + vault | JNA/OS keychain + fallback | `java-keyring` + vault | Use `java-keyring` primary with vault fallback |
| Token in `AuthSession` | Included in sample record | Excluded for safety | Included in sample record | Keep tokens out of persisted/public session model; runtime in-memory only |
| OAuth callback API | Has `completeCallback(...)` method | Internal callback handling | Internal callback handling | Handle callback internally in broker; no public callback completion API |
| Apple support | Future preset | Not primary | Detailed relay discussion | Mark as deferred with `requiresHttpsRedirect` capability |
| WebView auth | Fallback only | Avoided | Avoided | Not in v1 primary path |

## 15. Clarifications Needed

1. Confirm source file mapping: should `plan-copilot-gemini.md` be treated as the requested `plan-gemini.md`?
2. Confirm token exposure contract for downstream modules:
   - A) keep access token out of `AuthSession` (current merged decision), or
   - B) include access token in `AuthSession` for convenience.
3. Confirm v1 provider boundary:
   - A) ship only Generic OIDC + Google + GitHub, or
   - B) also ship Facebook/Amazon/Apple stubs in v1.

## 16. Definition of Done

1. Module compiles and tests pass in aggregator build.
2. Sample app can authenticate, refresh, and logout via login dock.
3. Content/session persistence excludes secrets by contract and tests.
4. Provider SPI allows adding a new provider with no broker changes.
5. Security checklist items are implemented and covered where testable.
