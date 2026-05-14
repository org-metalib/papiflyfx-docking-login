# papiflyfx-docking-login — Consolidated Implementation Plan

Merged from:
- `plan.md` — unified implementation plan (base)
- `plan-extra0.md` — settings module integration additions
- `plan-extra1-pluggins.md` — pluggable component breakdown (3-module split)

---

## 1. Objective

Implement `papiflyfx-docking-login` as a reusable docking-native authentication system providing:

1. A dockable login UI integrating with `DockManager` and content restore patterns.
2. OAuth 2.0 / OIDC authentication using Authorization Code + PKCE via system browser + loopback callback.
3. Optional device flow support where provider capability allows (initially GitHub).
4. Session lifecycle management (sign-in, refresh, expiration handling, logout, account switching).
5. Strict secret/non-secret persistence boundaries.
6. Two orthogonal plugin axes — **identity providers** and **session management** — each in a separate API module so implementations can be mixed independently.
7. Full integration with the settings module for storage, configuration UI, and secret management.

## 2. Scope

### 2.1 In Scope (v1)

1. Three Maven modules: `papiflyfx-docking-login-idapi`, `papiflyfx-docking-login-session-api`, `papiflyfx-docking-login`.
2. Public SPI contracts for identity providers and session management.
3. UI states: unauthenticated, auth in progress, authenticated summary, refresh/error feedback.
4. Provider SPI + registry with ServiceLoader discovery.
5. Built-in providers: Generic OIDC, Google, GitHub.
6. GitHub device flow as optional provider-specific path.
7. Session management delegating to settings module's `SecretStore` and `SettingsStorage`.
8. Docking content integration via `ContentFactory` + `ContentStateAdapter`.
9. Settings panel integration via `SettingsCategory` SPI.
10. Unit, integration, and targeted TestFX coverage.

### 2.2 Out of Scope (v1)

1. Embedded WebView login as primary auth mechanism.
2. Full passkey/WebAuthn implementation.
3. Full enterprise IAM/policy engine.
4. Centralized remote identity broker.
5. Cross-application shared token cache.
6. Facebook, Amazon, Apple providers (deferred to Phase 4).

## 3. Architectural Decisions

1. **Three-module architecture** with two orthogonal plugin axes:
   - `login-idapi` — Identity Provider SPI + built-in providers
   - `login-session-api` — Session Management SPI + built-in implementations
   - `login` — Orchestrator (broker, UI, docking integration)
2. **Default flow**: Authorization Code + PKCE with system browser and loopback callback (`127.0.0.1` + ephemeral port).
3. **Optional flow**: Device flow for providers with explicit capability flags.
4. **State model**: explicit auth state machine controlled only by broker.
5. **UI reactivity**: JavaFX properties and listeners, not a required global event bus in v1.
6. **Persistence delegation**: secrets via settings module's `SecretStore`; non-secrets via `SettingsStorage`. No custom persistence layer in the login modules.
7. **Module independence**: swapping session management does not affect identity providers and vice versa.

Dependency flow:

```
login-idapi  ←─┐
                ├── login (orchestrator)
login-session-api ←┘
```

---

## 4. Module and Package Layout

### 4.1 `papiflyfx-docking-login-idapi`

Package: `org.metalib.papifly.fx.login.idapi`

```text
papiflyfx-docking-login-idapi/
  src/main/java/org/metalib/papifly/fx/login/idapi/
    IdentityProvider.java
    ProviderCapabilities.java
    ProviderDescriptor.java
    ProviderRegistry.java
    ProviderRegistryListener.java
    ProviderConfig.java
    ProviderConfigValidator.java
    TokenResponse.java
    UserPrincipal.java
    DeviceCodeResponse.java
    AuthorizationRequest.java
    CodeExchangeRequest.java
    oauth/
      PkceGenerator.java
      OAuthStateStore.java
      LoopbackCallbackServer.java
      IdTokenValidator.java
    providers/
      GenericOidcProvider.java
      GoogleProvider.java
      GitHubProvider.java

  src/main/resources/META-INF/services/
    org.metalib.papifly.fx.login.idapi.IdentityProvider
```

### 4.2 `papiflyfx-docking-login-session-api`

Package: `org.metalib.papifly.fx.login.session`

```text
papiflyfx-docking-login-session-api/
  src/main/java/org/metalib/papifly/fx/login/session/
    SessionManager.java
    SessionStore.java
    SecretStore.java
    SessionLifecycleListener.java
    SessionPolicy.java
    AuthSession.java
    AuthState.java
    SessionMetadata.java
    SessionEvent.java
    store/
      InMemorySessionStore.java
      SettingsSessionStore.java
      PreferencesSessionStore.java
    secret/
      InMemorySecretStore.java
      SettingsSecretStoreAdapter.java
    lifecycle/
      TokenRefreshScheduler.java
      SessionExpiryMonitor.java
      SessionStateManager.java
      MultiAccountManager.java

  src/main/resources/META-INF/services/
    org.metalib.papifly.fx.login.session.SessionManager
    org.metalib.papifly.fx.login.session.SessionStore
    org.metalib.papifly.fx.login.session.SecretStore
```

### 4.3 `papiflyfx-docking-login` (Orchestrator)

Package: `org.metalib.papifly.fx.login`

```text
papiflyfx-docking-login/
  src/main/java/org/metalib/papifly/fx/login/
    AuthSessionBroker.java
    DefaultAuthSessionBroker.java
    LoginViewModel.java
    AuthException.java
    AuthErrorCode.java
    ui/
      LoginDockPane.java
      AccountStatusWidget.java
      DeviceFlowView.java
      ProviderSelectionView.java
      AuthProgressView.java
      ErrorView.java
    docking/
      LoginFactory.java
      LoginStateAdapter.java
    settings/
      AuthenticationCategory.java

  src/main/resources/META-INF/services/
    org.metalib.papifly.fx.docking.api.ContentStateAdapter
    org.metalib.papifly.fx.settings.api.SettingsCategory
```

---

## 5. Maven and Dependency Plan

### 5.1 Root `pom.xml`

Add modules:

```xml
<module>papiflyfx-docking-login-idapi</module>
<module>papiflyfx-docking-login-session-api</module>
<module>papiflyfx-docking-login</module>
```

### 5.2 Parent Properties (new)

1. `nimbus.oauth2.oidc.sdk.version`

### 5.3 Parent `dependencyManagement` additions

1. `com.nimbusds:oauth2-oidc-sdk`

### 5.4 Module Dependencies

| Module | Dependencies |
|---|---|
| `login-idapi` | `nimbus-jose-jwt`, `oauth2-oidc-sdk` |
| `login-session-api` | `papiflyfx-docking-settings-api` (optional) |
| `login` (orchestrator) | `login-idapi`, `login-session-api`, `papiflyfx-docking-api`, `papiflyfx-docking-settings-api` |

**Removed** (delegated to settings module):
- `com.github.javakeyring:java-keyring`
- `com.fasterxml.jackson.core:jackson-databind`

Test dependencies: JUnit 5 + TestFX (all modules).

---

## 6. Core API Contracts

### 6.1 Identity Provider SPI (`login-idapi`)

#### `IdentityProvider`

Core SPI — metadata, auth URL build, code exchange, user principal fetch.

Required:
1. Provider metadata (`providerId`, `displayName`, `capabilities`, `defaultScopes`)
2. Auth URL build for PKCE flow
3. Code-to-token exchange
4. User principal fetch/derive

Optional defaults:
1. Start/poll device flow
2. Token revocation

#### `ProviderCapabilities`

Capability flags record:

```java
public record ProviderCapabilities(
    boolean supportsAuthCodePkce,
    boolean supportsDeviceFlow,
    boolean supportsTokenRevocation,
    boolean providesOidcIdToken,
    boolean requiresHttpsRedirect
) {}
```

#### `ProviderDescriptor`

```java
public record ProviderDescriptor(
    String providerId,
    String displayName,
    String iconResource,
    List<String> defaultScopes,
    ProviderCapabilities capabilities
) {}
```

#### `ProviderRegistry`

Discovery and lookup of registered `IdentityProvider` instances. Supports:
- ServiceLoader-based discovery
- Programmatic `register(...)` / `unregister(...)`
- `ProviderRegistryListener` callback for add/remove events

#### Data Types

| Type | Fields |
|---|---|
| `TokenResponse` | `accessToken`, `refreshToken`, `idToken`, `tokenType`, `expiresIn`, `scope` |
| `UserPrincipal` | `subject`, `displayName`, `email`, `avatarUrl`, `rawClaims` |
| `DeviceCodeResponse` | `deviceCode`, `userCode`, `verificationUri`, `verificationUriComplete`, `expiresIn`, `interval` |
| `AuthorizationRequest` | `authUrl`, `state`, `nonce`, `codeVerifier`, `redirectUri` |
| `CodeExchangeRequest` | `code`, `codeVerifier`, `redirectUri`, `state` |

#### `ProviderConfig`

Record holding per-provider settings (client ID, scopes, discovery URL, enterprise URL). Validated by `ProviderConfigValidator`.

#### OAuth / OIDC Utilities

| Component | Description |
|---|---|
| `PkceGenerator` | S256 code verifier + challenge generation |
| `OAuthStateStore` | Transient state/nonce storage for CSRF validation |
| `LoopbackCallbackServer` | `127.0.0.1:0` HTTP server for auth code callback |
| `IdTokenValidator` | OIDC ID token claim validation (`iss`, `aud`, `exp`, `nonce`) |

#### Built-in Providers

| Provider | Description |
|---|---|
| `GenericOidcProvider` | Configurable OIDC discovery + endpoints; base class for custom providers |
| `GoogleProvider` | Google OAuth 2.0 + OIDC; extends `GenericOidcProvider` with Google-specific defaults |
| `GitHubProvider` | GitHub OAuth + optional device flow; non-OIDC (userinfo via `/user` API) |

#### Deferred Providers (Phase 4)

| Provider | Notes |
|---|---|
| `FacebookProvider` | Non-OIDC; graph API user fetch |
| `AppleProvider` | Requires HTTPS redirect relay; `requiresHttpsRedirect` capability |
| `AmazonProvider` | OIDC-compatible |

Third-party providers register via ServiceLoader or programmatic `ProviderRegistry.register(...)`.

### 6.2 Session Management SPI (`login-session-api`)

#### `SessionManager`

Core SPI — create, restore, refresh, expire, destroy sessions.

#### `SessionStore`

Persistence SPI for session metadata (non-secret). Implementations:

| Implementation | Description |
|---|---|
| `InMemorySessionStore` | For tests — no persistence |
| `SettingsSessionStore` | Delegates to settings module `SettingsStorage` for scoped key-value persistence |
| `PreferencesSessionStore` | Fallback using `java.util.prefs.Preferences` when settings module is not available |

#### `SecretStore`

Persistence SPI for session secrets (refresh tokens, vault keys). Delegates to settings module `SecretStore` when available.

| Implementation | Description |
|---|---|
| `InMemorySecretStore` | For tests |
| `SettingsSecretStoreAdapter` | Delegates to settings module `SecretStore` (primary) |

**Note**: OS keychain and encrypted file secret storage are handled by the settings module's own `SecretStore` implementations — no standalone fallbacks in the login modules.

#### `SessionPolicy`

Configurable rules: max concurrent sessions, idle timeout, refresh strategy.

#### `SessionLifecycleListener`

Callback for session state transitions.

#### `AuthState`

```java
public enum AuthState {
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
}
```

#### Session Data Types

| Type | Fields |
|---|---|
| `AuthSession` | `sessionId`, `providerId`, `subject`, `principal`, `state`, `createdAt`, `expiresAt`, `scopes` |
| `SessionMetadata` | `providerId`, `subject`, `displayName`, `email`, `avatarUrl`, `scopes`, `lastAuthenticated`, `expiresAt` |
| `SessionEvent` | `eventType`, `session`, `timestamp`, `error` |

Access token remains in-memory runtime state; refresh token persists only in `SecretStore`.

#### Session Lifecycle Components

| Component | Description |
|---|---|
| `TokenRefreshScheduler` | Schedules proactive token refresh before expiry |
| `SessionExpiryMonitor` | Monitors session expiry and transitions state |
| `SessionStateManager` | State machine enforcing valid `AuthState` transitions |
| `MultiAccountManager` | Manages multiple concurrent sessions; active session selection |

### 6.3 Orchestrator API (`login`)

#### `AuthSessionBroker`

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

#### `DefaultAuthSessionBroker`

Default implementation wiring `IdentityProvider` (via `ProviderRegistry`) + `SessionManager`.

#### `LoginViewModel`

JavaFX properties binding broker state to UI.

#### Error Handling

`AuthErrorCode` enum:

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
- Recoverable errors show retry action.
- Security failures force full restart of login flow.
- Refresh failures degrade to re-auth state cleanly.

---

## 7. Authentication Flow Design

### 7.1 Authorization Code + PKCE (default)

1. Generate `state`, `nonce`, `code_verifier`, `code_challenge`.
2. Start loopback callback server on `127.0.0.1:0`.
3. Open provider auth URL in system browser.
4. Await callback with timeout.
5. Validate `state` (and `nonce` for OIDC ID token).
6. Exchange code for tokens.
7. Persist refresh token in `SecretStore`.
8. Build session and transition to `AUTHENTICATED`.

### 7.2 Device Flow (optional)

1. Call provider `requestDeviceCode`.
2. Show verification URL + user code + expiry countdown.
3. Poll token endpoint per provider interval.
4. On success, finalize session using same persistence/state path.

---

## 8. Settings Module Integration

### 8.1 Storage Delegation

All persistence delegates to the settings module:

| Data | Settings Module Component | Scope |
|---|---|---|
| Refresh tokens | `SecretStore` | — |
| Vault master key | `SecretStore` | — |
| Session metadata | `SettingsStorage` | APPLICATION |
| Transient auth state | `SettingsStorage` | SESSION |
| Provider configuration | `SettingsStorage` | APPLICATION (default), WORKSPACE (override) |

### 8.2 Secret Key Naming

Follows `SecretKeyNames` convention:

| Key Pattern | Usage |
|---|---|
| `login:oauth:refresh:<providerId>:<subject>` | Refresh tokens |
| `login:vault:key` | Vault master key (encrypted file fallback) |
| `login:device:<providerId>:pending` | Transient device-flow state (SESSION scope) |

### 8.3 Session Metadata Keys

Stored as scoped settings keys:

- `login.active-provider` (APPLICATION scope)
- `login.session.<providerId>.<subject>.display-name` (APPLICATION scope)
- `login.session.<providerId>.<subject>.scopes` (APPLICATION scope)
- `login.session.<providerId>.<subject>.expiry` (SESSION scope — transient)

### 8.4 `AuthenticationCategory` — Settings Panel

`AuthenticationCategory implements SettingsCategory` surfaces login configuration in the central settings UI:

- **Provider list** — enable/disable toggles per identity provider, with per-provider sub-settings (client ID, custom scopes, discovery URL, enterprise URL).
- **Active session summary** — bound to `AuthSessionBroker.sessionProperty()`.
- **Stored token inventory** — list refresh tokens from `SecretStore.listKeys()` filtered by `login:oauth:refresh:*`; actions to revoke/delete.
- **Action buttons** — as `SettingsAction` instances:
  - `"Test Connection"` — verifies provider reachability
  - `"Refresh Token"` — calls `broker.refresh(true)`
  - `"Logout"` — calls `broker.logout(revokeRemote)`
  - `"Revoke All Tokens"` — clears all `login:oauth:refresh:*` keys
- **Sort order**: `order() = 25`

### 8.5 Declarative Provider Configuration

Each built-in provider exposes configurable fields as `List<SettingDefinition<?>>`:

```
GenericOidcProvider:
  - login.oidc.discovery-url     (STRING, required, URL validator)
  - login.oidc.client-id         (STRING, required)
  - login.oidc.scopes            (STRING, default "openid profile email")

GoogleProvider:
  - login.google.client-id       (STRING, required)
  - login.google.domain-restrict (STRING, optional)

GitHubProvider:
  - login.github.client-id       (STRING, required)
  - login.github.enterprise-url  (STRING, optional, URL validator)
  - login.github.device-flow     (BOOLEAN, default true)
```

`AuthenticationCategory.buildSettingsPane()` uses `SettingControlFactory.createControl()` for automatic form generation.

### 8.6 Configuration Validation

`SettingsValidator<T>` attached to provider `SettingDefinition`s:
- URL format validation for discovery/endpoint URLs
- Non-blank validation for required client IDs
- Scope syntax validation (space-separated, no special characters)
- "Test Connection" action attempts OIDC discovery fetch and returns `ValidationResult`

### 8.7 Settings Schema Migration

`SettingsMigrator` for the `login` namespace. Example migrations:
- v1 → v2: rename `login.oauth.refresh.*` keys to `login:oauth:refresh:*`
- v2 → v3: add `login.session.<provider>.<subject>.avatar-url` field

### 8.8 Scoped Provider Configuration

| Scope | Usage |
|---|---|
| APPLICATION | Default providers and client IDs (machine-wide) |
| WORKSPACE | Project-specific overrides (e.g., different GitHub Enterprise URL per repo) |
| SESSION | Transient auth state (current access token expiry, auth-in-progress flag) |

### 8.9 Security Settings Integration

Login tokens integrate with the settings module's `SecurityCategory`:
- Tokens follow `SecretKeyNames` convention and appear under `login:oauth:refresh:*` group.
- Human-readable labels: parse key pattern to show "Google — user@gmail.com".
- Delete actions from `SecurityCategory` cause broker to transition to `EXPIRED` / `UNAUTHENTICATED`.

### 8.10 Settings Search Integration

`AuthenticationCategory.definitions()` returns the full list of provider `SettingDefinition`s for indexing by the settings search. Search terms like "OAuth", "Google", "client ID", "refresh token" surface the Authentication category.

---

## 9. Docking Integration

1. `LoginFactory` implements `ContentFactory` with stable `FACTORY_ID = "login"`.
2. `LoginStateAdapter` implements `ContentStateAdapter` and persists non-secret UI/session pointers only.
3. Adapter registered via `ServiceLoader` entry under `META-INF/services`.
4. `LoginDockPane` implements `DisposableContent` and releases listeners/schedulers on dispose.

---

## 10. UI Components

| Component | Description |
|---|---|
| `LoginDockPane` | Main dockable content pane — provider selection, auth progress, status |
| `ProviderSelectionView` | Grid/list of available identity providers with icons |
| `AuthProgressView` | Progress indicator during auth flow |
| `AccountStatusWidget` | Compact authenticated user display (avatar, name, provider badge) |
| `DeviceFlowView` | Device flow code + countdown + QR code display |
| `ErrorView` | Error display with retry action |

---

## 11. Security Hardening Checklist

1. PKCE S256 and secure randomness.
2. Strict CSRF state validation.
3. Loopback bind only to `127.0.0.1`.
4. Callback timeout and one-shot server shutdown.
5. HTTPS-only token/userinfo endpoints.
6. OIDC token claim validation (`iss`, `aud`, `exp`, `nonce`).
7. No secrets in logs, serialized state, or plaintext files.
8. In-memory secret buffer zeroing where feasible.

---

## 12. Extensibility Points

| Extension Point | Mechanism | Example |
|---|---|---|
| Add new identity provider | Implement `IdentityProvider` + ServiceLoader | Corporate SAML provider |
| Custom session storage | Implement `SessionStore` + ServiceLoader | Redis-backed session store |
| Custom secret storage | Implement `SecretStore` + ServiceLoader | HashiCorp Vault adapter |
| Session policy | Implement `SessionPolicy` | Enforce single-session-only |
| Provider config validation | Implement `ProviderConfigValidator` | Domain-restricted Google auth |

---

## 13. Plugin Discovery Order

1. ServiceLoader scans for `IdentityProvider` and `SessionManager` implementations.
2. `ProviderRegistry` collects all discovered providers.
3. `DefaultAuthSessionBroker` receives registry + session manager at construction.
4. UI queries registry for available providers to render selection.

---

## 14. Testing Strategy

### 14.1 Unit Tests

1. PKCE generation correctness.
2. Loopback callback parsing and timeout behavior.
3. State/nonce validation.
4. Secret store roundtrip and error behavior.
5. Session state transitions.
6. Provider config validation.

### 14.2 Integration Tests

1. Mock OIDC auth-code flow.
2. Mock refresh flow.
3. Device flow polling behavior.
4. Metadata restore across broker restart.
5. Settings module integration (storage, secret store delegation).

### 14.3 TestFX Tests

1. Login provider selection and progress states.
2. Device flow code/countdown rendering.
3. Authenticated status widget and logout path.
4. Error state rendering and retry.
5. Settings panel provider configuration UI.

---

## 15. Delivery Phases

### Phase 1 — API Contracts + Scaffold ✅

- `login-idapi`: `IdentityProvider`, `ProviderCapabilities`, `ProviderDescriptor`, `ProviderRegistry`
- `login-idapi`: Data types (`TokenResponse`, `UserPrincipal`, `DeviceCodeResponse`, `AuthorizationRequest`, `CodeExchangeRequest`)
- `login-session-api`: `SessionManager`, `SessionStore`, `SecretStore`, `SessionPolicy`
- `login-session-api`: Data types (`AuthSession`, `AuthState`, `SessionMetadata`, `SessionEvent`)
- Module skeletons, Maven configuration

### Phase 2 — Core Implementations ✅

- `login-idapi`: `PkceGenerator`, `LoopbackCallbackServer`, `IdTokenValidator`
- `login-idapi`: `GenericOidcProvider`, `GoogleProvider`, `GitHubProvider`
- `login-session-api`: `InMemorySessionStore`, `SettingsSessionStore`
- `login-session-api`: `InMemorySecretStore`, `SettingsSecretStoreAdapter`
- `login-session-api`: `TokenRefreshScheduler`, `SessionStateManager`, `MultiAccountManager`

### Phase 3 — Orchestrator + UI ✅

- `login`: `DefaultAuthSessionBroker`, `LoginViewModel`
- `login`: `LoginDockPane`, `AccountStatusWidget`, `DeviceFlowView`, `ProviderSelectionView`
- `login`: `LoginFactory`, `LoginStateAdapter`
- `login`: `AuthenticationCategory` (settings integration)
- Refresh scheduler and forced refresh API
- Metadata restore + multi-account switching

### Phase 4 — Extended Providers + Hardening ✅

- `login-idapi`: `FacebookProvider`, `AppleProvider`, `AmazonProvider` (deferred to post-v1)
- Security hardening pass (§11) — PKCE S256, loopback 127.0.0.1, one-shot server, state/nonce validation
- Full test coverage across all three modules
- Settings migration support (`LoginSettingsMigrator`)
- Sample app integration

---

## 16. Conflict Resolution Log

| Topic | Resolution | Source |
|---|---|---|
| Module structure | Split into 3 modules (`login-idapi`, `login-session-api`, `login`) | `plan-extra1-pluggins.md` overrides `plan.md` single-module |
| `SecureTokenStore` | Removed — delegate to settings module `SecretStore` | `plan-extra0.md` §1 |
| `SessionMetadataStore` | Removed — delegate to `SettingsStorage` | `plan-extra0.md` §2 |
| `security/` package (4 classes) | Removed — settings module handles OS keychain + encrypted file | `plan-extra0.md` §13 |
| `persistence/` package (2 classes) | Removed — `SettingsStorage` + `ContentStateAdapter` | `plan-extra0.md` §14 |
| `java-keyring` dependency | Removed — settings module provides equivalent | `plan-extra0.md` §12 |
| `jackson-databind` dependency | Removed — `SettingsStorage` uses Map-based JSON | `plan-extra0.md` §12 |
| Secret key naming | Colon-separated `SecretKeyNames` convention | `plan-extra0.md` §3, `plan-extra1-pluggins.md` §2.6 |
| Settings panel | Added `AuthenticationCategory` via `SettingsCategory` SPI | `plan-extra0.md` §4 |
| Provider config | Declarative `SettingDefinition`s with validators | `plan-extra0.md` §5–6 |
| Standalone secret fallbacks | `KeychainSecretStore`, `EncryptedFileSecretStore` unused when settings module present | `plan-extra1-pluggins.md` §2.4 note |
| Package root | `org.metalib.papifly.fx.login` (consistent with repo modules) | `plan.md` §4 |
| Auth flow default | System browser + PKCE (mandatory) | All three plans agree |
| Device flow | Optional, GitHub first | All three plans agree |
| Event model | JavaFX properties/listeners in v1, no global event bus | `plan.md` §14 |
| Token in `AuthSession` | Excluded from persisted/public model; runtime in-memory only | `plan.md` §14 |

---

## 17. Definition of Done

1. All three modules compile and tests pass in aggregator build.
2. Sample app can authenticate, refresh, and logout via login dock.
3. Content/session persistence excludes secrets by contract and tests.
4. Provider SPI allows adding a new provider with no broker changes.
5. Session management SPI allows swapping storage with no provider changes.
6. Security checklist items are implemented and covered where testable.
7. Settings panel surfaces provider configuration, session summary, and actions.
8. Login secrets appear correctly in Security settings category inventory.