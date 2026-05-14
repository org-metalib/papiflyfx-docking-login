package org.metalib.papifly.fx.login.core;

import org.junit.jupiter.api.Test;
import org.metalib.papifly.fx.login.config.LoginProviderSettings;
import org.metalib.papifly.fx.login.idapi.AuthorizationRequest;
import org.metalib.papifly.fx.login.idapi.ProviderConfig;
import org.metalib.papifly.fx.login.idapi.providers.GoogleProvider;
import org.metalib.papifly.fx.settings.api.SecretStore;
import org.metalib.papifly.fx.settings.api.SettingScope;
import org.metalib.papifly.fx.settings.api.SettingsStorage;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProviderSettingsResolverTest {

    @Test
    void googleAuthorizationRequestIncludesOfflineAccessByDefault() {
        InMemorySettingsStorage storage = new InMemorySettingsStorage();
        storage.putString(SettingScope.APPLICATION, LoginProviderSettings.clientIdKey(GoogleProvider.PROVIDER_ID), "google-client");

        ProviderSettingsResolver resolver = new ProviderSettingsResolver(storage, new InMemorySecretStore());
        ProviderSettingsResolver.ProviderRuntimeConfig runtimeConfig = resolver.resolve(
            GoogleProvider.PROVIDER_ID,
            new GoogleProvider().descriptor()
        );

        AuthorizationRequest request = new GoogleProvider().buildAuthorizationRequest(
            runtimeConfig.config(),
            "http://127.0.0.1/callback"
        );

        assertEquals("offline", queryParameter(URI.create(request.authUrl()), "access_type"));
        assertNull(queryParameter(URI.create(request.authUrl()), "prompt"));
    }

    @Test
    void googleAuthorizationRequestIncludesConsentWhenConfigured() {
        ProviderConfig config = new ProviderConfig(
            "google-client",
            null,
            List.of("openid", "email", "profile"),
            null,
            null,
            null,
            null,
            null,
            Map.of(
                "access_type", "offline",
                "prompt", "consent"
            )
        );

        AuthorizationRequest request = new GoogleProvider().buildAuthorizationRequest(config, "http://127.0.0.1/callback");

        assertEquals("offline", queryParameter(URI.create(request.authUrl()), "access_type"));
        assertEquals("consent", queryParameter(URI.create(request.authUrl()), "prompt"));
    }

    private static String queryParameter(URI uri, String name) {
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
