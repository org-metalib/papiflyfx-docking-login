package org.metalib.papifly.fx.login.core;

import org.metalib.papifly.fx.login.config.LoginProviderSettings;
import org.metalib.papifly.fx.login.idapi.ProviderConfig;
import org.metalib.papifly.fx.login.idapi.ProviderDescriptor;
import org.metalib.papifly.fx.login.idapi.providers.GenericOidcProvider;
import org.metalib.papifly.fx.login.idapi.providers.GitHubProvider;
import org.metalib.papifly.fx.login.idapi.providers.GoogleProvider;
import org.metalib.papifly.fx.settings.api.SecretStore;
import org.metalib.papifly.fx.settings.api.SettingScope;
import org.metalib.papifly.fx.settings.api.SettingsStorage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class ProviderSettingsResolver {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().build();

    private final SettingsStorage storage;
    private final SecretStore secretStore;

    ProviderSettingsResolver(SettingsStorage storage, SecretStore secretStore) {
        this.storage = storage;
        this.secretStore = secretStore;
    }

    ProviderRuntimeConfig resolve(String providerId, ProviderDescriptor descriptor) {
        if (!readBoolean(LoginProviderSettings.systemProperty(providerId, "enabled"),
            LoginProviderSettings.enabledKey(providerId), true)) {
            throw new IllegalStateException(descriptor.displayName() + " is disabled in Settings > Authentication.");
        }

        String clientId = readString(
            LoginProviderSettings.systemProperty(providerId, "client-id"),
            LoginProviderSettings.clientIdKey(providerId),
            ""
        );
        if (clientId.isBlank()) {
            throw missingClientId(providerId, descriptor.displayName());
        }

        String clientSecret = readSecret(providerId);
        List<String> scopes = parseScopes(readString(
            LoginProviderSettings.systemProperty(providerId, "scopes"),
            LoginProviderSettings.scopesKey(providerId),
            String.join(" ", descriptor.defaultScopes())
        ));
        if (scopes.isEmpty()) {
            scopes = descriptor.defaultScopes();
        }

        return switch (providerId) {
            case GoogleProvider.PROVIDER_ID -> googleConfig(clientId, clientSecret, scopes);
            case GitHubProvider.PROVIDER_ID -> githubConfig(clientId, clientSecret, scopes);
            case GenericOidcProvider.PROVIDER_ID -> genericConfig(clientId, clientSecret, scopes);
            default -> new ProviderRuntimeConfig(
                new ProviderConfig(clientId, emptyToNull(clientSecret), scopes, null, null, null, null, null, Map.of()),
                null,
                false
            );
        };
    }

    private ProviderRuntimeConfig googleConfig(String clientId, String clientSecret, List<String> scopes) {
        String workspaceDomain = readString(
            LoginProviderSettings.systemProperty(GoogleProvider.PROVIDER_ID, "workspace-domain"),
            LoginProviderSettings.workspaceDomainKey(),
            ""
        );
        ProviderConfig config = new ProviderConfig(
            clientId,
            emptyToNull(clientSecret),
            scopes,
            null,
            "https://accounts.google.com/o/oauth2/v2/auth",
            "https://oauth2.googleapis.com/token",
            "https://openidconnect.googleapis.com/v1/userinfo",
            null,
            Map.of("access_type", "offline")
        );
        return new ProviderRuntimeConfig(config, emptyToNull(workspaceDomain), false);
    }

    private ProviderRuntimeConfig githubConfig(String clientId, String clientSecret, List<String> scopes) {
        String configuredEnterpriseUrl = readString(
            LoginProviderSettings.systemProperty(GitHubProvider.PROVIDER_ID, "enterprise-url"),
            LoginProviderSettings.enterpriseUrlKey(),
            readRaw(LoginProviderSettings.GITHUB_ENTERPRISE_URL_LEGACY_KEY).orElse("")
        );
        String enterpriseUrl = normalizeEnterpriseUrl(configuredEnterpriseUrl);
        String apiBase = resolveGitHubApiBase(enterpriseUrl);
        String baseUrl = resolveGitHubBaseUrl(enterpriseUrl);
        boolean preferDeviceFlow = "device".equalsIgnoreCase(readString(
            LoginProviderSettings.systemProperty(GitHubProvider.PROVIDER_ID, "flow"),
            LoginProviderSettings.flowKey(GitHubProvider.PROVIDER_ID),
            ""
        )) || clientSecret.isBlank();

        ProviderConfig config = new ProviderConfig(
            clientId,
            emptyToNull(clientSecret),
            scopes,
            null,
            baseUrl + "/login/oauth/authorize",
            baseUrl + "/login/oauth/access_token",
            apiBase + "/user",
            enterpriseUrl,
            Map.of()
        );
        return new ProviderRuntimeConfig(config, null, preferDeviceFlow);
    }

    private ProviderRuntimeConfig genericConfig(String clientId, String clientSecret, List<String> scopes) {
        String discoveryUrl = readString(
            LoginProviderSettings.systemProperty(GenericOidcProvider.PROVIDER_ID, "discovery-url"),
            LoginProviderSettings.discoveryUrlKey(),
            ""
        );
        if (discoveryUrl.isBlank()) {
            throw new IllegalStateException(
                "Generic OIDC requires a discovery URL. Set it in Settings > Authentication or pass -D"
                    + LoginProviderSettings.systemProperty(GenericOidcProvider.PROVIDER_ID, "discovery-url") + "=..."
            );
        }
        DiscoveryMetadata metadata = discover(discoveryUrl);
        ProviderConfig config = new ProviderConfig(
            clientId,
            emptyToNull(clientSecret),
            scopes,
            discoveryUrl,
            metadata.authorizationEndpoint(),
            metadata.tokenEndpoint(),
            metadata.userInfoEndpoint(),
            null,
            Map.of()
        );
        return new ProviderRuntimeConfig(config, null, false);
    }

    private DiscoveryMetadata discover(String discoveryUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(discoveryUrl))
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("OIDC discovery failed with HTTP " + response.statusCode() + '.');
            }
            Map<String, Object> json = JsonMapParser.parse(response.body());
            String authorizationEndpoint = stringValue(json, "authorization_endpoint");
            String tokenEndpoint = stringValue(json, "token_endpoint");
            String userInfoEndpoint = stringValue(json, "userinfo_endpoint");
            if (authorizationEndpoint == null || tokenEndpoint == null) {
                throw new IllegalStateException("OIDC discovery document is missing required endpoints.");
            }
            return new DiscoveryMetadata(authorizationEndpoint, tokenEndpoint, userInfoEndpoint);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Unable to load OIDC discovery document from " + discoveryUrl, exception);
        }
    }

    private IllegalStateException missingClientId(String providerId, String displayName) {
        return new IllegalStateException(
            displayName + " sign-in requires a client ID. Configure it in Settings > Authentication or pass -D"
                + LoginProviderSettings.systemProperty(providerId, "client-id") + "=..."
        );
    }

    private String readSecret(String providerId) {
        String propertyValue = System.getProperty(LoginProviderSettings.systemProperty(providerId, "client-secret"), "").trim();
        if (!propertyValue.isEmpty()) {
            return propertyValue;
        }
        return secretStore.getSecret(LoginProviderSettings.clientSecretStoreKey(providerId)).orElse("");
    }

    private String readString(String systemPropertyName, String settingKey, String defaultValue) {
        String propertyValue = System.getProperty(systemPropertyName, "").trim();
        if (!propertyValue.isEmpty()) {
            return propertyValue;
        }
        return storage.getEffectiveString(settingKey, defaultValue);
    }

    private boolean readBoolean(String systemPropertyName, String settingKey, boolean defaultValue) {
        String propertyValue = System.getProperty(systemPropertyName, "").trim();
        if (!propertyValue.isEmpty()) {
            return Boolean.parseBoolean(propertyValue);
        }
        for (SettingScope scope : SettingScope.resolutionOrder()) {
            Optional<String> rawValue = storage.getRaw(scope, settingKey);
            if (rawValue.isPresent()) {
                return Boolean.parseBoolean(rawValue.get());
            }
        }
        return defaultValue;
    }

    private Optional<String> readRaw(String key) {
        for (SettingScope scope : SettingScope.resolutionOrder()) {
            Optional<String> rawValue = storage.getRaw(scope, key);
            if (rawValue.isPresent()) {
                return rawValue;
            }
        }
        return Optional.empty();
    }

    private List<String> parseScopes(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return List.of(text.trim().split("[,\\s]+")).stream()
            .map(String::trim)
            .filter(scope -> !scope.isEmpty())
            .distinct()
            .toList();
    }

    private String normalizeEnterpriseUrl(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("/+$", "");
        if (normalized.isEmpty()
            || "https://github.com".equalsIgnoreCase(normalized)
            || "https://api.github.com".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private String resolveGitHubBaseUrl(String enterpriseUrl) {
        if (enterpriseUrl == null) {
            return "https://github.com";
        }
        int apiIndex = enterpriseUrl.indexOf("/api/");
        return apiIndex >= 0 ? enterpriseUrl.substring(0, apiIndex) : enterpriseUrl;
    }

    private String resolveGitHubApiBase(String enterpriseUrl) {
        if (enterpriseUrl == null) {
            return "https://api.github.com";
        }
        if (enterpriseUrl.contains("/api/")) {
            return enterpriseUrl;
        }
        return enterpriseUrl + "/api/v3";
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String stringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof String string && !string.isBlank()) {
            return string;
        }
        return null;
    }

    record ProviderRuntimeConfig(
        ProviderConfig config,
        String workspaceDomain,
        boolean preferDeviceFlow
    ) {
    }

    private record DiscoveryMetadata(
        String authorizationEndpoint,
        String tokenEndpoint,
        String userInfoEndpoint
    ) {
    }

    private static final class JsonMapParser {

        private JsonMapParser() {
        }

        static Map<String, Object> parse(String json) {
            return JsonParser.parseObject(json);
        }
    }

    private static final class JsonParser {

        private final String json;
        private int index;

        private JsonParser(String json) {
            this.json = json == null ? "" : json.trim();
        }

        static Map<String, Object> parseObject(String json) {
            JsonParser parser = new JsonParser(json);
            return parser.object();
        }

        private Map<String, Object> object() {
            if (peek() != '{') {
                return Map.of();
            }
            index++;
            java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
            skipWhitespace();
            while (peek() != '}' && peek() != '\0') {
                String key = string();
                skipWhitespace();
                if (peek() == ':') {
                    index++;
                }
                skipWhitespace();
                values.put(key, value());
                skipWhitespace();
                if (peek() == ',') {
                    index++;
                    skipWhitespace();
                }
            }
            if (peek() == '}') {
                index++;
            }
            return values;
        }

        private Object value() {
            return switch (peek()) {
                case '"' -> string();
                case '{' -> object();
                case '[' -> array();
                case 't', 'f' -> bool();
                case 'n' -> {
                    index = Math.min(index + 4, json.length());
                    yield null;
                }
                default -> number();
            };
        }

        private List<Object> array() {
            if (peek() != '[') {
                return List.of();
            }
            index++;
            java.util.List<Object> values = new java.util.ArrayList<>();
            skipWhitespace();
            while (peek() != ']' && peek() != '\0') {
                values.add(value());
                skipWhitespace();
                if (peek() == ',') {
                    index++;
                    skipWhitespace();
                }
            }
            if (peek() == ']') {
                index++;
            }
            return values;
        }

        private String string() {
            if (peek() != '"') {
                return "";
            }
            index++;
            StringBuilder builder = new StringBuilder();
            while (peek() != '"' && peek() != '\0') {
                char current = json.charAt(index++);
                if (current == '\\' && peek() != '\0') {
                    char escaped = json.charAt(index++);
                    builder.append(switch (escaped) {
                        case '"', '\\', '/' -> escaped;
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> escaped;
                    });
                } else {
                    builder.append(current);
                }
            }
            if (peek() == '"') {
                index++;
            }
            return builder.toString();
        }

        private Boolean bool() {
            if (json.regionMatches(true, index, "true", 0, 4)) {
                index += 4;
                return Boolean.TRUE;
            }
            index += 5;
            return Boolean.FALSE;
        }

        private Number number() {
            int start = index;
            while (peek() != '\0') {
                char current = peek();
                if (current == ',' || current == '}' || current == ']' || Character.isWhitespace(current)) {
                    break;
                }
                index++;
            }
            String token = json.substring(start, index);
            if (token.contains(".") || token.contains("e") || token.contains("E")) {
                return Double.parseDouble(token);
            }
            return Long.parseLong(token);
        }

        private void skipWhitespace() {
            while (Character.isWhitespace(peek())) {
                index++;
            }
        }

        private char peek() {
            return index >= json.length() ? '\0' : json.charAt(index);
        }
    }
}
