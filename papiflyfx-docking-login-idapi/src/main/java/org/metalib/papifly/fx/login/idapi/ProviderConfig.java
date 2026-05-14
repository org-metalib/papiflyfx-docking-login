package org.metalib.papifly.fx.login.idapi;

import java.util.List;
import java.util.Map;

public record ProviderConfig(
    String clientId,
    String clientSecret,
    List<String> scopes,
    String discoveryUrl,
    String authorizationEndpoint,
    String tokenEndpoint,
    String userInfoEndpoint,
    String enterpriseUrl,
    Map<String, String> authorizationParameters
) {

    public ProviderConfig {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        authorizationParameters = authorizationParameters == null ? Map.of() : Map.copyOf(authorizationParameters);
    }

    public static ProviderConfig ofClientId(String clientId) {
        return new ProviderConfig(clientId, null, List.of(), null, null, null, null, null, Map.of());
    }

    public static ProviderConfig ofClientIdAndScopes(String clientId, List<String> scopes) {
        return new ProviderConfig(clientId, null, scopes, null, null, null, null, null, Map.of());
    }

    public ProviderConfig withAuthorizationParameters(Map<String, String> newAuthorizationParameters) {
        return new ProviderConfig(
            clientId,
            clientSecret,
            scopes,
            discoveryUrl,
            authorizationEndpoint,
            tokenEndpoint,
            userInfoEndpoint,
            enterpriseUrl,
            newAuthorizationParameters
        );
    }
}
