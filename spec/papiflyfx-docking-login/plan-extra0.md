# papiflyfx-docking-login — Additional Items from Settings Module Integration

Items not covered in `plan.md` that arise when the settings module (`papiflyfx-docking-settings` + `papiflyfx-docking-settings-api`) is taken into account.

---

## 1. Replace `SecureTokenStore` with Unified `SecretStore`

The login plan defines its own `SecureTokenStore` interface (§4: `api/SecureTokenStore.java`) and three implementations (`InMemorySecureTokenStore`, `OsKeychainSecureTokenStore`, `FileVaultSecureTokenStore`). The settings module already provides an equivalent unified `SecretStore` with the same three implementation tiers (`InMemorySecretStore`, `KeychainSecretStore`, `EncryptedFileSecretStore`) plus `SecretStoreFactory` for platform detection.

**Action**: Drop `SecureTokenStore` and its implementations from the login module. Accept a `SecretStore` via constructor injection. Use `SecretStoreSecureAdapter` (settings plan §5.8) if the login module's internal code needs a `byte[]`-oriented API.

**Impact**: Eliminates ~4 classes from the login module; avoids duplicating OS keychain and AES-GCM cipher code.

---

## 2. Replace `SessionMetadataStore` / `PreferencesSessionMetadataStore` with `SettingsStorage`

The login plan (§4: `api/SessionMetadataStore.java`, `persistence/PreferencesSessionMetadataStore.java`) defines a separate persistence layer for non-secret session metadata (provider ID, subject, display name, scopes, expiry). The settings module's `SettingsStorage` already provides scoped key-value persistence with JSON backing.

**Action**: Store session metadata as scoped settings keys:
- `login.active-provider` (APPLICATION scope)
- `login.session.<providerId>.<subject>.display-name` (APPLICATION scope)
- `login.session.<providerId>.<subject>.scopes` (APPLICATION scope)
- `login.session.<providerId>.<subject>.expiry` (SESSION scope — transient)

**Impact**: Eliminates `SessionMetadataStore` interface and `PreferencesSessionMetadataStore`. Metadata participates in settings scope resolution (SESSION > WORKSPACE > APPLICATION) and schema migration.

---

## 3. Follow `SecretKeyNames` Convention

The login plan stores refresh tokens under ad-hoc keys. The settings module defines a hierarchical colon-separated namespace (`<module>:<category>:<id>`) with a `SecretKeyNames` utility class.

**Action**: Use the pre-defined naming pattern:
- `login:oauth:refresh:<providerId>:<subject>` — refresh tokens
- `login:vault:key` — vault master key (if encrypted file fallback is used)
- `login:device:<providerId>:pending` — transient device-flow state (SESSION scope in `SettingsStorage`, not in `SecretStore`)

**Impact**: Ensures stored login secrets appear correctly in the Security settings category's key inventory and avoids key collisions with other modules.

---

## 4. Contribute `AuthenticationCategory` via `SettingsCategory` SPI

The login plan has no mention of a settings panel integration. The settings module's SPI (`SettingsCategory` + ServiceLoader) allows the login module to surface its configuration in the central settings UI.

**Action**: Add `AuthenticationCategory implements SettingsCategory` in the login module:
- **Provider list** — enable/disable toggles per identity provider, with per-provider sub-settings (client ID, custom scopes, discovery URL for `GenericOidcProvider`, GitHub Enterprise API URL).
- **Active session summary** — bound to `AuthSessionBroker.sessionProperty()`: user name, email, avatar, provider, scopes, expiry countdown.
- **Stored token inventory** — list refresh tokens from `SecretStore.listKeys()` filtered by `login:oauth:refresh:*`; actions to revoke/delete.
- **Action buttons** — "Sign In", "Refresh Token", "Logout", "Switch Account" as `SettingsAction` instances with async handlers.
- **Sort order**: `order() = 25` (between Appearance at 10 and Editor/GitHub at higher values).

**Impact**: New package `org.metalib.papifly.fx.login.settings` with `AuthenticationCategory.java` + ServiceLoader registration in `META-INF/services/org.metalib.papifly.fx.settings.api.SettingsCategory`.

---

## 5. Use `SettingDefinition` for Declarative Provider Configuration

The login plan hardcodes provider configuration in each `IdentityProvider` implementation. Settings module's `SettingDefinition<T>` record enables declarative field definitions with typed defaults, validation, and automatic UI generation.

**Action**: Each built-in provider exposes its configurable fields as `List<SettingDefinition<?>>`:
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

**Impact**: `AuthenticationCategory.buildSettingsPane()` can use `SettingControlFactory.createControl()` for automatic form generation rather than hand-rolling controls.

---

## 6. Use `SettingsValidator` for Provider Configuration Validation

The login plan defines `AuthErrorCode` for runtime auth errors but has no pre-flight validation for provider configuration fields.

**Action**: Attach `SettingsValidator<T>` to provider `SettingDefinition`s:
- URL format validation for discovery/endpoint URLs
- Non-blank validation for required client IDs
- Scope syntax validation (space-separated, no special characters)
- "Test Connection" as a `SettingsAction` that attempts OIDC discovery fetch and returns `ValidationResult`

**Impact**: Users get inline validation feedback in the settings panel before attempting authentication.

---

## 7. Use `SettingsAction` for Login Operations in Settings Panel

The login plan's session actions (sign-in, refresh, logout, revoke) are broker API methods. The settings module's `SettingsAction` record bridges these to the settings UI as async action buttons.

**Action**: Register in `AuthenticationCategory.actions()`:
- `"Test Connection"` — verifies provider reachability, returns success/error `ValidationResult`
- `"Refresh Token"` — calls `broker.refresh(true)`, reports result
- `"Logout"` — calls `broker.logout(revokeRemote)`, clears session display
- `"Revoke All Tokens"` — iterates `SecretStore.listKeys()` for `login:oauth:refresh:*` prefix, clears each

**Impact**: All login actions accessible from the centralized settings panel without requiring the login dock pane to be open.

---

## 8. Use `SettingsMigrator` for Login Settings Schema Versioning

The login plan stores provider configurations and session metadata but has no versioning or migration strategy for its persisted data format.

**Action**: Register a `SettingsMigrator` for the `login` namespace. Example migrations:
- v1 → v2: rename `login.oauth.refresh.*` keys to `login:oauth:refresh:*` (colon-separated)
- v2 → v3: add `login.session.<provider>.<subject>.avatar-url` field

**Impact**: Login settings evolve safely across application updates without data loss.

---

## 9. Scoped Provider Configuration (APPLICATION vs WORKSPACE)

The login plan treats all provider configuration as global. The settings module's `SettingScope` enables workspace-level overrides.

**Action**: Support per-workspace identity provider overrides:
- APPLICATION scope: default providers and client IDs (personal machine-wide config)
- WORKSPACE scope: project-specific overrides (e.g., different GitHub Enterprise URL per repo, different Google workspace domain)
- SESSION scope: transient auth state (current access token expiry, auth-in-progress flag)

**Impact**: Users working across multiple organizations or repos can have different login configurations per workspace without manual switching.

---

## 10. Security Settings Category Integration

The settings module's built-in `SecurityCategory` lists all stored secret keys. Login module's refresh tokens should integrate cleanly.

**Action**:
- Ensure login tokens follow `SecretKeyNames` convention so `SecurityCategory` displays them under a recognizable `login:oauth:refresh:*` group.
- Provide human-readable labels: `SecurityCategory` can parse the key pattern to show "Google — user@gmail.com" instead of raw `login:oauth:refresh:google:user@gmail.com`.
- Honor delete actions from `SecurityCategory` — when a user deletes a refresh token key from the security panel, the login module's broker should detect the missing token and transition to `EXPIRED` or `UNAUTHENTICATED` on next refresh attempt.

**Impact**: Single pane of glass for all stored secrets, including OAuth tokens.

---

## 11. Settings Search Integration

The settings module provides a search bar that filters across all categories by `SettingDefinition` labels and descriptions.

**Action**: Ensure `AuthenticationCategory.definitions()` returns the full list of provider `SettingDefinition`s so they are indexed by the settings search. Search terms like "OAuth", "Google", "client ID", "refresh token" should surface the Authentication category.

**Impact**: Consistent discoverability of login-related settings alongside all other settings.

---

## 12. Dependency Direction Clarification

The login plan (§5.4) lists dependencies but does not account for the settings module.

**Action**: Add compile-scope dependencies:
```xml
<!-- in papiflyfx-docking-login/pom.xml -->
<dependency>
    <groupId>org.metalib.papifly.docking</groupId>
    <artifactId>papiflyfx-docking-settings-api</artifactId>
    <version>${project.version}</version>
</dependency>
```

Remove from login plan:
- `com.github.javakeyring:java-keyring` — no longer needed; `SecretStore` implementations live in settings module
- `com.fasterxml.jackson.core:jackson-databind` — not needed if using `SettingsStorage` (Map-based JSON, no external library)

**Impact**: Login module becomes thinner. Secret storage and non-secret persistence are fully delegated to the settings module.

---

## 13. Remove Redundant `security/` Package from Login Module

The login plan (§4) defines a `security/` package with `InMemorySecureTokenStore`, `OsKeychainSecureTokenStore`, `FileVaultSecureTokenStore`, `SecretCipher`. All of these are provided by the settings module's `secret/` package.

**Action**: Delete the entire `security/` package from the login plan. Replace with a thin `SecretStoreSecureAdapter` usage if the `byte[]` API is still needed internally.

**Impact**: Removes 4 classes and their associated tests from the login module.

---

## 14. Remove `persistence/` Package — Use Settings Infrastructure

The login plan (§4) defines `persistence/PreferencesSessionMetadataStore.java` and `persistence/LoginStateCodec.java`.

**Action**:
- `PreferencesSessionMetadataStore` → replaced by `SettingsStorage` (item 2 above)
- `LoginStateCodec` → replaced by standard `Map<String, Object>` serialization via `SettingsStorage.putMap()` / `getMap()` and the existing `ContentStateAdapter` pattern

**Impact**: Removes 2 classes. Login state persistence uses the same infrastructure as all other modules.

---

## 15. `nimbus-jose-jwt` / `oauth2-oidc-sdk` Remains Login-Specific

The OAuth/OIDC protocol handling (`PkceGenerator`, `LoopbackCallbackServer`, `IdTokenValidator`, provider implementations) is genuinely login-specific and has no equivalent in the settings module.

**No action required** — these remain in the login module's `oauth/` and `providers/` packages. The settings module handles storage; the login module handles protocol.

---

## Summary — What Changes in the Login Plan

| Login Plan Section | Change |
|---|---|
| §4 `api/SecureTokenStore.java` | Remove — use `SecretStore` from settings-api |
| §4 `api/SessionMetadataStore.java` | Remove — use `SettingsStorage` from settings-api |
| §4 `security/*` (4 classes) | Remove — use settings module's `SecretStore` implementations |
| §4 `persistence/*` (2 classes) | Remove — use `SettingsStorage` + `ContentStateAdapter` |
| §5 Dependencies | Drop `java-keyring`, `jackson-databind`; add `papiflyfx-docking-settings-api` |
| §9 Persistence | Delegate to `SettingsStorage` + `SecretStore` (no custom persistence layer) |
| §10 Docking Integration | Add `AuthenticationCategory` via `SettingsCategory` SPI |
| (new) Provider config | Declarative `SettingDefinition`s with validators |
| (new) Settings actions | `SettingsAction` wrappers for sign-in/refresh/logout |
| (new) Schema migration | `SettingsMigrator` for login settings namespace |
| (new) Scope support | APPLICATION / WORKSPACE / SESSION scoping for provider config |
