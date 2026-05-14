package org.metalib.papifly.fx.login.core;

import javafx.beans.property.ObjectProperty;
import org.metalib.papifly.fx.login.idapi.AuthorizationRequest;
import org.metalib.papifly.fx.login.idapi.DeviceCodeResponse;
import org.metalib.papifly.fx.login.idapi.IdentityProvider;
import org.metalib.papifly.fx.login.idapi.ProviderConfig;
import org.metalib.papifly.fx.login.idapi.TokenResponse;
import org.metalib.papifly.fx.login.idapi.oauth.OAuthStateStore;
import org.metalib.papifly.fx.login.session.AuthState;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class OAuthFlowExecutor {

    private final DefaultAuthSessionBroker.BrowserLauncher browserLauncher;
    private final DefaultAuthSessionBroker.CallbackServerFactory callbackServerFactory;
    private final OAuthStateStore oauthStateStore;
    private final ObjectProperty<AuthState> authState;
    private final ObjectProperty<DeviceCodeResponse> deviceCode;

    OAuthFlowExecutor(
        DefaultAuthSessionBroker.BrowserLauncher browserLauncher,
        DefaultAuthSessionBroker.CallbackServerFactory callbackServerFactory,
        OAuthStateStore oauthStateStore,
        ObjectProperty<AuthState> authState,
        ObjectProperty<DeviceCodeResponse> deviceCode
    ) {
        this.browserLauncher = browserLauncher;
        this.callbackServerFactory = callbackServerFactory;
        this.oauthStateStore = oauthStateStore;
        this.authState = authState;
        this.deviceCode = deviceCode;
    }

    CompletableFuture<Map<String, String>> startAuthorizationCodeFlow(AuthorizationRequestFactory requestFactory) {
        authState.set(AuthState.INITIATING_AUTH);
        final DefaultAuthSessionBroker.CallbackServerHandle callbackServer;
        try {
            callbackServer = callbackServerFactory.start();
        } catch (IOException exception) {
            authState.set(AuthState.ERROR);
            return CompletableFuture.failedFuture(new IllegalStateException("Unable to start the local OAuth callback server.", exception));
        }

        final AuthorizationRequest request;
        try {
            request = requestFactory.build(callbackServer.redirectUri());
            oauthStateStore.store(request.state(), request.nonce(), request.codeVerifier(), request.redirectUri());
            browserLauncher.open(URI.create(request.authUrl()));
        } catch (Exception exception) {
            callbackServer.close();
            authState.set(AuthState.ERROR);
            return CompletableFuture.failedFuture(new IllegalStateException(
                "Unable to open the system browser. Retry after fixing desktop browser integration.",
                exception
            ));
        }

        authState.set(AuthState.AWAITING_CALLBACK);
        return callbackServer.callbackParams()
            .whenComplete((ignored, error) -> {
                callbackServer.close();
                if (error != null) {
                    authState.set(AuthState.ERROR);
                }
            });
    }

    CompletableFuture<TokenResponse> startDeviceFlow(IdentityProvider provider, ProviderConfig config) {
        authState.set(AuthState.INITIATING_AUTH);
        return provider.requestDeviceCode(config)
            .thenCompose(response -> {
                deviceCode.set(response);
                openDeviceVerification(response);
                authState.set(AuthState.POLLING_DEVICE);
                return pollForDeviceToken(provider, config, response);
            })
            .whenComplete((ignored, error) -> {
                if (error != null) {
                    deviceCode.set(null);
                    authState.set(AuthState.ERROR);
                }
            });
    }

    OAuthStateStore.StateEntry consumeState(String state) {
        return oauthStateStore.consume(state)
            .orElseThrow(() -> new IllegalStateException("The OAuth callback state did not match the sign-in request."));
    }

    private CompletableFuture<TokenResponse> pollForDeviceToken(
        IdentityProvider provider,
        ProviderConfig config,
        DeviceCodeResponse deviceCodeResponse
    ) {
        long intervalSeconds = deviceCodeResponse.interval() > 0 ? deviceCodeResponse.interval() : 5;
        Instant deadline = Instant.now().plusSeconds(Math.max(deviceCodeResponse.expiresIn(), intervalSeconds));
        return pollForDeviceToken(provider, config, deviceCodeResponse.deviceCode(), intervalSeconds, deadline);
    }

    private CompletableFuture<TokenResponse> pollForDeviceToken(
        IdentityProvider provider,
        ProviderConfig config,
        String deviceCodeValue,
        long intervalSeconds,
        Instant deadline
    ) {
        return provider.pollDeviceToken(config, deviceCodeValue)
            .thenCompose(tokenResponse -> {
                if (tokenResponse.accessToken() != null && !tokenResponse.accessToken().isBlank()) {
                    return CompletableFuture.completedFuture(tokenResponse);
                }
                if (!Instant.now().isBefore(deadline)) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Device authorization expired before it was approved."));
                }
                return CompletableFuture.runAsync(
                    () -> { },
                    CompletableFuture.delayedExecutor(intervalSeconds, TimeUnit.SECONDS)
                ).thenCompose(ignored -> pollForDeviceToken(provider, config, deviceCodeValue, intervalSeconds, deadline));
            });
    }

    private void openDeviceVerification(DeviceCodeResponse deviceCodeResponse) {
        String uri = deviceCodeResponse.verificationUriComplete() != null && !deviceCodeResponse.verificationUriComplete().isBlank()
            ? deviceCodeResponse.verificationUriComplete()
            : deviceCodeResponse.verificationUri();
        if (uri == null || uri.isBlank() || deviceCodeResponse.userCode() == null || deviceCodeResponse.userCode().isBlank()) {
            throw new IllegalStateException("The provider did not return a valid device verification URL and user code.");
        }
        try {
            browserLauncher.open(URI.create(uri));
        } catch (Exception exception) {
            // Device flow can continue without automatic browser launch because the UI shows the URL and user code.
        }
    }

    @FunctionalInterface
    interface AuthorizationRequestFactory {

        AuthorizationRequest build(String redirectUri) throws Exception;
    }
}
