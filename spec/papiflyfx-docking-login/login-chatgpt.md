# papiflyfx-docking-login — Login Docking Component (Concept + Spec)

> Status: draft (research-driven)

## 1. Purpose

`papiflyfx-docking-login` provides a reusable **login prompt** and **session layer** for PapiflyFX Docking applications.

It targets modern authentication patterns for desktop apps:

- **Multiple identity providers** (Google, Facebook, GitHub, Apple, Amazon, plus generic OIDC)
- **Secure native-app flows** (Authorization Code + PKCE via system browser; optional Device Flow where appropriate)
- **Session lifecycle management** (sign-in, refresh, expiration, logout)
- **Secret management** (secure storage of refresh tokens and app/session secrets)

PapiflyFX Docking itself persists UI layout sessions; this module persists **authentication session state** in a secure, least-privilege way.

## 2. Goals and non-goals

### 2.1 Goals

- Provide a **Dockable Login Panel** (and optional modal prompt) that can be shown at startup or on-demand.
- Provide a pluggable **IdentityProvider SPI** so apps can enable providers via dependencies/config.
- Support **multi-account** (optional) and switching active identity.
- Centralize **token storage**, refresh, revocation, and logout.
- Provide a clear integration point for other docking modules (e.g., GitHub module, Hugo module) to request an authenticated session.

### 2.2 Non-goals

- Do not implement a full IAM server; this is a client module.
- Do not store user passwords.
- Do not embed provider login pages in a WebView unless absolutely unavoidable (preferred: system browser).

## 3. Concepts

### 3.1 Key idea: “Provider adapters + session broker”

The module is split into:

1. **UI**: JavaFX dockable login view(s)
2. **Provider adapters**: provider-specific OAuth/OIDC parameters and quirks
3. **Session broker**: a single authority for tokens, refresh, logout, and secure storage

This keeps the UX consistent while allowing provider variability.

### 3.2 Authentication protocol baseline

For desktop/native apps, the recommended baseline is:

- **OAuth 2.0 / OpenID Connect** using **Authorization Code + PKCE** (public client)
- Use the **system browser** and a **loopback redirect URI** (or custom scheme) for the callback

Google explicitly documents PKCE steps for installed/desktop apps. citeturn0search1turn1search5

### 3.3 When to use Device Flow

Some providers (notably GitHub) support **OAuth Device Authorization Grant** (device flow), which is useful for headless or constrained UX, and can also be used in desktop apps when callback listeners are problematic.

GitHub documents the device flow for OAuth/GitHub Apps and provides the endpoints/steps. citeturn0search2turn0search14turn0search6turn0search21

## 4. Module placement & naming

- Maven module name: `papiflyfx-docking-login`
- Spec location in repository: `spec/papiflyfx-docking-login/login-chatgpt.md`

Suggested package roots:

- `org.metalib.papiflyfx.docking.login.api`
- `org.metalib.papiflyfx.docking.login.ui`
- `org.metalib.papiflyfx.docking.login.core`
- `org.metalib.papiflyfx.docking.login.providers.*`

## 5. User experience

### 5.1 Docking login prompt

Provide a dockable component that can appear:

- As a **startup gate**: app opens to a login dock
- As a **toolbar action**: “Sign in / Sign out”
- As a **non-blocking status widget**: shows current user + provider + session expiry

Minimal UX elements:

- List of enabled providers: buttons (Google, GitHub, Apple, etc.)
- “Signed in as …” view with:
  - avatar (if available)
  - display name / email (if permitted)
  - active scopes
  - token expiry countdown
  - actions: **refresh**, **logout**, **switch account**

### 5.2 Provider login UX rule

Prefer **system browser** for login to reduce credential phishing risk and to reuse the OS browser session.

Facebook’s docs indicate that desktop login requires an embedded browser/webview-like flow, but in a JavaFX docking app you can still open the system browser and complete the OAuth redirect via callback. citeturn1search1

## 6. Architecture

### 6.1 High-level components

- `LoginDock` (UI)
  - `LoginView` (provider buttons, status)
  - `AccountChooserView` (optional)
  - `ConsentScopeView` (optional, app-defined)

- `AuthSessionBroker` (core)
  - `signIn(providerId)`
  - `handleCallback(uri)`
  - `getActiveSession()`
  - `refreshIfNeeded()`
  - `logout()` / `revoke()`

- `IdentityProvider` SPI
  - `providerId()`
  - `displayName()` / icon
  - `authorizationEndpoint()`
  - `tokenEndpoint()`
  - `scopes()`
  - `supportsDeviceFlow()`
  - `createAuthorizationRequest(pkce, redirectUri, state, nonce)`
  - `exchangeCodeForTokens(code, pkce, redirectUri)`
  - `fetchUserInfo(accessToken)` (optional)

- `SecureTokenStore`
  - stores refresh tokens and other long-lived secrets

- `SessionStore` (non-secret)
  - stores last selected provider, last active account id, UI preferences

### 6.2 Data model (suggested)

- `AuthSession`
  - `providerId`
  - `subject` (stable user id per provider)
  - `displayName` / `email` / `avatarUrl` (optional)
  - `scopes` (granted)
  - `accessToken` (in-memory)
  - `refreshTokenRef` (reference to secure store, not the token itself)
  - `expiresAt`

- `PkcePair`
  - `codeVerifier`
  - `codeChallenge`
  - `method = S256`

## 7. Authentication flows

### 7.1 Authorization Code + PKCE (default)

Steps:

1. Generate `state`, `nonce`, `PkcePair`.
2. Open system browser to provider authorization URL.
3. Receive redirect at:
   - **Loopback listener** (recommended): `http://127.0.0.1:{randomPort}/callback`
   - or custom scheme: `papiflyfx://callback`
4. Validate `state` and (if OIDC) validate ID token/nonce.
5. Exchange code for tokens.
6. Store refresh token (if any) in secure storage.
7. Keep access token in memory; refresh when nearing expiry.

Google’s installed-app OAuth flow explicitly supports PKCE and describes generating verifier/challenge. citeturn0search1turn0search5

### 7.2 GitHub Device Flow (optional per provider)

1. Request `device_code` and `user_code`.
2. Show user code + verification URL in the login dock.
3. Poll token endpoint until authorized or expired.

GitHub’s docs describe device flow and enabling it in app registration. citeturn0search2turn0search6turn0search14turn0search21

### 7.3 OIDC userinfo

If provider supports OIDC:

- Parse ID token (JWT) for basic claims
- Optionally call `userinfo` endpoint

Okta’s JavaFX OIDC example is a good reference for integrating OIDC into JavaFX apps. citeturn0search0turn0search8

## 8. Session management

### 8.1 Session lifecycle

- **Unauthenticated**: no active session
- **Authenticated**: valid access token until `expiresAt`
- **Expiring soon**: refresh token used to obtain new access token
- **Expired**: treat as signed-out unless refresh succeeds

Recommended behavior:

- Refresh when `now + skew >= expiresAt` (e.g., 60–120s skew)
- Backoff on refresh failures; surface errors to UI

### 8.2 Multi-session

Optional feature:

- Store multiple identities (providerId + subject) and switch the “active” one
- Store refresh tokens per identity in secure store

### 8.3 Logout and revocation

- Local logout: clear in-memory access token, mark session inactive
- Optional token revocation if provider supports it

## 9. Session secret management

### 9.1 What counts as a “secret”

- Refresh tokens
- Long-lived API tokens issued by enterprise IdPs
- Locally generated “session secret” used to encrypt cached data on disk

### 9.2 What NOT to store

- Provider *client secrets* for public/native apps (assume these can be extracted)
- User passwords

### 9.3 Storage strategy

**Preferred**: store secrets in OS-provided secure storage:

- macOS Keychain
- Windows Credential Manager / DPAPI
- Linux Secret Service (gnome-keyring / KWallet)

This is a common recommendation for desktop security hardening. citeturn1search11

Implementation approach options:

- **Option A (recommended): OS keychain integration**
  - Use a small native bridge (JNI/JNA) per OS
  - Store refresh tokens and encryption keys by `(appId, providerId, subject)`

- **Option B: Encrypted file vault**
  - Generate a master key
  - Protect the master key in OS keychain
  - Encrypt token cache on disk (AES-GCM)

### 9.4 Session encryption key

If you store any auth-adjacent cached data on disk (e.g., user profile, granted scopes, last provider), use an app-level encryption key:

- `K_app` stored in OS keychain
- Data encrypted with `K_app` (AES-GCM)

## 10. Provider coverage notes

This module should implement **generic OIDC** first, then ship provider presets.

- **Google**: native/desktop OAuth supports PKCE in installed app flows. citeturn0search1turn0search5turn0search5
- **GitHub**: OAuth device flow is available (enable in app settings). citeturn0search2turn0search6turn0search21
- **Amazon**: Login with Amazon uses an authorization code grant flow; PKCE is referenced in their docs/SDK usage. citeturn1search21
- **Facebook**: docs mention desktop apps needing embedded browser/webview-like flow; treat as a provider-specific constraint. citeturn1search1
- **Apple**: “Sign in with Apple” is OAuth/OIDC-like but has additional requirements; implement after generic OIDC is stable.

## 11. Library choices

### 11.1 OAuth client library

Two pragmatic options:

- **ScribeJava** as a lightweight OAuth client helper (OAuth 1.0a + 2.0), plus custom OIDC glue where needed. citeturn0search3turn0search11
- A minimal in-house client using Java `HttpClient` + JWT validation library (keeps dependencies small but requires more work).

The OAuth community list for Java libraries can be used to evaluate alternatives. citeturn0search7

### 11.2 Browser + callback listener

- Open system browser via `java.awt.Desktop.browse(URI)` (or platform-specific handler)
- Start a minimal HTTP server on `127.0.0.1` for the callback
  - keep it local-only
  - random port
  - short timeout

## 12. Public API (proposal)

### 12.1 Core service

```java
public interface AuthSessionBroker {
  CompletableFuture<AuthSession> signIn(String providerId);
  Optional<AuthSession> getActiveSession();

  /** Force refresh (or refresh if expiring). */
  CompletableFuture<AuthSession> refresh(boolean force);

  /** Local logout + optional remote revoke. */
  CompletableFuture<Void> logout(boolean revoke);

  /** Observable stream for UI binding. */
  ReadOnlyObjectProperty<AuthState> authStateProperty();
}
```

### 12.2 Provider SPI

```java
public interface IdentityProvider {
  String providerId();
  String displayName();

  ProviderCapabilities capabilities();

  AuthorizationRequest buildAuthorizationRequest(PkcePair pkce, URI redirectUri, String state, String nonce);
  TokenResponse exchangeCodeForTokens(String code, PkcePair pkce, URI redirectUri);

  default Optional<UserInfo> fetchUserInfo(String accessToken) { return Optional.empty(); }
}
```

### 12.3 Secure storage SPI

```java
public interface SecureTokenStore {
  void put(String key, byte[] secret);
  Optional<byte[]> get(String key);
  void delete(String key);
}
```

## 13. Persistence

Separate concerns:

- **Non-secret settings** (JSON): last provider, UI state, last active account id
- **Secrets**: refresh tokens + encryption keys in OS secure storage

Avoid writing refresh tokens to disk in plaintext.

## 14. Security considerations

- Use Authorization Code + PKCE (avoid implicit flow). citeturn1search9turn1search10
- Validate `state` to prevent CSRF.
- Use loopback redirect + local-only listener.
- Store secrets using OS secure storage where possible. citeturn1search11
- Treat the app as a **public client** (assume reverse engineering is possible).

## 15. Testing strategy

- Unit tests:
  - PKCE generation
  - state/nonce validation
  - token refresh scheduling

- Integration tests (optional CI secrets):
  - provider test sandboxes (mock OIDC server)

- UI tests (TestFX):
  - login dock rendering
  - device flow screen

## 16. Deliverables

### 16.1 Code deliverables (future)

- `papiflyfx-docking-login` module skeleton
- `LoginDock` JavaFX component
- `AuthSessionBroker` default implementation
- `SecureTokenStore` implementations:
  - `InMemorySecureTokenStore` (tests)
  - `FileVaultSecureTokenStore` (AES-GCM; master key in OS keychain)
  - `OsKeychainSecureTokenStore` (best effort, platform-by-platform)

### 16.2 Documentation

- This spec file
- Sample app demonstrating:
  - OIDC provider
  - GitHub device flow
  - token refresh + logout

## 17. Phased implementation plan

1. **MVP (generic OIDC)**
   - System browser login + loopback redirect
   - PKCE
   - Access token + refresh token support
   - Secure token store (file vault)

2. **Provider presets**
   - Google preset
   - GitHub preset (auth code + optional device flow)

3. **Hardening**
   - OS keychain token store
   - token revocation (where supported)

4. **UX polish**
   - multi-account switcher
   - toolbar widget

## 18. Open questions

- Do PapiflyFX apps want a shared “account” across modules (GitHub + Hugo + others), or isolated sessions per module?
- Should the login dock be a mandatory startup gate, or optional?
- What is the minimum set of providers to ship “built-in” vs community plugins?

