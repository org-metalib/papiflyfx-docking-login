package org.metalib.papifly.fx.login.idapi.providers;

import org.metalib.papifly.fx.login.idapi.ProviderCapabilities;
import org.metalib.papifly.fx.login.idapi.ProviderConfig;
import org.metalib.papifly.fx.login.idapi.UserPrincipal;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GoogleProvider extends GenericOidcProvider {

    public static final String PROVIDER_ID = "google";
    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_ENDPOINT = "https://openidconnect.googleapis.com/v1/userinfo";

    @Override
    protected String providerId() {
        return PROVIDER_ID;
    }

    @Override
    protected String displayName() {
        return "Google";
    }

    @Override
    protected List<String> defaultScopes() {
        return List.of("openid", "email", "profile");
    }

    @Override
    protected ProviderCapabilities capabilities() {
        return new ProviderCapabilities(true, false, true, true, false);
    }

    @Override
    protected String authorizationEndpoint(ProviderConfig config) {
        return config.authorizationEndpoint() != null ? config.authorizationEndpoint() : AUTH_ENDPOINT;
    }

    @Override
    protected String tokenEndpoint(ProviderConfig config) {
        return config.tokenEndpoint() != null ? config.tokenEndpoint() : TOKEN_ENDPOINT;
    }

    @Override
    protected String userInfoEndpoint(ProviderConfig config) {
        return config.userInfoEndpoint() != null ? config.userInfoEndpoint() : USERINFO_ENDPOINT;
    }

    @Override
    public CompletableFuture<UserPrincipal> fetchUserPrincipal(ProviderConfig config, String accessToken) {
        return fetchUserInfo(userInfoEndpoint(config), accessToken);
    }

    @Override
    public CompletableFuture<Void> revokeToken(ProviderConfig config, String token) {
        var request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create("https://oauth2.googleapis.com/revoke?token=" + encode(token)))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
            .build();

        return httpClient().sendAsync(request, java.net.http.HttpResponse.BodyHandlers.discarding())
            .thenApply(r -> null);
    }
}
