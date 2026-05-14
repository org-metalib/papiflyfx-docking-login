package org.metalib.papifly.fx.login.idapi;

import java.util.concurrent.CompletableFuture;

public interface IdentityProvider {

    ProviderDescriptor descriptor();

    AuthorizationRequest buildAuthorizationRequest(ProviderConfig config, String redirectUri);

    CompletableFuture<TokenResponse> exchangeCode(ProviderConfig config, CodeExchangeRequest request);

    CompletableFuture<UserPrincipal> fetchUserPrincipal(ProviderConfig config, String accessToken);

    default CompletableFuture<TokenResponse> refreshToken(ProviderConfig config, String refreshToken) {
        return CompletableFuture.failedFuture(
            new UnsupportedOperationException("Refresh token grant not supported by " + descriptor().providerId()));
    }

    default CompletableFuture<DeviceCodeResponse> requestDeviceCode(ProviderConfig config) {
        return CompletableFuture.failedFuture(
            new UnsupportedOperationException("Device flow not supported by " + descriptor().providerId()));
    }

    default CompletableFuture<TokenResponse> pollDeviceToken(ProviderConfig config, String deviceCode) {
        return CompletableFuture.failedFuture(
            new UnsupportedOperationException("Device flow not supported by " + descriptor().providerId()));
    }

    default CompletableFuture<Void> revokeToken(ProviderConfig config, String token) {
        return CompletableFuture.completedFuture(null);
    }
}
