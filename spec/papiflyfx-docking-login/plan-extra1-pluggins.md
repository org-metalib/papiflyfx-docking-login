# papiflyfx-docking-login — Pluggable Component Breakdown

Two orthogonal plugin axes: **identity providers** and **session management**.
Each axis maps to a separate API module so that implementations can be mixed independently.

---

## Module Layout

```
papiflyfx-docking-login-idapi/        → Identity Provider SPI + built-in providers
papiflyfx-docking-login-session-api/   → Session Management SPI + built-in implementations
papiflyfx-docking-login/              → Orchestrator (broker, UI, docking integration)
```

Dependency flow:

```
login-idapi  ←─┐
                ├── login (orchestrator)
login-session-api ←┘
```

The orchestrator (`papiflyfx-docking-login`) depends on both API modules and wires providers to session managers via the `AuthSessionBroker`.

---

## 1. login-idapi — Identity Provider API

Package: `org.metalib.papifly.fx.login.idapi`

### 1.1 SPI Interfaces

| Component | Description |
|---|---|
| `IdentityProvider` | Core SPI — metadata, auth URL build, code exchange, user principal fetch |
| `ProviderCapabilities` | Capability flags record (`supportsAuthCodePkce`, `supportsDeviceFlow`, `supportsTokenRevocation`, `providesOidcIdToken`, `requiresHttpsRedirect`) |
| `ProviderDescriptor` | Record: `providerId`, `displayName`, `iconResource`, `defaultScopes`, `capabilities` |
| `ProviderRegistry` | Discovery and lookup of registered `IdentityProvider` instances |
| `ProviderRegistryListener` | Callback for provider add/remove events |

### 1.2 Data Types

| Component | Description |
|---|---|
| `TokenResponse` | Record: `accessToken`, `refreshToken`, `idToken`, `tokenType`, `expiresIn`, `scope` |
| `UserPrincipal` | Record: `subject`, `displayName`, `email`, `avatarUrl`, `rawClaims` |
| `DeviceCodeResponse` | Record: `deviceCode`, `userCode`, `verificationUri`, `verificationUriComplete`, `expiresIn`, `interval` |
| `AuthorizationRequest` | Record: `authUrl`, `state`, `nonce`, `codeVerifier`, `redirectUri` |
| `CodeExchangeRequest` | Record: `code`, `codeVerifier`, `redirectUri`, `state` |

### 1.3 OAuth / OIDC Utilities

| Component | Description |
|---|---|
| `PkceGenerator` | S256 code verifier + challenge generation |
| `OAuthStateStore` | Transient state/nonce storage for CSRF validation |
| `LoopbackCallbackServer` | `127.0.0.1:0` HTTP server for auth code callback |
| `IdTokenValidator` | OIDC ID token claim validation (`iss`, `aud`, `exp`, `nonce`) |

### 1.4 Built-in Provider Implementations

| Component | Description |
|---|---|
| `GenericOidcProvider` | Configurable OIDC discovery + endpoints; base class for custom providers |
| `GoogleProvider` | Google OAuth 2.0 + OIDC; extends `GenericOidcProvider` with Google-specific defaults |
| `GitHubProvider` | GitHub OAuth + optional device flow; non-OIDC (userinfo via `/user` API) |
| `FacebookProvider` *(deferred)* | Facebook Login; non-OIDC; graph API user fetch |
| `AppleProvider` *(deferred)* | Sign in with Apple; requires HTTPS redirect relay |
| `AmazonProvider` *(deferred)* | Login with Amazon; OIDC-compatible |

### 1.5 Provider Configuration

| Component | Description |
|---|---|
| `ProviderConfig` | Record holding per-provider settings (client ID, scopes, discovery URL, enterprise URL) |
| `ProviderConfigValidator` | Validates provider configuration fields (URL format, non-blank client ID, scope syntax) |

### 1.6 ServiceLoader Registration

```
META-INF/services/org.metalib.papifly.fx.login.idapi.IdentityProvider
```

Third-party providers register via ServiceLoader or programmatic `ProviderRegistry.register(...)`.

---

## 2. login-session-api — Session Management API

Package: `org.metalib.papifly.fx.login.session`

### 2.1 SPI Interfaces

| Component | Description |
|---|---|
| `SessionManager` | Core SPI — create, restore, refresh, expire, destroy sessions |
| `SessionStore` | Persistence SPI for session metadata (non-secret) |
| `SecretStore` | Persistence SPI for session secrets (refresh tokens, vault keys). Delegates to settings module `SecretStore` when available |
| `SessionLifecycleListener` | Callback for session state transitions |
| `SessionPolicy` | Configurable rules: max concurrent sessions, idle timeout, refresh strategy |

### 2.2 Data Types

| Component | Description |
|---|---|
| `AuthSession` | Record: `sessionId`, `providerId`, `subject`, `principal`, `state`, `createdAt`, `expiresAt`, `scopes` |
| `AuthState` | Enum: `UNAUTHENTICATED`, `INITIATING_AUTH`, `AWAITING_CALLBACK`, `POLLING_DEVICE`, `EXCHANGING_CODE`, `AUTHENTICATED`, `REFRESHING`, `EXPIRED`, `SIGNED_OUT`, `ERROR` |
| `SessionMetadata` | Record: `providerId`, `subject`, `displayName`, `email`, `avatarUrl`, `scopes`, `lastAuthenticated`, `expiresAt` |
| `SessionEvent` | Record: `eventType`, `session`, `timestamp`, `error` |

### 2.3 Session Store Implementations

| Component | Description |
|---|---|
| `InMemorySessionStore` | For tests — no persistence |
| `SettingsSessionStore` | Delegates to settings module `SettingsStorage` for scoped key-value persistence |
| `PreferencesSessionStore` | Fallback using `java.util.prefs.Preferences` when settings module is not available |

### 2.4 Secret Store Implementations

| Component | Description |
|---|---|
| `InMemorySecretStore` | For tests |
| `SettingsSecretStoreAdapter` | Delegates to settings module `SecretStore` (primary) |
| `KeychainSecretStore` | OS keychain via `java-keyring` (standalone fallback when settings module is absent) |
| `EncryptedFileSecretStore` | AES-GCM file vault (standalone fallback) |

**Note**: When the settings module is present (expected for production), items 2.4.3 and 2.4.4 are unused — the settings module's own `SecretStore` implementations handle OS keychain and encrypted file storage.

### 2.5 Session Lifecycle Components

| Component | Description |
|---|---|
| `TokenRefreshScheduler` | Schedules proactive token refresh before expiry |
| `SessionExpiryMonitor` | Monitors session expiry and transitions state |
| `SessionStateManager` | State machine enforcing valid `AuthState` transitions |
| `MultiAccountManager` | Manages multiple concurrent sessions; active session selection |

### 2.6 Secret Key Naming

Follows settings module `SecretKeyNames` convention:

| Key Pattern | Usage |
|---|---|
| `login:oauth:refresh:<providerId>:<subject>` | Refresh tokens |
| `login:vault:key` | Vault master key (encrypted file fallback) |
| `login:device:<providerId>:pending` | Transient device-flow state (SESSION scope) |

### 2.7 ServiceLoader Registration

```
META-INF/services/org.metalib.papifly.fx.login.session.SessionManager
META-INF/services/org.metalib.papifly.fx.login.session.SessionStore
META-INF/services/org.metalib.papifly.fx.login.session.SecretStore
```

---

## 3. login (Orchestrator) — Components

Package: `org.metalib.papifly.fx.login`

### 3.1 Broker / Orchestration

| Component | Description |
|---|---|
| `AuthSessionBroker` | API interface — coordinates identity provider and session manager |
| `DefaultAuthSessionBroker` | Default implementation wiring `IdentityProvider` + `SessionManager` |
| `LoginViewModel` | JavaFX properties binding broker state to UI |
| `AuthException` | Exception type with `AuthErrorCode` |
| `AuthErrorCode` | Enum: `NETWORK_ERROR`, `USER_CANCELLED`, `CALLBACK_TIMEOUT`, `STATE_MISMATCH`, `TOKEN_EXCHANGE_FAILED`, etc. |

### 3.2 UI Components

| Component | Description |
|---|---|
| `LoginDockPane` | Main dockable content pane — provider selection, auth progress, status |
| `AccountStatusWidget` | Compact authenticated user display (avatar, name, provider badge) |
| `DeviceFlowView` | Device flow code + countdown + QR code display |
| `ProviderSelectionView` | Grid/list of available identity providers with icons |
| `AuthProgressView` | Progress indicator during auth flow |
| `ErrorView` | Error display with retry action |

### 3.3 Docking Integration

| Component | Description |
|---|---|
| `LoginFactory` | `ContentFactory` implementation — `FACTORY_ID = "login"` |
| `LoginStateAdapter` | `ContentStateAdapter` — persists non-secret UI state only |

### 3.4 Settings Integration

| Component | Description |
|---|---|
| `AuthenticationCategory` | `SettingsCategory` SPI implementation — surfaces provider config, session summary, and actions in settings UI |

---

## 4. Cross-Cutting Concerns

### 4.1 Plugin Discovery Order

1. ServiceLoader scans for `IdentityProvider` and `SessionManager` implementations
2. `ProviderRegistry` collects all discovered providers
3. `DefaultAuthSessionBroker` receives registry + session manager at construction
4. UI queries registry for available providers to render selection

### 4.2 Extensibility Points

| Extension Point | Mechanism | Example |
|---|---|---|
| Add new identity provider | Implement `IdentityProvider` + ServiceLoader | Corporate SAML provider |
| Custom session storage | Implement `SessionStore` + ServiceLoader | Redis-backed session store |
| Custom secret storage | Implement `SecretStore` + ServiceLoader | HashiCorp Vault adapter |
| Session policy | Implement `SessionPolicy` | Enforce single-session-only |
| Provider config validation | Implement `ProviderConfigValidator` | Domain-restricted Google auth |

### 4.3 Module Independence

The two API modules are independent — swapping session management does not affect identity providers and vice versa:

- **Same provider, different session store**: Google auth with OS keychain vs. encrypted file vs. remote vault
- **Same session store, different provider**: OS keychain storing tokens from Google, GitHub, or a corporate OIDC provider
- **Mix-and-match**: Multiple providers active simultaneously, each with sessions managed by the same `SessionManager`

---

## 5. Maven Module Summary

| Module | artifactId | Dependencies |
|---|---|---|
| `login-idapi` | `papiflyfx-docking-login-idapi` | `nimbus-jose-jwt`, `oauth2-oidc-sdk` |
| `login-session-api` | `papiflyfx-docking-login-session-api` | `papiflyfx-docking-settings-api` (optional) |
| `login` | `papiflyfx-docking-login` | `login-idapi`, `login-session-api`, `papiflyfx-docking-api`, `papiflyfx-docking-settings-api` |

---

## 6. Implementation Checklist

### Phase 1 — API Contracts
- [ ] `login-idapi`: `IdentityProvider`, `ProviderCapabilities`, `ProviderDescriptor`, `ProviderRegistry`
- [ ] `login-idapi`: Data types (`TokenResponse`, `UserPrincipal`, `DeviceCodeResponse`, `AuthorizationRequest`)
- [ ] `login-session-api`: `SessionManager`, `SessionStore`, `SecretStore`, `SessionPolicy`
- [ ] `login-session-api`: Data types (`AuthSession`, `AuthState`, `SessionMetadata`, `SessionEvent`)

### Phase 2 — Core Implementations
- [ ] `login-idapi`: `PkceGenerator`, `LoopbackCallbackServer`, `IdTokenValidator`
- [ ] `login-idapi`: `GenericOidcProvider`, `GoogleProvider`, `GitHubProvider`
- [ ] `login-session-api`: `InMemorySessionStore`, `SettingsSessionStore`
- [ ] `login-session-api`: `InMemorySecretStore`, `SettingsSecretStoreAdapter`
- [ ] `login-session-api`: `TokenRefreshScheduler`, `SessionStateManager`, `MultiAccountManager`

### Phase 3 — Orchestrator + UI
- [ ] `login`: `DefaultAuthSessionBroker`, `LoginViewModel`
- [ ] `login`: `LoginDockPane`, `AccountStatusWidget`, `DeviceFlowView`
- [ ] `login`: `LoginFactory`, `LoginStateAdapter`
- [ ] `login`: `AuthenticationCategory` (settings integration)

### Phase 4 — Extended Providers + Hardening
- [ ] `login-idapi`: `FacebookProvider`, `AppleProvider`, `AmazonProvider`
- [ ] `login-session-api`: `KeychainSecretStore`, `EncryptedFileSecretStore` (standalone fallbacks)
- [ ] Security hardening pass (see `plan.md` §9.3)
- [ ] Full test coverage across all three modules