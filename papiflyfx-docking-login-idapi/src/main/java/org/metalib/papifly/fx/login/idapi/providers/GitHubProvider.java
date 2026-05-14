package org.metalib.papifly.fx.login.idapi.providers;

import org.metalib.papifly.fx.login.idapi.DeviceCodeResponse;
import org.metalib.papifly.fx.login.idapi.ProviderCapabilities;
import org.metalib.papifly.fx.login.idapi.ProviderConfig;
import org.metalib.papifly.fx.login.idapi.TokenResponse;
import org.metalib.papifly.fx.login.idapi.UserPrincipal;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GitHubProvider extends GenericOidcProvider {

    public static final String PROVIDER_ID = "github";
    private static final String AUTH_ENDPOINT = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_ENDPOINT = "https://github.com/login/oauth/access_token";
    private static final String USERINFO_ENDPOINT = "https://api.github.com/user";
    private static final String DEVICE_CODE_ENDPOINT = "https://github.com/login/device/code";

    @Override
    protected String providerId() {
        return PROVIDER_ID;
    }

    @Override
    protected String displayName() {
        return "GitHub";
    }

    @Override
    protected List<String> defaultScopes() {
        return List.of("repo", "read:user");
    }

    @Override
    protected ProviderCapabilities capabilities() {
        return new ProviderCapabilities(true, true, true, false, false);
    }

    @Override
    protected String authorizationEndpoint(ProviderConfig config) {
        String base = resolveBaseUrl(config);
        if (!base.equals("https://github.com")) {
            return base + "/login/oauth/authorize";
        }
        return config.authorizationEndpoint() != null ? config.authorizationEndpoint() : AUTH_ENDPOINT;
    }

    @Override
    protected String tokenEndpoint(ProviderConfig config) {
        String base = resolveBaseUrl(config);
        if (!base.equals("https://github.com")) {
            return base + "/login/oauth/access_token";
        }
        return config.tokenEndpoint() != null ? config.tokenEndpoint() : TOKEN_ENDPOINT;
    }

    @Override
    protected String userInfoEndpoint(ProviderConfig config) {
        if (config.enterpriseUrl() != null && !config.enterpriseUrl().isBlank()) {
            String apiBase = config.enterpriseUrl().replaceAll("/$", "");
            return apiBase + "/user";
        }
        return config.userInfoEndpoint() != null ? config.userInfoEndpoint() : USERINFO_ENDPOINT;
    }

    @Override
    public CompletableFuture<UserPrincipal> fetchUserPrincipal(ProviderConfig config, String accessToken) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(userInfoEndpoint(config)))
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/json")
            .GET()
            .build();

        return httpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                Map<String, Object> map = SimpleJsonParser.parse(response.body());
                String id = String.valueOf(map.getOrDefault("id", map.getOrDefault("login", "unknown")));
                return new UserPrincipal(
                    id,
                    stringVal(map, "name"),
                    stringVal(map, "email"),
                    stringVal(map, "avatar_url"),
                    map
                );
            });
    }

    @Override
    public CompletableFuture<DeviceCodeResponse> requestDeviceCode(ProviderConfig config) {
        List<String> scopes = config.scopes().isEmpty() ? defaultScopes() : config.scopes();
        String body = "client_id=" + encode(config.clientId()) + "&scope=" + encode(String.join(" ", scopes));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(deviceCodeEndpoint(config)))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        return httpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(this::parseDeviceCodeResponse);
    }

    @Override
    public CompletableFuture<TokenResponse> pollDeviceToken(ProviderConfig config, String deviceCode) {
        String body = "client_id=" + encode(config.clientId())
            + "&device_code=" + encode(deviceCode)
            + "&grant_type=urn:ietf:params:oauth:grant-type:device_code";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(tokenEndpoint(config)))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        return httpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                Map<String, Object> map = SimpleJsonParser.parse(response.body());
                String error = stringVal(map, "error");
                if ("authorization_pending".equalsIgnoreCase(error) || "slow_down".equalsIgnoreCase(error)) {
                    return new TokenResponse(null, null, null, null, 0, null);
                }
                if (error != null && !error.isBlank()) {
                    String description = stringVal(map, "error_description");
                    throw new IllegalStateException(description == null || description.isBlank()
                        ? error
                        : error + ": " + description);
                }
                return parseTokenResponse(response.body());
            });
    }

    @Override
    public CompletableFuture<Void> revokeToken(ProviderConfig config, String token) {
        String apiBase = resolveApiBaseUrl(config);
        String body = "{\"access_token\":\"" + token + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBase + "/applications/" + config.clientId() + "/token"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .method("DELETE", HttpRequest.BodyPublishers.ofString(body))
            .build();

        return httpClient().sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .thenApply(r -> null);
    }

    private String resolveBaseUrl(ProviderConfig config) {
        if (config.enterpriseUrl() != null && !config.enterpriseUrl().isBlank()) {
            String url = config.enterpriseUrl().replaceAll("/$", "");
            if (url.contains("/api/")) {
                url = url.substring(0, url.indexOf("/api/"));
            }
            return url;
        }
        return "https://github.com";
    }

    private String resolveApiBaseUrl(ProviderConfig config) {
        if (config.enterpriseUrl() != null && !config.enterpriseUrl().isBlank()) {
            return config.enterpriseUrl().replaceAll("/$", "");
        }
        return "https://api.github.com";
    }

    private String deviceCodeEndpoint(ProviderConfig config) {
        String base = resolveBaseUrl(config);
        if (!base.equals("https://github.com")) {
            return base + "/login/device/code";
        }
        return DEVICE_CODE_ENDPOINT;
    }

    private static String stringVal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof String s ? s : (v != null ? String.valueOf(v) : null);
    }

    private DeviceCodeResponse parseDeviceCodeResponse(HttpResponse<String> response) {
        Map<String, Object> map = SimpleJsonParser.parse(response.body());
        String error = stringVal(map, "error");
        if (error != null && !error.isBlank()) {
            String description = stringVal(map, "error_description");
            throw new IllegalStateException(description == null || description.isBlank()
                ? error
                : error + ": " + description);
        }

        DeviceCodeResponse deviceCode = new DeviceCodeResponse(
            stringVal(map, "device_code"),
            stringVal(map, "user_code"),
            stringVal(map, "verification_uri"),
            stringVal(map, "verification_uri_complete"),
            longVal(map, "expires_in"),
            intVal(map, "interval")
        );
        if (isBlank(deviceCode.deviceCode()) || isBlank(deviceCode.userCode()) || isBlank(deviceCode.verificationUri())) {
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("GitHub device authorization failed with HTTP " + response.statusCode() + '.');
            }
            throw new IllegalStateException("GitHub did not return a valid device authorization response.");
        }
        return deviceCode;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static long longVal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) { try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; } }
        return 0;
    }

    private static int intVal(Map<String, Object> map, String key) {
        return (int) longVal(map, key);
    }
}
