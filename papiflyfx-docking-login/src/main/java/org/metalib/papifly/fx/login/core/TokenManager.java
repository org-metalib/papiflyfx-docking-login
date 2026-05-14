package org.metalib.papifly.fx.login.core;

import org.metalib.papifly.fx.login.idapi.ProviderConfig;
import org.metalib.papifly.fx.login.idapi.TokenResponse;
import org.metalib.papifly.fx.login.idapi.UserPrincipal;
import org.metalib.papifly.fx.login.session.AuthSession;
import org.metalib.papifly.fx.settings.api.SecretKeyNames;
import org.metalib.papifly.fx.settings.api.SecretStore;

import java.util.Map;

final class TokenManager {

    private static final String GOOGLE_PROVIDER_ID = "google";

    private final SecretStore secretStore;
    private final Map<String, DefaultAuthSessionBroker.SessionTokenState> sessionTokens;

    TokenManager(SecretStore secretStore, Map<String, DefaultAuthSessionBroker.SessionTokenState> sessionTokens) {
        this.secretStore = secretStore;
        this.sessionTokens = sessionTokens;
    }

    String refreshToken(AuthSession session) {
        DefaultAuthSessionBroker.SessionTokenState tokenState = sessionTokens.get(key(session.providerId(), session.subject()));
        if (tokenState != null && tokenState.refreshToken() != null) {
            return tokenState.refreshToken();
        }
        return secretStore.getSecret(SecretKeyNames.oauthRefreshToken(session.providerId(), session.subject())).orElse(null);
    }

    String revokeToken(AuthSession session) {
        DefaultAuthSessionBroker.SessionTokenState tokenState = sessionTokens.get(key(session.providerId(), session.subject()));
        if (tokenState != null && tokenState.accessToken() != null) {
            return tokenState.accessToken();
        }
        return secretStore.getSecret(SecretKeyNames.oauthRefreshToken(session.providerId(), session.subject())).orElse(null);
    }

    void storeSessionTokens(
        String providerId,
        UserPrincipal principal,
        TokenResponse tokenResponse,
        ProviderConfig config
    ) {
        String refreshToken = tokenResponse.refreshToken();
        if (refreshToken != null && !refreshToken.isBlank()) {
            secretStore.setSecret(SecretKeyNames.oauthRefreshToken(providerId, principal.subject()), refreshToken);
        } else {
            refreshToken = secretStore.getSecret(SecretKeyNames.oauthRefreshToken(providerId, principal.subject())).orElse(null);
        }
        sessionTokens.put(key(providerId, principal.subject()), new DefaultAuthSessionBroker.SessionTokenState(
            tokenResponse.accessToken(),
            refreshToken,
            tokenResponse.tokenType(),
            tokenResponse.idToken(),
            config
        ));
    }

    void removeSessionTokens(String providerId, String subject) {
        sessionTokens.remove(key(providerId, subject));
    }

    boolean shouldRetryGoogleWithConsent(String providerId, TokenResponse tokenResponse, UserPrincipal principal) {
        if (!GOOGLE_PROVIDER_ID.equals(providerId)) {
            return false;
        }
        if (tokenResponse.refreshToken() != null && !tokenResponse.refreshToken().isBlank()) {
            return false;
        }
        return secretStore.getSecret(SecretKeyNames.oauthRefreshToken(providerId, principal.subject()))
            .map(String::isBlank)
            .orElse(true);
    }

    IllegalStateException googleOfflineAccessError() {
        return new IllegalStateException(
            "Google did not grant offline access. The session cannot be refreshed without a refresh token."
        );
    }

    private String key(String providerId, String subject) {
        return providerId + ':' + subject;
    }
}
