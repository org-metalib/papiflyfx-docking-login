package org.metalib.papifly.fx.login.core;

import org.junit.jupiter.api.Test;
import org.metalib.papifly.fx.login.config.LoginProviderSettings;
import org.metalib.papifly.fx.login.idapi.AuthorizationRequest;
import org.metalib.papifly.fx.login.idapi.CodeExchangeRequest;
import org.metalib.papifly.fx.login.idapi.DeviceCodeResponse;
import org.metalib.papifly.fx.login.idapi.IdentityProvider;
import org.metalib.papifly.fx.login.idapi.ProviderCapabilities;
import org.metalib.papifly.fx.login.idapi.ProviderConfig;
import org.metalib.papifly.fx.login.idapi.ProviderDescriptor;
import org.metalib.papifly.fx.login.idapi.ProviderRegistry;
import org.metalib.papifly.fx.login.idapi.TokenResponse;
import org.metalib.papifly.fx.login.idapi.UserPrincipal;
import org.metalib.papifly.fx.login.idapi.providers.GoogleProvider;
import org.metalib.papifly.fx.login.session.AuthSession;
import org.metalib.papifly.fx.login.session.AuthState;
import org.metalib.papifly.fx.settings.api.SecretKeyNames;
import org.metalib.papifly.fx.settings.api.SecretStore;
import org.metalib.papifly.fx.settings.api.SettingScope;
import org.metalib.papifly.fx.settings.api.SettingsStorage;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultAuthSessionBrokerTest {

    @Test
    void signInCompletesAuthorizationCodeFlowAndPersistsRefreshToken() throws Exception {
        ProviderRegistry registry = new ProviderRegistry();
        registry.register(new FakeProvider());

        InMemorySettingsStorage storage = new InMemorySettingsStorage();
        storage.putString(SettingScope.APPLICATION, LoginProviderSettings.clientIdKey("fake"), "demo-client");
        InMemorySecretStore secretStore = new InMemorySecretStore();
        TestCallbackServer callbackServer = new TestCallbackServer();

        DefaultAuthSessionBroker broker = new DefaultAuthSessionBroker(
            registry,
            storage,
            secretStore,
            uri -> callbackServer.complete(uri, "test-code"),
            () -> callbackServer
        );

        AuthSession session = broker.signIn("fake").get(5, TimeUnit.SECONDS);

        assertEquals(AuthState.AUTHENTICATED, broker.authStateProperty().get());
        assertEquals("user-123", session.subject());
        assertEquals("Demo User", session.principal().displayName());
        assertEquals(
            "refresh-123",
            secretStore.getSecret(SecretKeyNames.oauthRefreshToken("fake", "user-123")).orElseThrow()
        );
    }

    @Test
    void refreshUsesStoredRefreshToken() throws Exception {
        ProviderRegistry registry = new ProviderRegistry();
        registry.register(new FakeProvider());

        InMemorySettingsStorage storage = new InMemorySettingsStorage();
        storage.putString(SettingScope.APPLICATION, LoginProviderSettings.clientIdKey("fake"), "demo-client");
        InMemorySecretStore secretStore = new InMemorySecretStore();
        TestCallbackServer callbackServer = new TestCallbackServer();

        DefaultAuthSessionBroker broker = new DefaultAuthSessionBroker(
            registry,
            storage,
            secretStore,
            uri -> callbackServer.complete(uri, "initial-code"),
            () -> callbackServer
        );

        AuthSession initial = broker.signIn("fake").get(5, TimeUnit.SECONDS);
        AuthSession refreshed = broker.refresh(true).get(5, TimeUnit.SECONDS);

        assertEquals(initial.sessionId(), refreshed.sessionId());
        assertEquals(AuthState.AUTHENTICATED, broker.authStateProperty().get());
        assertEquals("Refreshed User", refreshed.principal().displayName());
        assertTrue(refreshed.expiresAt().isAfter(initial.expiresAt()));
    }

    @Test
    void signInFailsWhenClientIdMissing() {
        ProviderRegistry registry = new ProviderRegistry();
        registry.register(new FakeProvider());

        DefaultAuthSessionBroker broker = new DefaultAuthSessionBroker(
            registry,
            new InMemorySettingsStorage(),
            new InMemorySecretStore(),
            uri -> { },
            TestCallbackServer::new
        );

        CompletionException error = assertThrows(
            CompletionException.class,
            () -> broker.signIn("fake").join()
        );

        assertTrue(error.getCause() instanceof IllegalStateException);
        assertTrue(error.getCause().getMessage().contains("client ID"));
        assertEquals(AuthState.ERROR, broker.authStateProperty().get());
    }

    @Test
    void deviceFlowContinuesWhenBrowserLaunchFails() throws Exception {
        ProviderRegistry registry = new ProviderRegistry();
        registry.register(new FakeDeviceFlowProvider());

        InMemorySettingsStorage storage = new InMemorySettingsStorage();
        storage.putString(SettingScope.APPLICATION, LoginProviderSettings.clientIdKey("device"), "demo-client");
        DefaultAuthSessionBroker broker = new DefaultAuthSessionBroker(
            registry,
            storage,
            new InMemorySecretStore(),
            uri -> { throw new IllegalStateException("Browser unavailable"); },
            TestCallbackServer::new
        );

        AuthSession session = broker.signIn("device").get(5, TimeUnit.SECONDS);

        assertEquals(AuthState.AUTHENTICATED, broker.authStateProperty().get());
        assertEquals("device-user", session.subject());
        assertEquals("Device User", session.principal().displayName());
        assertNull(broker.deviceCodeProperty().get());
    }

    @Test
    void googleSignInRetriesWithConsentWhenRefreshTokenIsMissing() throws Exception {
        ProviderRegistry registry = new ProviderRegistry();
        registry.register(new FakeGoogleProvider(
            new TokenResponse("google-access-1", null, null, "Bearer", 3600, "openid email profile"),
            new TokenResponse("google-access-2", "google-refresh-2", null, "Bearer", 3600, "openid email profile")
        ));

        InMemorySettingsStorage storage = new InMemorySettingsStorage();
        storage.putString(SettingScope.APPLICATION, LoginProviderSettings.clientIdKey(GoogleProvider.PROVIDER_ID), "google-client");
        InMemorySecretStore secretStore = new InMemorySecretStore();
        AtomicReference<TestCallbackServer> activeServer = new AtomicReference<>();
        List<URI> openedUris = new java.util.ArrayList<>();

        DefaultAuthSessionBroker broker = new DefaultAuthSessionBroker(
            registry,
            storage,
            secretStore,
            uri -> {
                openedUris.add(uri);
                TestCallbackServer server = activeServer.get();
                assertNotNull(server);
                server.complete(uri, "consent".equals(optionalQueryParameter(uri, "prompt")) ? "consent-code" : "initial-code");
            },
            () -> {
                TestCallbackServer server = new TestCallbackServer();
                activeServer.set(server);
                return server;
            }
        );

        AuthSession session = broker.signIn(GoogleProvider.PROVIDER_ID).get(5, TimeUnit.SECONDS);

        assertEquals(AuthState.AUTHENTICATED, broker.authStateProperty().get());
        assertEquals("user-123", session.subject());
        assertEquals(2, openedUris.size());
        assertNull(optionalQueryParameter(openedUris.get(0), "prompt"));
        assertEquals("consent", optionalQueryParameter(openedUris.get(1), "prompt"));
        assertEquals("offline", requiredQueryParameter(openedUris.get(0), "access_type"));
        assertEquals(
            "google-refresh-2",
            secretStore.getSecret(SecretKeyNames.oauthRefreshToken(GoogleProvider.PROVIDER_ID, "user-123")).orElseThrow()
        );
    }

    @Test
    void googleSignInReusesStoredRefreshTokenWithoutConsentRetry() throws Exception {
        ProviderRegistry registry = new ProviderRegistry();
        registry.register(new FakeGoogleProvider(
            new TokenResponse("google-access-1", null, null, "Bearer", 3600, "openid email profile"),
            new TokenResponse("google-access-2", null, null, "Bearer", 3600, "openid email profile")
        ));

        InMemorySettingsStorage storage = new InMemorySettingsStorage();
        storage.putString(SettingScope.APPLICATION, LoginProviderSettings.clientIdKey(GoogleProvider.PROVIDER_ID), "google-client");
        InMemorySecretStore secretStore = new InMemorySecretStore();
        secretStore.setSecret(SecretKeyNames.oauthRefreshToken(GoogleProvider.PROVIDER_ID, "user-123"), "stored-refresh");
        AtomicReference<TestCallbackServer> activeServer = new AtomicReference<>();
        List<URI> openedUris = new java.util.ArrayList<>();

        DefaultAuthSessionBroker broker = new DefaultAuthSessionBroker(
            registry,
            storage,
            secretStore,
            uri -> {
                openedUris.add(uri);
                TestCallbackServer server = activeServer.get();
                assertNotNull(server);
                server.complete(uri, "initial-code");
            },
            () -> {
                TestCallbackServer server = new TestCallbackServer();
                activeServer.set(server);
                return server;
            }
        );

        AuthSession session = broker.signIn(GoogleProvider.PROVIDER_ID).get(5, TimeUnit.SECONDS);

        assertEquals(AuthState.AUTHENTICATED, broker.authStateProperty().get());
        assertEquals("user-123", session.subject());
        assertEquals(1, openedUris.size());
        assertNull(optionalQueryParameter(openedUris.get(0), "prompt"));
        assertEquals(
            "stored-refresh",
            secretStore.getSecret(SecretKeyNames.oauthRefreshToken(GoogleProvider.PROVIDER_ID, "user-123")).orElseThrow()
        );
    }

    @Test
    void googleSignInFailsWhenConsentRetryStillDoesNotReturnRefreshToken() {
        ProviderRegistry registry = new ProviderRegistry();
        registry.register(new FakeGoogleProvider(
            new TokenResponse("google-access-1", null, null, "Bearer", 3600, "openid email profile"),
            new TokenResponse("google-access-2", null, null, "Bearer", 3600, "openid email profile")
        ));

        InMemorySettingsStorage storage = new InMemorySettingsStorage();
        storage.putString(SettingScope.APPLICATION, LoginProviderSettings.clientIdKey(GoogleProvider.PROVIDER_ID), "google-client");
        InMemorySecretStore secretStore = new InMemorySecretStore();
        AtomicReference<TestCallbackServer> activeServer = new AtomicReference<>();
        List<URI> openedUris = new java.util.ArrayList<>();

        DefaultAuthSessionBroker broker = new DefaultAuthSessionBroker(
            registry,
            storage,
            secretStore,
            uri -> {
                openedUris.add(uri);
                TestCallbackServer server = activeServer.get();
                assertNotNull(server);
                server.complete(uri, "consent".equals(optionalQueryParameter(uri, "prompt")) ? "consent-code" : "initial-code");
            },
            () -> {
                TestCallbackServer server = new TestCallbackServer();
                activeServer.set(server);
                return server;
            }
        );

        CompletionException error = assertThrows(
            CompletionException.class,
            () -> broker.signIn(GoogleProvider.PROVIDER_ID).join()
        );

        assertTrue(error.getCause() instanceof IllegalStateException);
        assertTrue(error.getCause().getMessage().contains("offline access"));
        assertEquals(2, openedUris.size());
        assertEquals("consent", optionalQueryParameter(openedUris.get(1), "prompt"));
        assertEquals(AuthState.ERROR, broker.authStateProperty().get());
        assertFalse(secretStore.getSecret(SecretKeyNames.oauthRefreshToken(GoogleProvider.PROVIDER_ID, "user-123")).isPresent());
    }

    private static String requiredQueryParameter(URI uri, String name) {
        String query = uri.getRawQuery();
        assertNotNull(query);
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = java.net.URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
            if (name.equals(key)) {
                return java.net.URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("Missing query parameter " + name);
    }

    private static String optionalQueryParameter(URI uri, String name) {
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = java.net.URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
            if (name.equals(key)) {
                return java.net.URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static final class TestCallbackServer implements DefaultAuthSessionBroker.CallbackServerHandle {

        private final CompletableFuture<Map<String, String>> callbackFuture = new CompletableFuture<>();

        @Override
        public String redirectUri() {
            return "http://127.0.0.1/callback";
        }

        @Override
        public CompletableFuture<Map<String, String>> callbackParams() {
            return callbackFuture;
        }

        @Override
        public void close() {
        }

        void complete(URI authUri, String code) {
            callbackFuture.complete(Map.of(
                "code", code,
                "state", requiredQueryParameter(authUri, "state")
            ));
        }
    }

    private static final class FakeProvider implements IdentityProvider {

        @Override
        public ProviderDescriptor descriptor() {
            return new ProviderDescriptor(
                "fake",
                "Fake Provider",
                null,
                List.of("openid", "profile"),
                new ProviderCapabilities(true, false, false, false, false)
            );
        }

        @Override
        public AuthorizationRequest buildAuthorizationRequest(ProviderConfig config, String redirectUri) {
            return new AuthorizationRequest(
                "https://example.test/authorize?redirect_uri="
                    + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                    + "&state=fake-state",
                "fake-state",
                "fake-nonce",
                "fake-verifier",
                redirectUri
            );
        }

        @Override
        public CompletableFuture<TokenResponse> exchangeCode(ProviderConfig config, CodeExchangeRequest request) {
            String accessToken = "initial-code".equals(request.code()) ? "access-123" : "access-000";
            return CompletableFuture.completedFuture(new TokenResponse(
                accessToken,
                "refresh-123",
                null,
                "Bearer",
                3600,
                "openid profile"
            ));
        }

        @Override
        public CompletableFuture<UserPrincipal> fetchUserPrincipal(ProviderConfig config, String accessToken) {
            String displayName = "access-456".equals(accessToken) ? "Refreshed User" : "Demo User";
            return CompletableFuture.completedFuture(new UserPrincipal("user-123", displayName, "demo@example.com", ""));
        }

        @Override
        public CompletableFuture<TokenResponse> refreshToken(ProviderConfig config, String refreshToken) {
            assertEquals("refresh-123", refreshToken);
            return CompletableFuture.completedFuture(new TokenResponse(
                "access-456",
                "refresh-123",
                null,
                "Bearer",
                7200,
                "openid profile"
            ));
        }
    }

    private static final class FakeDeviceFlowProvider implements IdentityProvider {

        @Override
        public ProviderDescriptor descriptor() {
            return new ProviderDescriptor(
                "device",
                "Device Provider",
                null,
                List.of("repo", "read:user"),
                new ProviderCapabilities(false, true, false, false, false)
            );
        }

        @Override
        public AuthorizationRequest buildAuthorizationRequest(ProviderConfig config, String redirectUri) {
            throw new UnsupportedOperationException("Device flow only");
        }

        @Override
        public CompletableFuture<TokenResponse> exchangeCode(ProviderConfig config, CodeExchangeRequest request) {
            throw new UnsupportedOperationException("Device flow only");
        }

        @Override
        public CompletableFuture<DeviceCodeResponse> requestDeviceCode(ProviderConfig config) {
            return CompletableFuture.completedFuture(new DeviceCodeResponse(
                "device-code",
                "ABCD-EFGH",
                "https://github.com/login/device",
                "https://github.com/login/device?user_code=ABCD-EFGH",
                900,
                5
            ));
        }

        @Override
        public CompletableFuture<TokenResponse> pollDeviceToken(ProviderConfig config, String deviceCode) {
            assertEquals("device-code", deviceCode);
            return CompletableFuture.completedFuture(new TokenResponse(
                "device-access",
                "device-refresh",
                null,
                "Bearer",
                3600,
                "repo read:user"
            ));
        }

        @Override
        public CompletableFuture<UserPrincipal> fetchUserPrincipal(ProviderConfig config, String accessToken) {
            assertEquals("device-access", accessToken);
            return CompletableFuture.completedFuture(new UserPrincipal(
                "device-user",
                "Device User",
                "device@example.com",
                ""
            ));
        }
    }

    private static final class FakeGoogleProvider extends GoogleProvider {

        private final TokenResponse initialResponse;
        private final TokenResponse consentResponse;

        private FakeGoogleProvider(TokenResponse initialResponse, TokenResponse consentResponse) {
            this.initialResponse = initialResponse;
            this.consentResponse = consentResponse;
        }

        @Override
        public CompletableFuture<TokenResponse> exchangeCode(ProviderConfig config, CodeExchangeRequest request) {
            return CompletableFuture.completedFuture(
                "consent-code".equals(request.code()) ? consentResponse : initialResponse
            );
        }

        @Override
        public CompletableFuture<UserPrincipal> fetchUserPrincipal(ProviderConfig config, String accessToken) {
            return CompletableFuture.completedFuture(new UserPrincipal(
                "user-123",
                "Google User",
                "user@gmail.com",
                ""
            ));
        }
    }

    private static final class InMemorySecretStore implements SecretStore {

        private final Map<String, String> values = new LinkedHashMap<>();

        @Override
        public Optional<String> getSecret(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public void setSecret(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void clearSecret(String key) {
            values.remove(key);
        }

        @Override
        public Set<String> listKeys() {
            return Set.copyOf(values.keySet());
        }
    }

    private static final class InMemorySettingsStorage implements SettingsStorage {

        private final Map<SettingScope, Map<String, Object>> scopes = new EnumMap<>(SettingScope.class);

        @Override
        public String getString(SettingScope scope, String key, String defaultValue) {
            Object value = scopes.computeIfAbsent(scope, ignored -> new LinkedHashMap<>()).get(key);
            return value == null ? defaultValue : String.valueOf(value);
        }

        @Override
        public boolean getBoolean(SettingScope scope, String key, boolean defaultValue) {
            Object value = scopes.computeIfAbsent(scope, ignored -> new LinkedHashMap<>()).get(key);
            return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
        }

        @Override
        public int getInt(SettingScope scope, String key, int defaultValue) {
            Object value = scopes.computeIfAbsent(scope, ignored -> new LinkedHashMap<>()).get(key);
            return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
        }

        @Override
        public double getDouble(SettingScope scope, String key, double defaultValue) {
            Object value = scopes.computeIfAbsent(scope, ignored -> new LinkedHashMap<>()).get(key);
            return value == null ? defaultValue : Double.parseDouble(String.valueOf(value));
        }

        @Override
        public Optional<String> getRaw(SettingScope scope, String key) {
            Object value = scopes.computeIfAbsent(scope, ignored -> new LinkedHashMap<>()).get(key);
            return value == null ? Optional.empty() : Optional.of(String.valueOf(value));
        }

        @Override
        public void putString(SettingScope scope, String key, String value) {
            scopes.computeIfAbsent(scope, ignored -> new LinkedHashMap<>()).put(key, value);
        }

        @Override
        public void putBoolean(SettingScope scope, String key, boolean value) {
            scopes.computeIfAbsent(scope, ignored -> new LinkedHashMap<>()).put(key, value);
        }

        @Override
        public void putInt(SettingScope scope, String key, int value) {
            scopes.computeIfAbsent(scope, ignored -> new LinkedHashMap<>()).put(key, value);
        }

        @Override
        public void putDouble(SettingScope scope, String key, double value) {
            scopes.computeIfAbsent(scope, ignored -> new LinkedHashMap<>()).put(key, value);
        }

        @Override
        public Map<String, Object> getMap(SettingScope scope, String key) {
            Object value = scopes.computeIfAbsent(scope, ignored -> new LinkedHashMap<>()).get(key);
            if (value instanceof Map<?, ?> map) {
                return new LinkedHashMap<>((Map<String, Object>) map);
            }
            return new LinkedHashMap<>();
        }

        @Override
        public void putMap(SettingScope scope, String key, Map<String, Object> value) {
            scopes.computeIfAbsent(scope, ignored -> new LinkedHashMap<>()).put(key, new LinkedHashMap<>(value));
        }

        @Override
        public void save() {
        }

        @Override
        public void reload() {
        }
    }
}
