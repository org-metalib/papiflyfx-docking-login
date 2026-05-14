# papiflyfx-docking-login Implementation Plan (Codex)

## 1. Objective

Implement a new module `papiflyfx-docking-login` that provides a dockable authentication component for PapiflyFX applications with:

1. Docking-native login prompt and authenticated state UI.
2. Multi-provider OAuth 2.0 / OIDC support (Google, GitHub, Facebook, Apple, Amazon, and generic OIDC).
3. Session lifecycle management (sign-in, refresh, expiration, logout, account switching).
4. Secure session secret management with clear separation between secret and non-secret persistence.

## 2. Source Synthesis

This plan merges ideas from:

1. `spec/papiflyfx-docking-login/login-chatgpt.md`
2. `spec/papiflyfx-docking-login/login-gemini.md`
3. `spec/papiflyfx-docking-login/login-grok.md`

Resulting decisions:

1. Use a 4-layer structure: UI dock pane, view-model/orchestration, provider adapters, secure persistence.
2. Default to `Authorization Code + PKCE` via system browser + loopback callback.
3. Add optional `Device Flow` path for providers that support it (first target: GitHub).
4. Keep provider implementations behind a strict SPI contract.
5. Persist non-secrets in docking/session JSON; persist secrets via `SecureSecretStore`.
6. Integrate with `DockManager` through `ContentFactory` + `ContentStateAdapter`.
7. Model auth as an explicit state machine to keep UI predictable under async operations.
8. Keep WebView-based login as an optional provider-specific fallback only.

Conflict resolution from sources:

1. `login-grok.md` suggests embedding OAuth in `WebView`; `login-chatgpt.md` and `login-gemini.md` prioritize system browser. Final plan chooses system browser as default and allows a fallback strategy interface for exceptions.
2. `login-gemini.md` uses an event bus concept; repository currently has no shared event-bus utility. Final plan uses listener interfaces + JavaFX properties in v1.

## 3. Scope

### 3.1 In Scope (v1)

1. New module `papiflyfx-docking-login` with API and default implementation.
2. Dockable login pane (`LoginDockPane`) with these UI states:
   - unauthenticated provider picker
   - in-progress authentication
   - authenticated account summary
   - refresh/error status area
3. Generic OIDC provider implementation using auth-code + PKCE.
4. Provider presets for Google and GitHub.
5. Optional GitHub device flow implementation.
6. Session broker with refresh scheduling and logout/revoke hooks.
7. Secret persistence abstraction + at least:
   - in-memory implementation for tests
   - filesystem encrypted vault implementation
   - pluggable native secret store bridge
8. Content state adapter integration for restoring pane UI/session metadata (non-secret only).
9. Unit tests + focused TestFX tests.

### 3.2 Out of Scope (v1)

1. Full passkey/WebAuthn implementation.
2. Enterprise policy engine (SSO policy admin, org-wide constraints).
3. Full browser implementation in JavaFX `WebView` as primary path.
4. Centralized remote identity broker service.
5. Cross-application shared token cache.

## 4. Repository and Build Changes

## 4.1 Root Aggregator (`pom.xml`)

Add new module entry:

```xml
<modules>
    <module>papiflyfx-docking-api</module>
    <module>papiflyfx-docking-docks</module>
    <module>papiflyfx-docking-code</module>
    <module>papiflyfx-docking-tree</module>
    <module>papiflyfx-docking-media</module>
    <module>papiflyfx-docking-hugo</module>
    <module>papiflyfx-docking-login</module>
    <module>papiflyfx-docking-samples</module>
</modules>
```

## 4.2 Parent Dependency Management Additions

Add properties and dependencyManagement entries in the parent POM (versions intentionally property-driven):

```xml
<properties>
    <nimbus.oauth2.oidc.sdk.version>...</nimbus.oauth2.oidc.sdk.version>
    <jna.version>...</jna.version>
    <jackson.version>...</jackson.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.nimbusds</groupId>
            <artifactId>oauth2-oidc-sdk</artifactId>
            <version>${nimbus.oauth2.oidc.sdk.version}</version>
        </dependency>
        <dependency>
            <groupId>net.java.dev.jna</groupId>
            <artifactId>jna</artifactId>
            <version>${jna.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## 4.3 New Module `papiflyfx-docking-login/pom.xml`

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.metalib.papifly.docking</groupId>
        <artifactId>papiflyfx-docking</artifactId>
        <version>0.0.14-SNAPSHOT</version>
    </parent>

    <artifactId>papiflyfx-docking-login</artifactId>
    <name>papiflyfx-docking-login</name>
    <description>Docking-native login component for PapiflyFX.</description>

    <dependencies>
        <dependency>
            <groupId>org.metalib.papifly.docking</groupId>
            <artifactId>papiflyfx-docking-api</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.metalib.papifly.docking</groupId>
            <artifactId>papiflyfx-docking-docks</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-controls</artifactId>
            <classifier>${javafx.platform}</classifier>
        </dependency>
        <dependency>
            <groupId>com.nimbusds</groupId>
            <artifactId>oauth2-oidc-sdk</artifactId>
        </dependency>
        <dependency>
            <groupId>net.java.dev.jna</groupId>
            <artifactId>jna</artifactId>
        </dependency>

        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testfx</groupId>
            <artifactId>testfx-junit5</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

## 5. Module Layout

```text
papiflyfx-docking-login/
  src/main/java/org/metalib/papifly/fx/login/api/
    LoginDockPane.java
    LoginFactory.java
    LoginStateAdapter.java
    LoginConfig.java
    AuthSessionBroker.java
    AuthSession.java
    AuthState.java
    IdentityProvider.java
    ProviderCapabilities.java
    UserPrincipal.java

  src/main/java/org/metalib/papifly/fx/login/core/
    DefaultAuthSessionBroker.java
    LoginViewModel.java
    AuthFlowCoordinator.java
    RefreshScheduler.java
    AuthEvents.java
    AuthException.java

  src/main/java/org/metalib/papifly/fx/login/oauth/
    PkceGenerator.java
    OAuthStateStore.java
    AuthorizationUrlBuilder.java
    CallbackHttpServer.java
    TokenHttpClient.java
    IdTokenValidator.java

  src/main/java/org/metalib/papifly/fx/login/providers/
    GenericOidcProvider.java
    GitHubProvider.java
    GoogleProvider.java
    DeviceFlowClient.java

  src/main/java/org/metalib/papifly/fx/login/security/
    SecureSecretStore.java
    InMemorySecretStore.java
    FileVaultSecretStore.java
    NativeSecretStore.java
    SecretCipher.java

  src/main/java/org/metalib/papifly/fx/login/persistence/
    SessionMetadataStore.java
    PreferencesSessionMetadataStore.java
    LoginStateCodec.java

  src/main/resources/META-INF/services/
    org.metalib.papifly.fx.docking.api.ContentStateAdapter

  src/test/java/org/metalib/papifly/fx/login/
    PkceGeneratorTest.java
    CallbackHttpServerTest.java
    OAuthStateStoreTest.java
    DefaultAuthSessionBrokerTest.java
    LoginStateAdapterTest.java
    LoginDockPaneFxTest.java
```

## 6. Runtime Architecture

## 6.1 Layered Design

```text
LoginDockPane (JavaFX Node + Dock content)
  -> LoginViewModel (state + user commands)
      -> AuthSessionBroker (auth/session orchestration)
          -> IdentityProvider (provider-specific protocol)
          -> CallbackHttpServer / DeviceFlowClient (flow transport)
          -> SecureSecretStore + SessionMetadataStore (persistence)
```

## 6.2 State Machine

```java
package org.metalib.papifly.fx.login.api;

public enum AuthState {
    UNAUTHENTICATED,
    INITIATING_AUTH,
    WAITING_EXTERNAL_BROWSER,
    EXCHANGING_CODE,
    AUTHENTICATED,
    REFRESHING,
    EXPIRED,
    ERROR
}
```

Rules:

1. UI must never directly mutate auth state.
2. Only `AuthSessionBroker` transitions state.
3. Every transition emits structured auth event.
4. `ERROR` state always includes a typed error code.

## 6.3 Event Contract

```java
package org.metalib.papifly.fx.login.core;

import org.metalib.papifly.fx.login.api.AuthSession;
import org.metalib.papifly.fx.login.api.AuthState;

public sealed interface AuthEvent permits AuthEvent.StateChanged, AuthEvent.SessionChanged {
    record StateChanged(AuthState from, AuthState to, String reason) implements AuthEvent {}
    record SessionChanged(AuthSession previous, AuthSession current) implements AuthEvent {}
}
```

## 7. Public API Contracts

## 7.1 Session Broker

```java
package org.metalib.papifly.fx.login.api;

import javafx.beans.property.ReadOnlyObjectProperty;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface AuthSessionBroker {
    CompletableFuture<AuthSession> signIn(String providerId);
    CompletableFuture<AuthSession> completeCallback(String callbackUri);
    CompletableFuture<AuthSession> refresh(boolean force);
    CompletableFuture<Void> logout(boolean revokeRemote);

    Optional<AuthSession> activeSession();
    ReadOnlyObjectProperty<AuthState> authStateProperty();
    void addListener(AuthSessionListener listener);
    void removeListener(AuthSessionListener listener);
}
```

## 7.2 Identity Provider SPI

```java
package org.metalib.papifly.fx.login.api;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

public interface IdentityProvider {
    String providerId();
    String displayName();
    ProviderCapabilities capabilities();

    AuthorizationRequest buildAuthorizationRequest(
        URI redirectUri,
        String state,
        String nonce,
        String codeChallenge
    );

    TokenBundle exchangeCode(
        String code,
        URI redirectUri,
        String codeVerifier
    );

    default Optional<DeviceFlowStart> startDeviceFlow() {
        return Optional.empty();
    }

    default Optional<TokenBundle> pollDeviceFlow(DeviceFlowStart start) {
        return Optional.empty();
    }

    Optional<UserPrincipal> loadUserPrincipal(String accessToken);
    Set<String> defaultScopes();
}
```

## 7.3 Session and Principal Model

```java
package org.metalib.papifly.fx.login.api;

import java.time.Instant;
import java.util.Set;

public record AuthSession(
    String providerId,
    String subject,
    UserPrincipal principal,
    Set<String> scopes,
    String accessToken,
    String refreshTokenRef,
    Instant expiresAt,
    Instant issuedAt
) {
    public boolean isExpiringSoon(Instant now, long skewSeconds) {
        return expiresAt != null && now.plusSeconds(skewSeconds).isAfter(expiresAt);
    }
}
```

```java
package org.metalib.papifly.fx.login.api;

public record UserPrincipal(
    String subject,
    String displayName,
    String email,
    String avatarUrl
) {}
```

## 7.4 Secret and Metadata Stores

```java
package org.metalib.papifly.fx.login.security;

import java.util.Optional;

public interface SecureSecretStore {
    void put(String key, byte[] secret);
    Optional<byte[]> get(String key);
    void delete(String key);
}
```

```java
package org.metalib.papifly.fx.login.persistence;

import java.util.Optional;

public interface SessionMetadataStore {
    void save(LoginSessionMetadata metadata);
    Optional<LoginSessionMetadata> load();
    void clear();
}
```

## 8. OAuth/OIDC Flow Design

## 8.1 Authorization Code + PKCE (Default)

Flow:

1. Generate `state`, `nonce`, `codeVerifier`, `codeChallenge`.
2. Start loopback callback server on `127.0.0.1:0`.
3. Build provider authorization URL with PKCE + state.
4. Launch URL in system browser.
5. Wait for callback with timeout.
6. Validate callback state.
7. Exchange code for tokens.
8. Validate ID token (if present).
9. Resolve principal via ID token/userinfo.
10. Save refresh token in `SecureSecretStore`.
11. Publish authenticated session.

Code snippet:

```java
public CompletableFuture<AuthSession> signIn(String providerId) {
    return CompletableFuture.supplyAsync(() -> {
        IdentityProvider provider = providerRegistry.require(providerId);
        PkcePair pkce = pkceGenerator.generate();
        String state = oauthStateStore.newStateToken();
        String nonce = oauthStateStore.newNonceToken();

        CallbackSession callback = callbackHttpServer.start(Duration.ofMinutes(3));
        URI redirectUri = callback.redirectUri();

        AuthorizationRequest req = provider.buildAuthorizationRequest(
            redirectUri, state, nonce, pkce.codeChallenge()
        );
        desktopBrowserLauncher.open(req.authorizationUri());

        CallbackPayload payload = callback.awaitResult();
        oauthStateStore.validateState(payload.state());

        TokenBundle tokens = provider.exchangeCode(payload.code(), redirectUri, pkce.codeVerifier());
        UserPrincipal principal = provider.loadUserPrincipal(tokens.accessToken())
            .orElse(new UserPrincipal("unknown", "Unknown", null, null));

        return sessionAssembler.create(provider, principal, tokens);
    }, ioExecutor);
}
```

## 8.2 PKCE Utility

```java
package org.metalib.papifly.fx.login.oauth;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class PkceGenerator {
    private static final SecureRandom RNG = new SecureRandom();

    public PkcePair generate() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String challenge = s256(verifier);
        return new PkcePair(verifier, challenge, "S256");
    }

    private String s256(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("PKCE challenge generation failed", ex);
        }
    }
}
```

## 8.3 Loopback Callback Server

```java
public final class CallbackHttpServer {

    public CallbackSession start(Duration timeout) {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CompletableFuture<CallbackPayload> callbackFuture = new CompletableFuture<>();

        server.createContext("/oauth/callback", exchange -> {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String code = query.get("code");
            String state = query.get("state");
            callbackFuture.complete(new CallbackPayload(code, state));
            writeResponse(exchange, 200, "Login complete. You can close this tab.");
        });

        server.start();
        URI redirectUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/oauth/callback");
        return new CallbackSession(server, redirectUri, callbackFuture, timeout);
    }
}
```

## 8.4 Device Flow (Optional)

Applies when provider capability contains `DEVICE_FLOW`.

```java
public CompletableFuture<AuthSession> signInWithDeviceFlow(String providerId) {
    return CompletableFuture.supplyAsync(() -> {
        IdentityProvider provider = providerRegistry.require(providerId);
        DeviceFlowStart start = provider.startDeviceFlow()
            .orElseThrow(() -> new AuthException(AuthErrorCode.DEVICE_FLOW_NOT_SUPPORTED));

        uiBridge.showDeviceCode(start.verificationUri(), start.userCode(), start.expiresInSeconds());

        Instant deadline = Instant.now().plusSeconds(start.expiresInSeconds());
        while (Instant.now().isBefore(deadline)) {
            Optional<TokenBundle> maybeToken = provider.pollDeviceFlow(start);
            if (maybeToken.isPresent()) {
                return finalizeTokenSession(provider, maybeToken.get());
            }
            sleepSeconds(start.intervalSeconds());
        }
        throw new AuthException(AuthErrorCode.DEVICE_FLOW_TIMEOUT);
    }, ioExecutor);
}
```

## 9. Provider Strategy

## 9.1 Provider Capabilities

```java
public record ProviderCapabilities(
    boolean supportsAuthCodePkce,
    boolean supportsDeviceFlow,
    boolean supportsTokenRevocation,
    boolean providesOidcIdToken,
    boolean supportsUserInfoEndpoint
) {}
```

## 9.2 Provider Registry

```java
public final class ProviderRegistry {
    private final Map<String, IdentityProvider> providers = new LinkedHashMap<>();

    public void register(IdentityProvider provider) {
        providers.put(provider.providerId(), provider);
    }

    public IdentityProvider require(String providerId) {
        IdentityProvider provider = providers.get(providerId);
        if (provider == null) {
            throw new AuthException(AuthErrorCode.PROVIDER_NOT_REGISTERED, providerId);
        }
        return provider;
    }

    public List<IdentityProvider> enabledProviders() {
        return List.copyOf(providers.values());
    }
}
```

## 9.3 Built-in Presets (v1)

1. `GoogleProvider`: auth code + PKCE + OIDC claims.
2. `GitHubProvider`: auth code + optional device flow + user endpoint mapping.
3. `GenericOidcProvider`: configurable discovery metadata and scopes.

Future presets:

1. Facebook provider.
2. Apple provider.
3. Amazon provider.

## 10. UI and Docking Integration

## 10.1 Login Dock Pane

`LoginDockPane` is a JavaFX `BorderPane` that implements `DisposableContent`.

```java
public final class LoginDockPane extends BorderPane implements DisposableContent {
    private final LoginViewModel vm;
    private final ComboBox<IdentityProviderOption> providerBox = new ComboBox<>();
    private final Button signInButton = new Button("Sign in");
    private final Button refreshButton = new Button("Refresh");
    private final Button logoutButton = new Button("Logout");
    private final Label statusLabel = new Label();

    public LoginDockPane(LoginViewModel vm) {
        this.vm = vm;
        setTop(buildToolbar());
        setCenter(buildContent());
        bindState();
    }

    private void bindState() {
        statusLabel.textProperty().bind(vm.statusTextProperty());
        signInButton.disableProperty().bind(vm.busyProperty());
        refreshButton.disableProperty().bind(vm.notAuthenticatedProperty());
        logoutButton.disableProperty().bind(vm.notAuthenticatedProperty());
    }

    @Override
    public void dispose() {
        vm.dispose();
    }
}
```

## 10.2 Dock Content Factory

```java
public final class LoginFactory implements ContentFactory {
    public static final String FACTORY_ID = "login-dock";
    private final Supplier<LoginDockPane> paneSupplier;

    public LoginFactory(Supplier<LoginDockPane> paneSupplier) {
        this.paneSupplier = paneSupplier;
    }

    @Override
    public Node create(String factoryId) {
        return FACTORY_ID.equals(factoryId) ? paneSupplier.get() : null;
    }
}
```

## 10.3 Content State Adapter (Non-secret only)

```java
public final class LoginStateAdapter implements ContentStateAdapter {

    @Override
    public String getTypeKey() {
        return LoginFactory.FACTORY_ID;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Map<String, Object> saveState(String contentId, Node content) {
        if (!(content instanceof LoginDockPane pane)) {
            return Map.of();
        }
        LoginDockState state = pane.captureDockState();
        return LoginStateCodec.toMap(state);
    }

    @Override
    public Node restore(LeafContentData content) {
        LoginDockState state = LoginStateCodec.fromMap(content.state());
        return LoginBootstrap.restorePane(state, content.contentId());
    }
}
```

## 10.4 ServiceLoader Registration

`src/main/resources/META-INF/services/org.metalib.papifly.fx.docking.api.ContentStateAdapter`:

```text
org.metalib.papifly.fx.login.api.LoginStateAdapter
```

## 10.5 Application Startup Pattern

```java
DockManager dm = DockManager.create()
    .withTheme(Theme.dark())
    .withContentFactory(new LoginFactory(() -> loginPaneFactory.createDefault()))
    .build();

ContentStateRegistry registry = ContentStateRegistry.fromServiceLoader();
dm.setContentStateRegistry(registry);

DockTabGroup group = dm.createTabGroup();
group.addLeaf(dm.createLeaf("Sign In", loginPaneFactory.createDefault()));
dm.setRoot(group);
```

## 11. Session Lifecycle and Account Management

## 11.1 Refresh Policy

1. On any API consumer token request, if token is near expiry, trigger refresh.
2. Scheduler proactively refreshes before expiry (default skew: 90 seconds).
3. Refresh failure transitions to `EXPIRED`, then `UNAUTHENTICATED` if not recoverable.

```java
public final class RefreshScheduler {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public void schedule(AuthSession session, Runnable refreshAction) {
        long secondsUntilRefresh = Duration.between(Instant.now(), session.expiresAt())
            .minusSeconds(90)
            .toSeconds();
        long delay = Math.max(5, secondsUntilRefresh);
        scheduler.schedule(refreshAction, delay, TimeUnit.SECONDS);
    }
}
```

## 11.2 Multi-account Data Model

```java
public record SessionKey(String providerId, String subject) {}

public final class SessionCatalog {
    private final Map<SessionKey, AuthSession> sessions = new LinkedHashMap<>();
    private SessionKey activeKey;

    public void upsert(AuthSession session) {
        SessionKey key = new SessionKey(session.providerId(), session.subject());
        sessions.put(key, session);
        activeKey = key;
    }

    public Optional<AuthSession> active() {
        return Optional.ofNullable(activeKey).map(sessions::get);
    }
}
```

## 11.3 Logout and Revocation

Flow:

1. Best-effort provider revocation if enabled.
2. Remove refresh token from `SecureSecretStore`.
3. Clear access token from memory/session catalog.
4. Persist unauthenticated metadata state.
5. Emit `SESSION_CHANGED` + `STATE_CHANGED`.

## 12. Secret Management Plan

## 12.1 Persistence Split

1. `SecureSecretStore`:
   - refresh token
   - vault master key
2. `SessionMetadataStore`:
   - provider id
   - subject
   - display name/email/avatar
   - granted scopes
   - expiresAt
   - last selected provider

## 12.2 Key Naming Convention

```java
public final class SecretKeyNames {
    public static String refreshTokenKey(String appId, String providerId, String subject) {
        return appId + ":oauth:refresh:" + providerId + ":" + subject;
    }

    public static String vaultKey(String appId) {
        return appId + ":vault:key";
    }
}
```

## 12.3 File Vault Encryption

```java
public final class SecretCipher {
    public byte[] encrypt(byte[] plaintext, SecretKey key) {
        byte[] iv = secureRandomBytes(12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);
        return ByteBuffer.allocate(1 + iv.length + ciphertext.length)
            .put((byte) iv.length)
            .put(iv)
            .put(ciphertext)
            .array();
    }
}
```

## 12.4 Memory Hygiene

Use mutable buffers for sensitive values and zero them after use:

```java
char[] verifier = buildVerifierChars();
try {
    tokenClient.exchange(code, verifier);
} finally {
    Arrays.fill(verifier, '\0');
}
```

## 13. Persistence and Restore Semantics

## 13.1 What Gets Serialized into Dock Session

Allowed:

1. `factoryId` / `typeKey` / `contentId`.
2. Login pane UI mode (`compact`, `expanded`).
3. Last selected provider id.
4. Active session metadata id pointer (`providerId + subject`) without secret.

Forbidden:

1. Access token.
2. Refresh token.
3. ID token raw payload.
4. Provider client secret.

## 13.2 Session Metadata DTO

```java
public record LoginSessionMetadata(
    String providerId,
    String subject,
    String displayName,
    String email,
    String avatarUrl,
    Set<String> scopes,
    Instant expiresAt,
    String lastSelectedProvider
) {}
```

## 14. Error Taxonomy and UX Behavior

## 14.1 Error Codes

```java
public enum AuthErrorCode {
    NETWORK_ERROR,
    USER_CANCELLED,
    CALLBACK_TIMEOUT,
    STATE_MISMATCH,
    TOKEN_EXCHANGE_FAILED,
    TOKEN_VALIDATION_FAILED,
    REFRESH_FAILED,
    PROVIDER_NOT_REGISTERED,
    DEVICE_FLOW_NOT_SUPPORTED,
    DEVICE_FLOW_TIMEOUT,
    SECRET_STORE_FAILURE
}
```

## 14.2 Exception Wrapper

```java
public final class AuthException extends RuntimeException {
    private final AuthErrorCode code;

    public AuthException(AuthErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public AuthException(AuthErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AuthErrorCode code() {
        return code;
    }
}
```

## 14.3 UI Mapping Rules

1. `NETWORK_ERROR`: show retry button and provider status text.
2. `USER_CANCELLED`: clear busy state, remain unauthenticated.
3. `STATE_MISMATCH`: hard error + clear callback listener + force new sign-in.
4. `REFRESH_FAILED`: degrade session, keep UI open for re-auth.
5. `SECRET_STORE_FAILURE`: allow in-memory session continuation but show warning banner.

## 15. Security Hardening Checklist (Implementation Tasks)

1. PKCE generation uses `SecureRandom`.
2. `state` is mandatory and validated on callback.
3. `nonce` is generated and validated for OIDC providers.
4. Callback listener binds only to `127.0.0.1` and uses random OS-assigned port.
5. Callback listener has strict timeout and shuts down after completion.
6. Token endpoint and userinfo requests reject non-HTTPS endpoints.
7. ID token validation includes issuer, audience, expiration, nonce.
8. No secrets are logged; structured logs redact token-like fields.
9. Session IDs use high-entropy random values.
10. Refresh failures include bounded retry with backoff.

## 16. Detailed Implementation Phases

## 16.1 Phase 1 - Module Scaffolding and Contracts

Deliverables:

1. New module skeleton with package structure.
2. Core interfaces (`AuthSessionBroker`, `IdentityProvider`, stores).
3. Initial `LoginDockPane` with placeholder actions.
4. `LoginFactory` + `LoginStateAdapter`.

Acceptance criteria:

1. Module compiles in aggregator.
2. `DockManager` can host `LoginDockPane`.
3. Session capture/restore works for non-secret state.

## 16.2 Phase 2 - PKCE Auth Code Flow

Deliverables:

1. `PkceGenerator`, `OAuthStateStore`, `CallbackHttpServer`.
2. `DefaultAuthSessionBroker.signIn(...)` for auth-code flow.
3. `GenericOidcProvider` implementation.

Acceptance criteria:

1. Local mocked OIDC flow succeeds end to end.
2. State mismatch path is correctly rejected.
3. Timeout path transitions to `ERROR` with code `CALLBACK_TIMEOUT`.

## 16.3 Phase 3 - Secret Persistence and Refresh

Deliverables:

1. `FileVaultSecretStore` + `InMemorySecretStore`.
2. Refresh scheduler and refresh path.
3. Metadata store and startup restore behavior.

Acceptance criteria:

1. Restart restores metadata and can refresh silently.
2. Secret data never appears in serialized `DockSessionData`.
3. Forced logout clears secret store references.

## 16.4 Phase 4 - Provider Presets and Device Flow

Deliverables:

1. `GoogleProvider`.
2. `GitHubProvider` with optional device flow.
3. Provider registration API for host applications.

Acceptance criteria:

1. Provider list renders dynamically based on registry.
2. Device flow UI (verification URI + code + countdown) works.
3. Broker can switch between provider sessions.

## 16.5 Phase 5 - UX and Diagnostics Polish

Deliverables:

1. Improved status components (expiry countdown, active scopes, user summary).
2. Error panel with retry and diagnostics.
3. Structured logging and metrics hooks.

Acceptance criteria:

1. UI remains responsive through all auth transitions.
2. No blocking network operation on JavaFX thread.
3. Logs are useful and token-safe.

## 17. Testing Plan

## 17.1 Unit Tests

1. PKCE verifier/challenge generation.
2. OAuth state store generation/validation.
3. Callback query parser and timeout handling.
4. Refresh scheduling timing logic.
5. Session catalog multi-account behavior.
6. Secret key naming determinism.
7. Redaction behavior in logs.

Example:

```java
@Test
void pkceChallengeIsUrlSafeAndDeterministicForVerifier() {
    PkceGenerator gen = new PkceGenerator();
    PkcePair pair = gen.generate();

    assertNotNull(pair.codeVerifier());
    assertNotNull(pair.codeChallenge());
    assertFalse(pair.codeChallenge().contains("+"));
    assertFalse(pair.codeChallenge().contains("/"));
}
```

## 17.2 Integration Tests

1. Mock OIDC server:
   - authorization redirect
   - token exchange
   - refresh exchange
2. Secret store roundtrip tests.
3. Metadata restore tests after broker restart.

## 17.3 TestFX Coverage

1. `LoginDockPane` state transitions:
   - unauthenticated -> busy -> authenticated
   - authenticated -> refreshing -> authenticated
   - error path rendering
2. Provider selection and command button enablement.
3. Device flow panel rendering and countdown behavior.

## 17.4 Negative Security Tests

1. Callback with missing state.
2. Callback with wrong state.
3. Token response missing required fields.
4. Expired ID token.
5. Invalid issuer/audience.

## 18. Integration Example for Host Applications

```java
ProviderRegistry providers = new ProviderRegistry();
providers.register(new GoogleProvider(googleConfig));
providers.register(new GitHubProvider(githubConfig));

SecureSecretStore secrets = new FileVaultSecretStore(appDataDir, nativeSecretStore);
SessionMetadataStore metadata = new PreferencesSessionMetadataStore(appPreferences);

AuthSessionBroker broker = new DefaultAuthSessionBroker(
    providers,
    secrets,
    metadata,
    new CallbackHttpServer(),
    Executors.newCachedThreadPool()
);

LoginViewModel vm = new LoginViewModel(broker, providers);
LoginDockPane pane = new LoginDockPane(vm);
```

## 19. Risks and Mitigations

1. Risk: Provider-specific edge cases around callback parameters.
   Mitigation: Keep provider adapters isolated; add provider contract tests.
2. Risk: Secret storage backend unavailable at runtime.
   Mitigation: Fallback to encrypted file vault with explicit warning surface.
3. Risk: Token refresh race conditions from concurrent API calls.
   Mitigation: Single-flight refresh lock per active session.
4. Risk: UI freezes during network or browser launch operations.
   Mitigation: Dedicated IO executor; strict JavaFX-thread boundaries.
5. Risk: Session restoration chooses stale account after multi-account usage.
   Mitigation: persist explicit active account key and validate on startup.

## 20. Definition of Done

1. Module builds and tests pass in local Maven run.
2. Docking sample can open login dock, authenticate, refresh, and logout.
3. Session metadata restores across restart; secrets stay out of JSON session files.
4. Provider SPI allows adding a new provider with no broker changes.
5. Documentation includes usage examples for:
   - registering providers
   - wiring broker to `LoginDockPane`
   - restoring state with `ContentStateAdapter`
6. Security checklist items in Section 15 are implemented and test-covered where feasible.

## 21. Future Enhancements

1. Optional policy hooks for per-action re-auth.
2. Authenticated role/claim mapping to dock visibility.
3. Passkey-first providers where available.
4. External secret manager plugin contracts.
5. Admin telemetry endpoint for enterprise diagnostics.
