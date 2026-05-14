package org.metalib.papifly.fx.login.idapi.providers;

import org.metalib.papifly.fx.login.idapi.AuthorizationRequest;
import org.metalib.papifly.fx.login.idapi.CodeExchangeRequest;
import org.metalib.papifly.fx.login.idapi.IdentityProvider;
import org.metalib.papifly.fx.login.idapi.ProviderCapabilities;
import org.metalib.papifly.fx.login.idapi.ProviderConfig;
import org.metalib.papifly.fx.login.idapi.ProviderDescriptor;
import org.metalib.papifly.fx.login.idapi.TokenResponse;
import org.metalib.papifly.fx.login.idapi.UserPrincipal;
import org.metalib.papifly.fx.login.idapi.oauth.PkceGenerator;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

public class GenericOidcProvider implements IdentityProvider {

    public static final String PROVIDER_ID = "generic-oidc";

    private final HttpClient httpClient;

    public GenericOidcProvider() {
        this.httpClient = HttpClient.newBuilder().build();
    }

    protected GenericOidcProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public ProviderDescriptor descriptor() {
        return new ProviderDescriptor(
            providerId(),
            displayName(),
            null,
            defaultScopes(),
            capabilities()
        );
    }

    protected String providerId() {
        return PROVIDER_ID;
    }

    protected String displayName() {
        return "Generic OIDC";
    }

    protected List<String> defaultScopes() {
        return List.of("openid", "profile", "email");
    }

    protected ProviderCapabilities capabilities() {
        return new ProviderCapabilities(true, false, false, true, false);
    }

    protected String authorizationEndpoint(ProviderConfig config) {
        return config.authorizationEndpoint() != null ? config.authorizationEndpoint() : "";
    }

    protected String tokenEndpoint(ProviderConfig config) {
        return config.tokenEndpoint() != null ? config.tokenEndpoint() : "";
    }

    protected String userInfoEndpoint(ProviderConfig config) {
        return config.userInfoEndpoint() != null ? config.userInfoEndpoint() : "";
    }

    @Override
    public AuthorizationRequest buildAuthorizationRequest(ProviderConfig config, String redirectUri) {
        String state = PkceGenerator.generateState();
        String nonce = PkceGenerator.generateNonce();
        String codeVerifier = PkceGenerator.generateCodeVerifier();
        String codeChallenge = PkceGenerator.generateCodeChallenge(codeVerifier);

        List<String> scopes = config.scopes().isEmpty() ? defaultScopes() : config.scopes();
        String scopeParam = String.join(" ", scopes);

        StringJoiner query = new StringJoiner("&");
        query.add("response_type=code");
        query.add("client_id=" + encode(config.clientId()));
        query.add("redirect_uri=" + encode(redirectUri));
        query.add("scope=" + encode(scopeParam));
        query.add("state=" + encode(state));
        query.add("nonce=" + encode(nonce));
        query.add("code_challenge=" + encode(codeChallenge));
        query.add("code_challenge_method=S256");
        config.authorizationParameters().forEach((key, value) ->
            query.add(encode(key) + "=" + encode(value)));

        String authUrl = authorizationEndpoint(config) + "?" + query;

        return new AuthorizationRequest(authUrl, state, nonce, codeVerifier, redirectUri);
    }

    @Override
    public CompletableFuture<TokenResponse> exchangeCode(ProviderConfig config, CodeExchangeRequest request) {
        String body = "grant_type=authorization_code"
            + "&code=" + encode(request.code())
            + "&redirect_uri=" + encode(request.redirectUri())
            + "&client_id=" + encode(config.clientId())
            + "&code_verifier=" + encode(request.codeVerifier());

        if (config.clientSecret() != null && !config.clientSecret().isBlank()) {
            body += "&client_secret=" + encode(config.clientSecret());
        }

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(tokenEndpoint(config)))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> parseTokenResponse(response.body()));
    }

    @Override
    public CompletableFuture<UserPrincipal> fetchUserPrincipal(ProviderConfig config, String accessToken) {
        String endpoint = userInfoEndpoint(config);
        if (endpoint == null || endpoint.isBlank()) {
            return CompletableFuture.completedFuture(
                new UserPrincipal("unknown", "Unknown", "", "")
            );
        }
        return fetchUserInfo(endpoint, accessToken);
    }

    @Override
    public CompletableFuture<TokenResponse> refreshToken(ProviderConfig config, String refreshToken) {
        String body = "grant_type=refresh_token"
            + "&refresh_token=" + encode(refreshToken)
            + "&client_id=" + encode(config.clientId());

        if (config.clientSecret() != null && !config.clientSecret().isBlank()) {
            body += "&client_secret=" + encode(config.clientSecret());
        }

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(tokenEndpoint(config)))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> parseTokenResponse(response.body()));
    }

    protected CompletableFuture<UserPrincipal> fetchUserInfo(String endpoint, String accessToken) {
        if (endpoint == null || endpoint.isBlank()) {
            return CompletableFuture.completedFuture(new UserPrincipal("unknown", "Unknown", "", ""));
        }
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/json")
            .GET()
            .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> parseUserPrincipal(response.body()));
    }

    protected TokenResponse parseTokenResponse(String json) {
        Map<String, Object> map = SimpleJsonParser.parse(json);
        String error = stringOrNull(map, "error");
        if (error != null && !error.isBlank()) {
            String description = stringOrNull(map, "error_description");
            throw new IllegalStateException(description == null || description.isBlank()
                ? error
                : error + ": " + description);
        }
        return new TokenResponse(
            stringOrNull(map, "access_token"),
            stringOrNull(map, "refresh_token"),
            stringOrNull(map, "id_token"),
            stringOrNull(map, "token_type"),
            longOrZero(map, "expires_in"),
            stringOrNull(map, "scope")
        );
    }

    protected UserPrincipal parseUserPrincipal(String json) {
        Map<String, Object> map = SimpleJsonParser.parse(json);
        String sub = stringOrNull(map, "sub");
        if (sub == null) sub = stringOrNull(map, "id");
        if (sub == null) sub = String.valueOf(map.getOrDefault("id", "unknown"));
        return new UserPrincipal(
            sub,
            stringOrNull(map, "name"),
            stringOrNull(map, "email"),
            stringOrNull(map, "picture"),
            map
        );
    }

    protected HttpClient httpClient() {
        return httpClient;
    }

    protected static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stringOrNull(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof String s ? s : null;
    }

    private static long longOrZero(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }
}
