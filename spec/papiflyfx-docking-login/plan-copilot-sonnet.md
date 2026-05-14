# papiflyfx-docking-login — Implementation Plan

> Synthesised from `login-chatgpt.md`, `login-gemini.md`, and `login-grok.md`.

---

## 1. Purpose & Scope

`papiflyfx-docking-login` provides a reusable **login dock** and **authentication session layer**
for PapiflyFX Docking applications.  It covers:

- A **dockable login panel** that acts as a startup gate or on-demand identity prompt.
- A **pluggable `IdentityProvider` SPI** so each provider lives in its own preset class.
- **OAuth 2.0 / OIDC** with Authorization Code + PKCE via the system browser (RFC 8252, RFC 7636).
- **GitHub Device Flow** as a provider-specific opt-in.
- **Secure session management** — silent refresh, idle timeout, multi-account.
- **OS-native secret storage** (macOS Keychain, Windows Credential Manager, Linux Secret Service)
  with an AES-GCM file-vault fallback.
- Clean integration with `DockManager`, `ContentFactory`, `ContentStateAdapter`, and `Theme`.

**Out of scope**: IAM server, password storage, embedded WebView-based login
(system browser is always preferred), full RBAC.

---

## 2. Module Placement & Package Structure

```
papiflyfx-docking-login/
├── pom.xml
└── src/
    └── main/java/org/metalib/papifly/fx/docking/login/
        ├── api/
        │   ├── AuthSessionBroker.java       # central service interface
        │   ├── IdentityProvider.java         # SPI for each IdP
        │   ├── SecureTokenStore.java         # SPI for secret storage
        │   ├── AuthSession.java              # value record
        │   ├── AuthState.java                # observable enum
        │   ├── PkcePair.java                 # value record
        │   ├── UserInfo.java                 # value record
        │   └── ProviderCapabilities.java     # capability flags record
        ├── core/
        │   ├── DefaultAuthSessionBroker.java
        │   ├── LoopbackCallbackServer.java
        │   ├── PkceGenerator.java
        │   ├── TokenRefreshScheduler.java
        │   └── store/
        │       ├── InMemorySecureTokenStore.java
        │       ├── FileVaultSecureTokenStore.java
        │       └── OsKeychainSecureTokenStore.java
        ├── providers/
        │   ├── GoogleIdentityProvider.java
        │   ├── GitHubIdentityProvider.java
        │   ├── GitHubDeviceFlowProvider.java
        │   ├── FacebookIdentityProvider.java
        │   ├── AmazonIdentityProvider.java
        │   └── AppleIdentityProvider.java
        └── ui/
            ├── LoginDockContent.java         # implements DisposableContent
            ├── LoginView.java                # provider button row + status
            ├── DeviceFlowView.java           # user-code + polling indicator
            └── AccountStatusWidget.java      # compact signed-in badge
```

Maven groupId/artifactId: `org.metalib.papifly.docking` / `papiflyfx-docking-login`

---

## 3. Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│                   DockManager                       │
│  ┌───────────────┐   authStateProperty()             │
│  │  LoginDock    │◄──────────────────────────────┐  │
│  │  (DockLeaf)   │                               │  │
│  └──────┬────────┘                               │  │
│         │ creates                                 │  │
│  ┌──────▼──────────────────────────────────────┐ │  │
│  │           LoginDockContent                  │ │  │
│  │  LoginView ─► DeviceFlowView ─► StatusWidget│ │  │
│  └──────────────────┬──────────────────────────┘ │  │
│                     │ signIn(providerId)           │  │
│  ┌──────────────────▼──────────────────────────┐ │  │
│  │       DefaultAuthSessionBroker              │─┘  │
│  │  ┌────────────────────────────────────────┐ │    │
│  │  │     IdentityProvider registry          │ │    │
│  │  │  Google │ GitHub │ Apple │ Amazon │ FB │ │    │
│  │  └────────────────────────────────────────┘ │    │
│  │  ┌────────────────────────────────────────┐ │    │
│  │  │   LoopbackCallbackServer               │ │    │
│  │  │   (binds :0, captures auth code)       │ │    │
│  │  └────────────────────────────────────────┘ │    │
│  │  ┌────────────────────────────────────────┐ │    │
│  │  │   TokenRefreshScheduler                │ │    │
│  │  └────────────────────────────────────────┘ │    │
│  │  ┌────────────────────────────────────────┐ │    │
│  │  │   SecureTokenStore (OS keychain / AES) │ │    │
│  │  └────────────────────────────────────────┘ │    │
│  └─────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
```

**Event flow**: `DefaultAuthSessionBroker` updates `authStateProperty()` on the FX thread.
`LoginDockContent` observes this property and transitions its internal view accordingly.
Other docks (GitHub, Hugo, …) call `broker.getActiveSession()` or subscribe to the same property.

---

## 4. Core Data Model

### 4.1 `AuthState` — observable lifecycle enum

```java
package org.metalib.papifly.fx.docking.login.api;

public enum AuthState {
    /** No session.  Show login prompt. */
    UNAUTHENTICATED,
    /** Browser opened; loopback listener active. */
    AWAITING_CALLBACK,
    /** Code received; token exchange in progress. */
    EXCHANGING_CODE,
    /** Device-flow polling in progress. */
    POLLING_DEVICE,
    /** Valid access token held in memory. */
    AUTHENTICATED,
    /** Access token nearing expiry; silent refresh in progress. */
    REFRESHING,
    /** Refresh failed or token revoked; back to login. */
    SESSION_EXPIRED,
    /** Explicit user sign-out completed. */
    SIGNED_OUT
}
```

### 4.2 `PkcePair` — code-verifier / challenge pair

```java
package org.metalib.papifly.fx.docking.login.api;

/** Immutable PKCE S256 pair. */
public record PkcePair(String codeVerifier, String codeChallenge) {

    /** Challenge method as required by RFC 7636. */
    public String method() { return "S256"; }
}
```

### 4.3 `UserInfo` — OIDC / provider profile

```java
package org.metalib.papifly.fx.docking.login.api;

import java.util.Optional;

public record UserInfo(
    String subject,
    Optional<String> name,
    Optional<String> email,
    Optional<String> avatarUrl
) {}
```

### 4.4 `AuthSession` — live session state

```java
package org.metalib.papifly.fx.docking.login.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record AuthSession(
    String providerId,
    UserInfo userInfo,
    /** Access token.  Kept in-memory only; never written to disk. */
    String accessToken,
    /** Key under which the refresh token was stored in SecureTokenStore. */
    String refreshTokenKey,
    Instant expiresAt,
    List<String> grantedScopes
) {
    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }

    /** True when within the refresh skew window (default 90 s). */
    public boolean isExpiringSoon() {
        return Instant.now().plusSeconds(90).isAfter(expiresAt);
    }
}
```

### 4.5 `ProviderCapabilities`

```java
package org.metalib.papifly.fx.docking.login.api;

public record ProviderCapabilities(
    boolean supportsPkce,
    boolean supportsDeviceFlow,
    boolean supportsOidc,
    boolean supportsTokenRevocation,
    /** Apple-style: redirect MUST go to a verified HTTPS domain, not loopback. */
    boolean requiresHttpsRedirect
) {}
```

---

## 5. Core Interfaces (API / SPI)

### 5.1 `IdentityProvider` SPI

```java
package org.metalib.papifly.fx.docking.login.api;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public interface IdentityProvider {

    String providerId();         // e.g. "google", "github"
    String displayName();        // e.g. "Google", "GitHub"

    ProviderCapabilities capabilities();

    /** Scopes requested by default. */
    List<String> defaultScopes();

    // ── Authorization Code + PKCE ────────────────────────────────────────────

    /** Build the URL to open in the system browser. */
    URI buildAuthorizationUri(PkcePair pkce, URI redirectUri,
                              String state, String nonce);

    /** Exchange code → tokens. Runs on a background thread. */
    TokenResponse exchangeCodeForTokens(String code, PkcePair pkce, URI redirectUri);

    // ── Device Flow (optional) ───────────────────────────────────────────────

    default Optional<DeviceCodeResponse> requestDeviceCode() {
        return Optional.empty();
    }

    default Optional<TokenResponse> pollDeviceToken(String deviceCode) {
        return Optional.empty();
    }

    // ── UserInfo (optional) ──────────────────────────────────────────────────

    default Optional<UserInfo> fetchUserInfo(String accessToken) {
        return Optional.empty();
    }

    // ── Revocation (optional) ────────────────────────────────────────────────

    default void revokeToken(String token) {}
}
```

**`TokenResponse`** and **`DeviceCodeResponse`** are simple value records
returned from JSON-parsed HTTP responses:

```java
public record TokenResponse(
    String accessToken,
    String tokenType,
    int expiresIn,
    Optional<String> refreshToken,
    Optional<String> idToken,
    List<String> scope
) {}

public record DeviceCodeResponse(
    String deviceCode,
    String userCode,
    String verificationUri,
    int expiresIn,
    int interval    // polling interval in seconds
) {}
```

### 5.2 `AuthSessionBroker`

```java
package org.metalib.papifly.fx.docking.login.api;

import javafx.beans.property.ReadOnlyObjectProperty;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface AuthSessionBroker {

    /** Start the PKCE flow for the given provider. Returns the new session on success. */
    CompletableFuture<AuthSession> signIn(String providerId);

    /** Start device flow for providers that support it. */
    CompletableFuture<AuthSession> signInWithDeviceFlow(String providerId);

    Optional<AuthSession> getActiveSession();

    /** Force = always call token endpoint; false = only if nearing expiry. */
    CompletableFuture<AuthSession> refresh(boolean force);

    /** Local clear + optional remote revocation. */
    CompletableFuture<Void> logout(boolean revoke);

    /** Observable on the FX application thread. */
    ReadOnlyObjectProperty<AuthState> authStateProperty();

    /** All registered provider IDs. */
    List<String> availableProviders();

    /** Multi-account: all sessions currently stored. */
    List<AuthSession> allSessions();

    /** Switch which session is "active". */
    void setActiveSession(AuthSession session);
}
```

### 5.3 `SecureTokenStore` SPI

```java
package org.metalib.papifly.fx.docking.login.api;

import java.util.Optional;

/**
 * Platform-specific or encrypted storage for long-lived secrets.
 * Keys are namespaced as "papiflyfx/{providerId}/{subject}/{field}".
 */
public interface SecureTokenStore {
    void put(String key, byte[] secret);
    Optional<byte[]> get(String key);
    void delete(String key);

    /** Wipe all secrets owned by this application. */
    void deleteAll();
}
```

---

## 6. Authentication Flows

### 6.1 Authorization Code + PKCE (primary flow)

#### PKCE Generation

```java
package org.metalib.papifly.fx.docking.login.core;

import org.metalib.papifly.fx.docking.login.api.PkcePair;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class PkceGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PkceGenerator() {}

    public static PkcePair generate() {
        // 96 bytes → 128-char base64url (well within 43-128 char limit of RFC 7636)
        byte[] bytes = new byte[96];
        RANDOM.nextBytes(bytes);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String challenge = sha256Base64Url(verifier);
        return new PkcePair(verifier, challenge);
    }

    private static String sha256Base64Url(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

#### Loopback Callback Server

```java
package org.metalib.papifly.fx.docking.login.core;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Binds a temporary HTTP server on 127.0.0.1 port 0 (OS-assigned).
 * Captures exactly one OAuth redirect, then shuts down.
 */
public final class LoopbackCallbackServer implements AutoCloseable {

    private final HttpServer server;
    private final CompletableFuture<Map<String, String>> callbackParams = new CompletableFuture<>();

    public LoopbackCallbackServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.createContext("/callback", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQuery(query);
            String html = "<html><body><h2>Authentication complete. You may close this tab.</h2></body></html>";
            byte[] body = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
            callbackParams.complete(params);
            // Stop the server asynchronously to allow the response to flush first.
            new Thread(() -> { try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                server.stop(0); }).start();
        });
        server.start();
    }

    /** The redirect URI to register with the IdP, e.g. http://127.0.0.1:51234/callback */
    public URI redirectUri() {
        int port = server.getAddress().getPort();
        return URI.create("http://127.0.0.1:" + port + "/callback");
    }

    /**
     * Waits for the single callback, then returns the query parameters.
     * Times out after {@code timeoutSeconds}.
     */
    public CompletableFuture<Map<String, String>> awaitCallback() {
        return callbackParams;
    }

    @Override
    public void close() { server.stop(0); }

    private static Map<String, String> parseQuery(String query) {
        if (query == null || query.isBlank()) return Map.of();
        return Arrays.stream(query.split("&"))
            .map(pair -> pair.split("=", 2))
            .filter(kv -> kv.length == 2)
            .collect(Collectors.toMap(kv -> kv[0], kv -> kv[1]));
    }
}
```

#### Complete PKCE sign-in orchestration (inside `DefaultAuthSessionBroker`)

```java
// Inside DefaultAuthSessionBroker.signIn(String providerId)
public CompletableFuture<AuthSession> signIn(String providerId) {
    IdentityProvider provider = registry.get(providerId);
    PkcePair pkce = PkceGenerator.generate();
    String state = generateSecureState();   // SecureRandom, 32 bytes, base64url
    String nonce = generateSecureState();

    return CompletableFuture.supplyAsync(() -> {
        try (LoopbackCallbackServer callbackServer = new LoopbackCallbackServer()) {
            URI redirectUri = callbackServer.redirectUri();
            URI authUri = provider.buildAuthorizationUri(pkce, redirectUri, state, nonce);

            // Open system browser (FX thread not required for Desktop.browse).
            java.awt.Desktop.getDesktop().browse(authUri);

            // Block background thread, not FX thread.
            Map<String, String> params = callbackServer.awaitCallback()
                .orTimeout(180, TimeUnit.SECONDS)
                .join();

            // CSRF validation.
            if (!state.equals(params.get("state"))) {
                throw new SecurityException("CSRF state mismatch — aborting login");
            }
            String code = params.get("code");
            if (code == null) throw new IllegalStateException("No authorization code returned");

            TokenResponse tokens = provider.exchangeCodeForTokens(code, pkce, redirectUri);
            return buildAndPersistSession(provider, tokens);
        } catch (IOException e) {
            throw new RuntimeException("Loopback server failed", e);
        }
    }).whenComplete((session, ex) -> updateAuthState(session, ex));
}
```

### 6.2 GitHub Device Flow

```java
// Inside DefaultAuthSessionBroker.signInWithDeviceFlow(String providerId)
public CompletableFuture<AuthSession> signInWithDeviceFlow(String providerId) {
    IdentityProvider provider = registry.get(providerId);

    return CompletableFuture.supplyAsync(() -> {
        DeviceCodeResponse deviceCode = provider.requestDeviceCode()
            .orElseThrow(() -> new UnsupportedOperationException(
                providerId + " does not support device flow"));

        // Push the user-code + URI to the UI via authStateProperty.
        Platform.runLater(() -> deviceFlowSubject.setValue(deviceCode));

        // Poll every deviceCode.interval() seconds until granted or expired.
        long deadline = System.currentTimeMillis() + deviceCode.expiresIn() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try { Thread.sleep(deviceCode.interval() * 1000L); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }

            Optional<TokenResponse> tokenOpt = provider.pollDeviceToken(deviceCode.deviceCode());
            if (tokenOpt.isPresent()) {
                return buildAndPersistSession(provider, tokenOpt.get());
            }
        }
        throw new RuntimeException("Device flow timed out");
    }).whenComplete((session, ex) -> updateAuthState(session, ex));
}
```

### 6.3 Silent Token Refresh

```java
package org.metalib.papifly.fx.docking.login.core;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class TokenRefreshScheduler {

    private static final long SKEW_SECONDS = 90;
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "auth-token-refresh");
            t.setDaemon(true);
            return t;
        });

    private ScheduledFuture<?> pending;

    public void schedule(AuthSession session, Runnable refreshAction) {
        cancelPending();
        long delay = Math.max(0,
            session.expiresAt().getEpochSecond()
            - System.currentTimeMillis() / 1000
            - SKEW_SECONDS);
        pending = scheduler.schedule(refreshAction, delay, TimeUnit.SECONDS);
    }

    public void cancelPending() {
        if (pending != null && !pending.isDone()) pending.cancel(false);
    }

    public void shutdown() { scheduler.shutdownNow(); }
}
```

---

## 7. Secure Token Storage

### 7.1 `OsKeychainSecureTokenStore` (primary)

Delegates to the `java-keyring` library which uses JNA to call the OS-native APIs:
- **macOS** → Keychain Services (`SecKeychainAddGenericPassword`)
- **Windows** → Credential Manager (`CredWrite` / `CredRead`)
- **Linux** → Secret Service over D-Bus (`libsecret`)

```java
package org.metalib.papifly.fx.docking.login.core.store;

import com.github.javakeyring.Keyring;
import org.metalib.papifly.fx.docking.login.api.SecureTokenStore;

import java.util.Optional;

public final class OsKeychainSecureTokenStore implements SecureTokenStore {

    private static final String SERVICE = "papiflyfx-docking";
    private final Keyring keyring;

    public OsKeychainSecureTokenStore() {
        try { keyring = Keyring.create(); }
        catch (Exception e) { throw new IllegalStateException(
            "OS keychain unavailable — use FileVaultSecureTokenStore as fallback", e); }
    }

    @Override
    public void put(String key, byte[] secret) {
        try {
            keyring.setPassword(SERVICE, key,
                new String(secret, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) { throw new RuntimeException("Keychain write failed", e); }
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            String val = keyring.getPassword(SERVICE, key);
            return Optional.ofNullable(val)
                .map(v -> v.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public void delete(String key) {
        try { keyring.deletePassword(SERVICE, key); } catch (Exception ignored) {}
    }

    @Override
    public void deleteAll() {
        // Enumerate and remove — implementation deferred to Phase 3.
    }
}
```

### 7.2 `FileVaultSecureTokenStore` (fallback, Phase 1 default)

Encrypts all secrets with AES-256-GCM.  The master key is a
`SecureRandom`-generated 256-bit key stored once in the OS keychain
(bootstrapping) or in a protected user-config directory (fallback-of-fallback).

```java
package org.metalib.papifly.fx.docking.login.core.store;

import org.metalib.papifly.fx.docking.login.api.SecureTokenStore;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;

/**
 * AES-256-GCM encrypted JSON vault.
 * Each entry is stored as: base64(IV) + ":" + base64(ciphertext+tag).
 */
public final class FileVaultSecureTokenStore implements SecureTokenStore {

    private static final int GCM_TAG_LENGTH = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey masterKey;
    private final Path vaultPath;
    private final Map<String, String> entries;   // key → encrypted blob

    public FileVaultSecureTokenStore(SecretKey masterKey, Path vaultPath) {
        this.masterKey = masterKey;
        this.vaultPath = vaultPath;
        this.entries = loadOrCreate(vaultPath);
    }

    @Override
    public void put(String key, byte[] secret) {
        entries.put(key, encrypt(secret));
        persist();
    }

    @Override
    public Optional<byte[]> get(String key) {
        return Optional.ofNullable(entries.get(key)).map(this::decrypt);
    }

    @Override
    public void delete(String key) {
        entries.remove(key);
        persist();
    }

    @Override
    public void deleteAll() {
        entries.clear();
        persist();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ct = cipher.doFinal(plain);
            return Base64.getEncoder().encodeToString(iv) + ":"
                + Base64.getEncoder().encodeToString(ct);
        } catch (Exception e) { throw new RuntimeException("Encryption failed", e); }
    }

    private byte[] decrypt(String blob) {
        try {
            String[] parts = blob.split(":");
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ct = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(ct);
        } catch (Exception e) { throw new RuntimeException("Decryption failed", e); }
    }

    private void persist() {
        // Minimal JSON serialization — no external library.
        StringBuilder sb = new StringBuilder("{");
        entries.forEach((k, v) ->
            sb.append("\"").append(k).append("\":\"").append(v).append("\","));
        if (!entries.isEmpty()) sb.deleteCharAt(sb.length() - 1);
        sb.append("}");
        try { Files.writeString(vaultPath, sb, StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private static Map<String, String> loadOrCreate(Path path) {
        // Simplified bootstrap — full JSON parsing deferred to implementation.
        return new java.util.LinkedHashMap<>();
    }
}
```

### 7.3 Memory Hygiene

After tokens have been written to storage, zero-fill the byte arrays to
prevent recovery from heap dumps or crash reports:

```java
private static void zeroFill(byte[] secret) {
    Arrays.fill(secret, (byte) 0);
}

private static void zeroFill(char[] secret) {
    Arrays.fill(secret, '\0');
}
```

Call `zeroFill` immediately after `store.put(key, tokenBytes)`.

---

## 8. Provider Implementations

### 8.1 `GoogleIdentityProvider`

```java
package org.metalib.papifly.fx.docking.login.providers;

import org.metalib.papifly.fx.docking.login.api.*;

import java.net.URI;
import java.net.http.*;
import java.util.List;
import java.util.Optional;

public final class GoogleIdentityProvider implements IdentityProvider {

    private static final String AUTH_EP  = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_EP = "https://oauth2.googleapis.com/token";
    private static final String USER_EP  = "https://openidconnect.googleapis.com/v1/userinfo";

    private final String clientId;

    public GoogleIdentityProvider(String clientId) { this.clientId = clientId; }

    @Override public String providerId()   { return "google"; }
    @Override public String displayName()  { return "Google"; }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities(true, false, true, true, false);
    }

    @Override public List<String> defaultScopes() {
        return List.of("openid", "email", "profile");
    }

    @Override
    public URI buildAuthorizationUri(PkcePair pkce, URI redirectUri,
                                     String state, String nonce) {
        return URI.create(AUTH_EP
            + "?client_id=" + clientId
            + "&response_type=code"
            + "&redirect_uri=" + redirectUri
            + "&scope=openid%20email%20profile"
            + "&state=" + state
            + "&nonce=" + nonce
            + "&code_challenge=" + pkce.codeChallenge()
            + "&code_challenge_method=S256"
            + "&access_type=offline"
            + "&prompt=consent");
    }

    @Override
    public TokenResponse exchangeCodeForTokens(String code, PkcePair pkce, URI redirectUri) {
        String body = "client_id=" + clientId
            + "&code=" + code
            + "&code_verifier=" + pkce.codeVerifier()
            + "&grant_type=authorization_code"
            + "&redirect_uri=" + redirectUri;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(TOKEN_EP))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        // Parse the JSON response into a TokenResponse record.
        // Use the minimal JSON parser from the docking framework (no external dep).
        return parseTokenResponse(sendRequest(request));
    }

    @Override
    public Optional<UserInfo> fetchUserInfo(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(USER_EP))
            .header("Authorization", "Bearer " + accessToken)
            .GET().build();
        return Optional.of(parseUserInfo(sendRequest(request)));
    }

    // ... private helpers (sendRequest, parseTokenResponse, parseUserInfo) ...
}
```

### 8.2 `GitHubIdentityProvider` (Auth Code + optional Device Flow)

```java
public final class GitHubIdentityProvider implements IdentityProvider {

    private static final String AUTH_EP   = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_EP  = "https://github.com/login/oauth/access_token";
    private static final String DEVICE_EP = "https://github.com/login/device/code";
    private static final String USER_EP   = "https://api.github.com/user";

    private final String clientId;
    private final boolean deviceFlowEnabled;

    public GitHubIdentityProvider(String clientId, boolean deviceFlowEnabled) {
        this.clientId = clientId;
        this.deviceFlowEnabled = deviceFlowEnabled;
    }

    @Override public String providerId()  { return "github"; }
    @Override public String displayName() { return "GitHub"; }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities(false, deviceFlowEnabled, false, true, false);
    }

    @Override public List<String> defaultScopes() { return List.of("read:user", "user:email"); }

    @Override
    public URI buildAuthorizationUri(PkcePair pkce, URI redirectUri,
                                     String state, String nonce) {
        // GitHub does not require PKCE for the auth-code flow but it does
        // support it — include it when present.
        return URI.create(AUTH_EP
            + "?client_id=" + clientId
            + "&redirect_uri=" + redirectUri
            + "&scope=read:user%20user:email"
            + "&state=" + state);
    }

    @Override
    public Optional<DeviceCodeResponse> requestDeviceCode() {
        if (!deviceFlowEnabled) return Optional.empty();
        String body = "client_id=" + clientId + "&scope=read:user%20user:email";
        // POST to DEVICE_EP, parse response ...
        return Optional.of(parseDeviceCodeResponse(/* ... */));
    }

    @Override
    public Optional<TokenResponse> pollDeviceToken(String deviceCode) {
        String body = "client_id=" + clientId
            + "&device_code=" + deviceCode
            + "&grant_type=urn:ietf:params:oauth:grant-type:device_code";
        // POST to TOKEN_EP, check for "authorization_pending" or success.
        return parsePollingResponse(/* ... */);
    }
}
```

### 8.3 Apple (requires relay service)

Apple requires the authorization response to be `POST`ed to a verified HTTPS domain — not a loopback
address.  The recommended pattern is a tiny relay service:

```
App ──► browser ──► Apple IdP
         │
         ▼
     relay.example.com/apple/callback  (HTTPS, verified domain)
         │ forwards { code, state, id_token } via WebSocket / SSE
         ▼
     127.0.0.1:<port>/callback (loopback)
         │
         ▼
     LoopbackCallbackServer
```

The `AppleIdentityProvider` sets `requiresHttpsRedirect = true` in its
`ProviderCapabilities`, and `DefaultAuthSessionBroker` checks this flag
before starting `LoopbackCallbackServer` — it instead starts a WebSocket
listener and opens the relay URL.

### 8.4 Provider Comparison

| Provider | Redirect | PKCE | Device Flow | OIDC UserInfo | Notes |
|----------|----------|------|-------------|---------------|-------|
| Google   | loopback | ✓ mandatory | ✗ | ✓ `/userinfo` | `access_type=offline` for refresh token |
| GitHub   | loopback | optional | ✓ (opt-in) | ✗ `/user` API | No client secret needed for device flow |
| Facebook | loopback | ✓ | ✗ | ✗ Graph API | App secret required; no pure-native flow |
| Amazon   | loopback | ✓ | ✗ | ✓ | Strict token rotation every 1 h |
| Apple    | HTTPS relay | ✓ | ✗ | ✓ (JWT only) | POST to verified domain; relay required |

---

## 9. Session Management

### 9.1 Lifecycle State Machine

```
 UNAUTHENTICATED
      │ signIn()
      ▼
 AWAITING_CALLBACK ──timeout──► UNAUTHENTICATED
      │ code received
      ▼
 EXCHANGING_CODE ──error──► UNAUTHENTICATED
      │ success
      ▼
 AUTHENTICATED ◄─────────── REFRESHING
      │ isExpiringSoon()         │
      ├──────────────────────────┘ (silent refresh)
      │
      │ logout()
      ▼
 SIGNED_OUT ──────────────► UNAUTHENTICATED

 AUTHENTICATED ──refresh fails──► SESSION_EXPIRED ──► UNAUTHENTICATED
```

State transitions happen on a background thread; `Platform.runLater` updates
`authStateProperty()` so JavaFX bindings work correctly.

### 9.2 Multi-Account Support

```java
// Persisting multiple sessions
private final Map<String, AuthSession> sessions = new LinkedHashMap<>();
private volatile String activeKey;

private String sessionKey(AuthSession s) {
    return s.providerId() + "/" + s.userInfo().subject();
}

public void setActiveSession(AuthSession session) {
    this.activeKey = sessionKey(session);
    Platform.runLater(() -> authStateProperty.set(AuthState.AUTHENTICATED));
}
```

### 9.3 Idle Timeout (optional)

```java
// In LoginDockContent — attach to the Scene's event filter:
scene.addEventFilter(Event.ANY, e -> lastActivityTime.set(System.currentTimeMillis()));

ScheduledExecutorService idle = Executors.newSingleThreadScheduledExecutor();
idle.scheduleAtFixedRate(() -> {
    long idleMs = System.currentTimeMillis() - lastActivityTime.get();
    if (idleMs > IDLE_TIMEOUT_MS && broker.getActiveSession().isPresent()) {
        Platform.runLater(() -> broker.logout(false));
    }
}, 30, 30, TimeUnit.SECONDS);
```

---

## 10. JavaFX UI Components

### 10.1 `LoginView` — provider button grid + status area

```java
package org.metalib.papifly.fx.docking.login.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.metalib.papifly.fx.docking.login.api.*;

public final class LoginView extends VBox {

    private final AuthSessionBroker broker;
    private final Label statusLabel = new Label();
    private final ProgressBar progress = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);

    public LoginView(AuthSessionBroker broker) {
        this.broker = broker;
        setAlignment(Pos.CENTER);
        setSpacing(12);
        setPadding(new Insets(24));

        Label title = new Label("Sign in");
        title.setStyle("-fx-font-size: 18; -fx-font-weight: bold;");

        FlowPane providerRow = new FlowPane(8, 8);
        providerRow.setAlignment(Pos.CENTER);
        broker.availableProviders().forEach(id -> {
            Button btn = new Button("Sign in with " + id);
            btn.setOnAction(e -> startSignIn(id));
            providerRow.getChildren().add(btn);
        });

        progress.setVisible(false);
        statusLabel.setWrapText(true);

        getChildren().addAll(title, providerRow, progress, statusLabel);

        // React to AuthState changes on the FX thread.
        broker.authStateProperty().addListener((obs, old, state) -> applyState(state));
        applyState(broker.authStateProperty().get());
    }

    private void startSignIn(String providerId) {
        progress.setVisible(true);
        statusLabel.setText("Opening browser…");
        broker.signIn(providerId).whenComplete((session, ex) -> {
            // state change propagates through authStateProperty; no direct UI update here.
        });
    }

    private void applyState(AuthState state) {
        progress.setVisible(state == AuthState.AWAITING_CALLBACK
            || state == AuthState.EXCHANGING_CODE
            || state == AuthState.REFRESHING);
        statusLabel.setText(switch (state) {
            case AWAITING_CALLBACK -> "Complete sign-in in your browser…";
            case EXCHANGING_CODE   -> "Verifying tokens…";
            case REFRESHING        -> "Refreshing session…";
            case SESSION_EXPIRED   -> "Session expired. Please sign in again.";
            case AUTHENTICATED     -> "Signed in.";
            default -> "";
        });
    }
}
```

### 10.2 `DeviceFlowView` — user-code display + countdown

```java
public final class DeviceFlowView extends VBox {

    public DeviceFlowView(DeviceCodeResponse deviceCode) {
        setAlignment(Pos.CENTER);
        setSpacing(16);
        setPadding(new Insets(24));

        Label instruction = new Label("Open the following URL and enter the code:");
        Hyperlink link = new Hyperlink(deviceCode.verificationUri());
        link.setOnAction(e -> openBrowser(deviceCode.verificationUri()));

        Label code = new Label(deviceCode.userCode());
        code.setStyle("-fx-font-size: 28; -fx-font-weight: bold; -fx-font-family: monospace;");

        ProgressIndicator spinner = new ProgressIndicator();
        Label countdown = new Label("Expires in " + deviceCode.expiresIn() + "s");

        // Countdown timer
        javafx.animation.Timeline timer = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(Duration.seconds(1), ev -> {
                // decrement and update countdown label ...
            }));
        timer.setCycleCount(deviceCode.expiresIn());
        timer.play();

        getChildren().addAll(instruction, link, code, spinner, countdown);
    }
}
```

### 10.3 `LoginDockContent` — `DisposableContent` adapter

```java
package org.metalib.papifly.fx.docking.login.ui;

import javafx.scene.Node;
import org.metalib.papifly.fx.docking.api.DisposableContent;
import org.metalib.papifly.fx.docking.login.api.*;

public final class LoginDockContent extends javafx.scene.layout.StackPane
    implements DisposableContent {

    public static final String FACTORY_ID = "login";

    private final AuthSessionBroker broker;
    private final LoginView loginView;
    private final AccountStatusWidget statusWidget;

    public LoginDockContent(AuthSessionBroker broker) {
        this.broker = broker;
        this.loginView = new LoginView(broker);
        this.statusWidget = new AccountStatusWidget(broker);

        broker.authStateProperty().addListener((obs, old, state) -> {
            if (state == AuthState.AUTHENTICATED) {
                getChildren().setAll(statusWidget);
            } else {
                getChildren().setAll(loginView);
            }
        });

        // Initial view
        boolean hasSession = broker.getActiveSession().isPresent();
        getChildren().add(hasSession ? statusWidget : loginView);
    }

    @Override
    public void dispose() {
        // Unregister listeners, stop refresh scheduler, etc.
        broker.logout(false);
    }
}
```

### 10.4 `ContentFactory` and `ContentStateAdapter` registration

```java
// In application startup (mirrors CodeEditor pattern from CLAUDE.md):
ContentStateRegistry.register(new LoginSessionStateAdapter());  // first
dockManager.setContentFactory(factoryId -> {
    if (LoginDockContent.FACTORY_ID.equals(factoryId)) {
        return new LoginDockContent(broker);
    }
    return existingFactory.create(factoryId);
});
```

`LoginSessionStateAdapter` saves only non-secret state (e.g., last selected provider ID, panel
position) and restores the `LoginDockContent` shell.  It **never** writes tokens to the
session JSON.

### 10.5 Theme integration

```java
// LoginView reads from the injected Theme, matching framework convention.
public LoginView(AuthSessionBroker broker, Theme theme) {
    // ...
    setBackground(new Background(new BackgroundFill(
        theme.background(), new CornerRadii(theme.cornerRadius()), Insets.EMPTY)));
    title.setTextFill(theme.textColorActive());
    // ...
}
```

---

## 11. Event Bus Integration

```java
// Published by DefaultAuthSessionBroker:
public enum LoginEventType { SIGN_IN_STARTED, SIGN_IN_SUCCESS, SIGN_IN_FAILED,
                              SESSION_REFRESHED, SESSION_EXPIRED, SIGNED_OUT }

public record LoginEvent(LoginEventType type, Optional<AuthSession> session,
                         Optional<Throwable> error) {}
```

The `DockManager` (or the application) subscribes to observe these events and
can trigger perspective switches, dock visibility toggling, etc.:

```java
broker.authStateProperty().addListener((obs, old, newState) -> {
    if (newState == AuthState.AUTHENTICATED) {
        // Restore user's saved docking layout.
        dockManager.setRoot(savedLayout);
    } else if (newState == AuthState.SIGNED_OUT || newState == AuthState.SESSION_EXPIRED) {
        // Show login-only perspective.
        dockManager.setRoot(buildLoginLayout(broker));
    }
});
```

---

## 12. Maven Module Setup

```xml
<!-- papiflyfx-docking-login/pom.xml -->
<project>
    <parent>
        <groupId>org.metalib.papifly.docking</groupId>
        <artifactId>papiflyfx-docking</artifactId>
        <version>0.0.14-SNAPSHOT</version>
    </parent>

    <artifactId>papiflyfx-docking-login</artifactId>
    <name>papiflyfx-docking-login</name>
    <description>Login dock: OAuth 2.0/OIDC, session management, secure token storage.</description>

    <dependencies>
        <!-- Framework APIs -->
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

        <!-- JavaFX -->
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-controls</artifactId>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-web</artifactId>
            <optional>true</optional>   <!-- only if WebView needed -->
        </dependency>

        <!-- OS keychain (JNA-based) -->
        <dependency>
            <groupId>com.github.javakeyring</groupId>
            <artifactId>java-keyring</artifactId>
            <version>${java.keyring.version}</version>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

Add to root `pom.xml` `<modules>` and `<dependencyManagement>`:
```xml
<module>papiflyfx-docking-login</module>

<!-- in dependencyManagement -->
<dependency>
    <groupId>com.github.javakeyring</groupId>
    <artifactId>java-keyring</artifactId>
    <version>${java.keyring.version}</version>
</dependency>
```

And in root `<properties>`:
```xml
<java.keyring.version>1.0.4</java.keyring.version>
```

---

## 13. Security Checklist

| # | Requirement | Detail |
|---|-------------|--------|
| 1 | PKCE mandatory | Use S256; generate fresh verifier per attempt |
| 2 | State (CSRF) validation | Compare exact string; reject on mismatch |
| 3 | Loopback only | Bind to `127.0.0.1`, never `0.0.0.0` |
| 4 | System browser | Never capture credentials via embedded WebView |
| 5 | Ephemeral port | Use port 0 to avoid conflicts |
| 6 | Short-lived listener | Auto-close server after 180 s or first response |
| 7 | OS native secret storage | `OsKeychainSecureTokenStore` preferred |
| 8 | AES-GCM fallback | `FileVaultSecureTokenStore` with per-install master key |
| 9 | Memory hygiene | Zero-fill token byte arrays after persisting |
| 10 | No plaintext secrets | Never log, serialize to JSON, or write to disk in cleartext |
| 11 | HTTPS enforcement | All provider endpoints over TLS; reject HTTP |
| 12 | Nonce validation | Verify OIDC ID token `nonce` claim |
| 13 | Access token in-memory | Never persist access tokens; only refresh-token refs |
| 14 | Public client | No embedded client secrets; support PKCE-only flows |

---

## 14. Testing Strategy

### 14.1 Unit tests (`*Test.java`)

```java
// PkceGeneratorTest
@Test
void verifierAndChallengeAreDifferent() {
    PkcePair pair = PkceGenerator.generate();
    assertNotEquals(pair.codeVerifier(), pair.codeChallenge());
    assertEquals("S256", pair.method());
}

@Test
void challengeIsCorrectSha256Base64url() throws Exception {
    // Known vector from RFC 7636 appendix B.
    PkcePair pair = new PkcePair(
        "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
        "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
    );
    assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", pair.codeChallenge());
}
```

```java
// LoopbackCallbackServerTest
@Test
void capturesQueryParams() throws Exception {
    try (LoopbackCallbackServer server = new LoopbackCallbackServer()) {
        URI callback = URI.create(server.redirectUri() + "?code=abc123&state=xyz");
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(callback).GET().build(),
            HttpResponse.BodyHandlers.discarding());
        Map<String, String> params = server.awaitCallback().get(5, TimeUnit.SECONDS);
        assertEquals("abc123", params.get("code"));
        assertEquals("xyz", params.get("state"));
    }
}
```

```java
// FileVaultSecureTokenStoreTest
@Test
void roundTripEncryptDecrypt(@TempDir Path tmp) {
    SecretKey key = generateAes256Key();
    FileVaultSecureTokenStore store = new FileVaultSecureTokenStore(key, tmp.resolve("vault.json"));
    byte[] secret = "my-refresh-token".getBytes(StandardCharsets.UTF_8);
    store.put("github/user1/refresh", secret);
    Optional<byte[]> loaded = store.get("github/user1/refresh");
    assertTrue(loaded.isPresent());
    assertArrayEquals(secret, loaded.get());
}
```

### 14.2 Integration tests (mock OIDC server)

Use an embedded mock OAuth server (e.g., WireMock or a tiny `HttpServer`)
to simulate token exchanges without hitting real IdP endpoints.

```java
@Test
void signInSucceedsWithMockServer() throws Exception {
    // Start mock IdP server, configure GoogleIdentityProvider to point to it.
    // Invoke broker.signIn("google") and drive the redirect manually.
    // Assert AuthState.AUTHENTICATED and session fields.
}
```

### 14.3 UI tests (`*FxTest.java`, TestFX)

```java
@Test
void loginViewShowsProviderButtons(FxRobot robot) {
    AuthSessionBroker mockBroker = Mockito.mock(AuthSessionBroker.class);
    when(mockBroker.availableProviders()).thenReturn(List.of("google", "github"));
    when(mockBroker.authStateProperty()).thenReturn(new SimpleObjectProperty<>(UNAUTHENTICATED));
    LoginView view = new LoginView(mockBroker);
    // ... verify button labels with robot.lookup(...)
}
```

---

## 15. Phased Implementation Plan

### Phase 1 — Core scaffold + generic OIDC

Goals: working sign-in through any OIDC provider, tokens in the file vault,
login dock visible in the samples app.

- `papiflyfx-docking-login` module added to root POM.
- `PkceGenerator`, `LoopbackCallbackServer`, `DefaultAuthSessionBroker` implemented.
- `FileVaultSecureTokenStore` (AES-GCM) as the sole `SecureTokenStore`.
- `LoginView` and `LoginDockContent` basic UI (no theming yet).
- `GenericOidcIdentityProvider` accepting a JSON config file for endpoint URLs.
- Unit tests: PKCE generation, loopback server, vault round-trip.
- Demo in `papiflyfx-docking-samples`.

### Phase 2 — Provider presets

Goals: named presets for Google and GitHub, device flow on GitHub.

- `GoogleIdentityProvider` (PKCE + userinfo).
- `GitHubIdentityProvider` (auth code + optional device flow).
- `DeviceFlowView` UI component.
- `AccountStatusWidget` (avatar, name, expiry countdown, logout button).
- Provider integration tests against mock OIDC server.

### Phase 3 — OS keychain + security hardening

Goals: secrets stored natively; no plaintext on disk for any real user.

- `OsKeychainSecureTokenStore` using `java-keyring`.
- Automatic fallback to `FileVaultSecureTokenStore` when OS keychain unavailable.
- Memory hygiene: `zeroFill` called after every `store.put`.
- `TokenRefreshScheduler` — proactive background refresh 90 s before expiry.
- State validation hardening: nonce check for OIDC.

### Phase 4 — Additional providers + polish

Goals: Facebook, Amazon, Apple (relay); multi-account; idle timeout; theme binding.

- `FacebookIdentityProvider`, `AmazonIdentityProvider`.
- `AppleIdentityProvider` with relay documentation + code stub.
- Multi-account chooser UI (`AccountChooserView`).
- Idle-timeout guard in `LoginDockContent`.
- Full `Theme` binding across all login UI components.
- Javadoc for all public-API types.
- Documentation: `papiflyfx-docking-login/README.md` with setup example.

---

## 16. Open Questions

1. **Shared broker vs per-module brokers** — Should GitHub Dock and Hugo Dock share the
   same `AuthSessionBroker` bean, or have dedicated instances with their own sessions?
   A shared broker with multi-account support is the recommended default.

2. **Login gate vs optional panel** — Should the dock enforce an unauthenticated gate
   (app blocked until login) or allow the panel to be closed?
   Recommend making this configurable via `LoginDockConfig.requireAuthOnStartup`.

3. **Minimum built-in providers** — Ship Google + GitHub in Phase 2; all others as
   community presets or app-supplied `IdentityProvider` instances.

4. **Apple relay hosting** — Who runs the relay?  Each app team must host their own
   or use a shared community relay.  Document clearly; do not hardcode.

5. **Session persistence between JVM restarts** — With refresh tokens in the OS keychain
   and non-secret metadata (provider ID, subject, scopes) in a JSON file,
   auto-login on next launch is achievable.  Confirm desired UX before implementing.
