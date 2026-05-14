package org.metalib.papifly.fx.login.core;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.metalib.papifly.fx.login.api.AuthSessionAdmin;
import org.metalib.papifly.fx.login.api.AuthSessionBroker;
import org.metalib.papifly.fx.login.idapi.AuthorizationRequest;
import org.metalib.papifly.fx.login.idapi.CodeExchangeRequest;
import org.metalib.papifly.fx.login.idapi.DeviceCodeResponse;
import org.metalib.papifly.fx.login.idapi.IdentityProvider;
import org.metalib.papifly.fx.login.idapi.ProviderConfig;
import org.metalib.papifly.fx.login.idapi.ProviderRegistry;
import org.metalib.papifly.fx.login.idapi.TokenResponse;
import org.metalib.papifly.fx.login.idapi.UserPrincipal;
import org.metalib.papifly.fx.login.idapi.oauth.LoopbackCallbackServer;
import org.metalib.papifly.fx.login.idapi.oauth.OAuthStateStore;
import org.metalib.papifly.fx.login.session.AuthSession;
import org.metalib.papifly.fx.login.session.AuthState;
import org.metalib.papifly.fx.settings.api.SecretKeyNames;
import org.metalib.papifly.fx.settings.api.SecretStore;
import org.metalib.papifly.fx.settings.api.SettingsStorage;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public class DefaultAuthSessionBroker implements AuthSessionBroker, AuthSessionAdmin {

    private final Map<String, AuthSession> sessions = new LinkedHashMap<>();
    private final Map<String, SessionTokenState> sessionTokens = new LinkedHashMap<>();
    private final SimpleObjectProperty<AuthState> authState = new SimpleObjectProperty<>(AuthState.UNAUTHENTICATED);
    private final SimpleObjectProperty<AuthSession> activeSession = new SimpleObjectProperty<>();
    private final SimpleObjectProperty<DeviceCodeResponse> deviceCode = new SimpleObjectProperty<>();
    private final OAuthStateStore oauthStateStore = new OAuthStateStore();

    private final ProviderRegistry providerRegistry;
    private final ProviderSettingsResolver settingsResolver;
    private final SecretStore secretStore;
    private final BrowserLauncher browserLauncher;
    private final CallbackServerFactory callbackServerFactory;
    private final TokenManager tokenManager;
    private final SessionPersistenceService sessionPersistenceService;
    private final OAuthFlowExecutor oauthFlowExecutor;

    public DefaultAuthSessionBroker() {
        this.providerRegistry = null;
        this.settingsResolver = null;
        this.secretStore = null;
        this.browserLauncher = BrowserLauncher.desktop();
        this.callbackServerFactory = CallbackServerFactory.loopback();
        this.tokenManager = null;
        this.sessionPersistenceService = new SessionPersistenceService(
            this,
            sessions,
            authState,
            activeSession,
            deviceCode,
            null
        );
        this.oauthFlowExecutor = new OAuthFlowExecutor(browserLauncher, callbackServerFactory, oauthStateStore, authState, deviceCode);
    }

    public DefaultAuthSessionBroker(ProviderRegistry providerRegistry, SettingsStorage settingsStorage, SecretStore secretStore) {
        this(providerRegistry, settingsStorage, secretStore, BrowserLauncher.desktop(), CallbackServerFactory.loopback());
    }

    DefaultAuthSessionBroker(
        ProviderRegistry providerRegistry,
        SettingsStorage settingsStorage,
        SecretStore secretStore,
        BrowserLauncher browserLauncher
    ) {
        this(providerRegistry, settingsStorage, secretStore, browserLauncher, CallbackServerFactory.loopback());
    }

    DefaultAuthSessionBroker(
        ProviderRegistry providerRegistry,
        SettingsStorage settingsStorage,
        SecretStore secretStore,
        BrowserLauncher browserLauncher,
        CallbackServerFactory callbackServerFactory
    ) {
        this.providerRegistry = providerRegistry;
        this.settingsResolver = new ProviderSettingsResolver(settingsStorage, secretStore);
        this.secretStore = secretStore;
        this.browserLauncher = browserLauncher;
        this.callbackServerFactory = callbackServerFactory;
        this.tokenManager = new TokenManager(secretStore, sessionTokens);
        this.sessionPersistenceService = new SessionPersistenceService(
            this,
            sessions,
            authState,
            activeSession,
            deviceCode,
            tokenManager
        );
        this.oauthFlowExecutor = new OAuthFlowExecutor(browserLauncher, callbackServerFactory, oauthStateStore, authState, deviceCode);
    }

    public boolean isConfiguredForOAuth() {
        return providerRegistry != null && settingsResolver != null && secretStore != null;
    }

    @Override
    public synchronized CompletableFuture<AuthSession> signIn(String providerId) {
        deviceCode.set(null);
        if (!isConfiguredForOAuth()) {
            return CompletableFuture.completedFuture(sessionPersistenceService.signInStub(providerId));
        }

        try {
            IdentityProvider provider = provider(providerId);
            ProviderSettingsResolver.ProviderRuntimeConfig runtimeConfig = resolveConfig(providerId, provider);
            if (runtimeConfig.preferDeviceFlow() && provider.descriptor().capabilities().supportsDeviceFlow()) {
                return signInWithDeviceFlow(providerId, provider, runtimeConfig);
            }
            if (!provider.descriptor().capabilities().supportsAuthCodePkce()
                && provider.descriptor().capabilities().supportsDeviceFlow()) {
                return signInWithDeviceFlow(providerId, provider, runtimeConfig);
            }
            return signInWithAuthorizationCode(providerId, provider, runtimeConfig);
        } catch (RuntimeException exception) {
            authState.set(AuthState.ERROR);
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public synchronized CompletableFuture<AuthSession> signInWithDeviceFlow(String providerId) {
        deviceCode.set(null);
        if (!isConfiguredForOAuth()) {
            authState.set(AuthState.POLLING_DEVICE);
            return CompletableFuture.completedFuture(sessionPersistenceService.signInStub(providerId));
        }

        try {
            IdentityProvider provider = provider(providerId);
            ProviderSettingsResolver.ProviderRuntimeConfig runtimeConfig = resolveConfig(providerId, provider);
            return signInWithDeviceFlow(providerId, provider, runtimeConfig);
        } catch (RuntimeException exception) {
            authState.set(AuthState.ERROR);
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public synchronized CompletableFuture<AuthSession> refresh(boolean force) {
        if (!isConfiguredForOAuth()) {
            return CompletableFuture.completedFuture(sessionPersistenceService.refreshStub());
        }

        AuthSession session = sessionPersistenceService.activeSession().orElse(null);
        if (session == null) {
            authState.set(AuthState.ERROR);
            return CompletableFuture.failedFuture(new IllegalStateException("No active session."));
        }

        String refreshToken = tokenManager.refreshToken(session);
        if ((refreshToken == null || refreshToken.isBlank()) && !force && !session.isExpired(Instant.now())) {
            return CompletableFuture.completedFuture(session);
        }
        if ((refreshToken == null || refreshToken.isBlank()) && !session.isExpired(Instant.now())) {
            return CompletableFuture.completedFuture(session);
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            authState.set(AuthState.EXPIRED);
            return CompletableFuture.failedFuture(new IllegalStateException("No refresh token is available. Sign in again."));
        }

        try {
            IdentityProvider provider = provider(session.providerId());
            ProviderSettingsResolver.ProviderRuntimeConfig runtimeConfig = resolveConfig(session.providerId(), provider);
            authState.set(AuthState.REFRESHING);

            return provider.refreshToken(runtimeConfig.config(), refreshToken)
                .handle((tokenResponse, error) -> {
                    if (error != null) {
                        if (!session.isExpired(Instant.now())) {
                            synchronized (this) {
                                authState.set(AuthState.AUTHENTICATED);
                            }
                            return CompletableFuture.completedFuture(session);
                        }
                        synchronized (this) {
                            authState.set(AuthState.ERROR);
                        }
                        return CompletableFuture.<AuthSession>failedFuture(rootCause(error));
                    }
                    String accessToken = tokenResponse.accessToken();
                    if (accessToken == null || accessToken.isBlank()) {
                        return CompletableFuture.completedFuture(session);
                    }
                    return provider.fetchUserPrincipal(runtimeConfig.config(), accessToken)
                        .thenApply(principal -> persistAuthenticatedSession(
                            session.providerId(),
                            runtimeConfig,
                            tokenResponse,
                            principal,
                            session
                        ));
                })
                .thenCompose(future -> future);
        } catch (RuntimeException exception) {
            authState.set(AuthState.ERROR);
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public synchronized CompletableFuture<Void> logout(boolean revokeRemote) {
        deviceCode.set(null);
        if (!isConfiguredForOAuth()) {
            activeSession.set(null);
            authState.set(AuthState.SIGNED_OUT);
            return CompletableFuture.completedFuture(null);
        }

        AuthSession session = sessionPersistenceService.activeSession().orElse(null);
        if (session == null) {
            authState.set(AuthState.SIGNED_OUT);
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> revokeFuture = CompletableFuture.completedFuture(null);
        if (revokeRemote) {
            String token = tokenManager.revokeToken(session);
            if (token != null && !token.isBlank()) {
                try {
                    IdentityProvider provider = provider(session.providerId());
                    ProviderSettingsResolver.ProviderRuntimeConfig runtimeConfig = resolveConfig(session.providerId(), provider);
                    revokeFuture = provider.revokeToken(runtimeConfig.config(), token);
                } catch (RuntimeException exception) {
                    revokeFuture = CompletableFuture.failedFuture(exception);
                }
            }
        }

        return revokeFuture.handle((ignored, error) -> {
            synchronized (this) {
                activeSession.set(null);
                authState.set(AuthState.SIGNED_OUT);
                if (revokeRemote) {
                    secretStore.clearSecret(SecretKeyNames.oauthRefreshToken(session.providerId(), session.subject()));
                    sessionPersistenceService.removeSession(session.providerId(), session.subject());
                }
            }
            if (error != null) {
                throw new CompletionException(rootCause(error));
            }
            return null;
        });
    }

    @Override
    public synchronized Optional<AuthSession> activeSession() {
        return sessionPersistenceService.activeSession();
    }

    @Override
    public synchronized List<AuthSession> allSessions() {
        return sessionPersistenceService.allSessions();
    }

    @Override
    public synchronized void setActiveSession(String providerId, String subject) {
        sessionPersistenceService.setActiveSession(providerId, subject);
    }

    @Override
    public ReadOnlyObjectProperty<AuthState> authStateProperty() {
        return authState;
    }

    @Override
    public ReadOnlyObjectProperty<AuthSession> sessionProperty() {
        return activeSession;
    }

    @Override
    public ReadOnlyObjectProperty<DeviceCodeResponse> deviceCodeProperty() {
        return deviceCode;
    }

    @Override
    public synchronized void upsertSession(AuthSession session) {
        sessionPersistenceService.upsertSession(session);
    }

    @Override
    public synchronized void removeSession(String providerId, String subject) {
        sessionPersistenceService.removeSession(providerId, subject);
    }

    private CompletableFuture<AuthSession> signInWithAuthorizationCode(
        String providerId,
        IdentityProvider provider,
        ProviderSettingsResolver.ProviderRuntimeConfig runtimeConfig
    ) {
        return signInWithAuthorizationCode(providerId, provider, runtimeConfig, false);
    }

    private CompletableFuture<AuthSession> signInWithAuthorizationCode(
        String providerId,
        IdentityProvider provider,
        ProviderSettingsResolver.ProviderRuntimeConfig runtimeConfig,
        boolean googleConsentRetry
    ) {
        return oauthFlowExecutor.startAuthorizationCodeFlow(redirectUri -> {
                ProviderSettingsResolver.ProviderRuntimeConfig attemptConfig = authorizationAttemptConfig(
                    providerId,
                    runtimeConfig,
                    googleConsentRetry
                );
                return provider.buildAuthorizationRequest(attemptConfig.config(), redirectUri);
            })
            .thenCompose(params -> exchangeAuthorizationCode(providerId, provider, runtimeConfig, params, googleConsentRetry))
            .whenComplete((session, error) -> {
                if (error != null) {
                    synchronized (this) {
                        authState.set(AuthState.ERROR);
                    }
                }
            });
    }

    private CompletableFuture<AuthSession> signInWithDeviceFlow(
        String providerId,
        IdentityProvider provider,
        ProviderSettingsResolver.ProviderRuntimeConfig runtimeConfig
    ) {
        return oauthFlowExecutor.startDeviceFlow(provider, runtimeConfig.config())
            .thenCompose(tokenResponse -> completeAuthentication(
                providerId,
                provider,
                runtimeConfig,
                tokenResponse,
                null,
                false
            ))
            .whenComplete((session, error) -> {
                if (error != null) {
                    synchronized (this) {
                        deviceCode.set(null);
                        authState.set(AuthState.ERROR);
                    }
                }
            });
    }

    private CompletableFuture<AuthSession> exchangeAuthorizationCode(
        String providerId,
        IdentityProvider provider,
        ProviderSettingsResolver.ProviderRuntimeConfig runtimeConfig,
        Map<String, String> params,
        boolean googleConsentRetry
    ) {
        String error = params.get("error");
        if (error != null && !error.isBlank()) {
            String description = params.getOrDefault("error_description", error);
            return CompletableFuture.failedFuture(new IllegalStateException(description));
        }

        String state = params.get("state");
        if (state == null || state.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Missing OAuth state in callback."));
        }

        OAuthStateStore.StateEntry stateEntry = oauthFlowExecutor.consumeState(state);

        String code = params.get("code");
        if (code == null || code.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Missing authorization code in callback."));
        }

        authState.set(AuthState.EXCHANGING_CODE);
        CodeExchangeRequest request = new CodeExchangeRequest(
            code,
            stateEntry.codeVerifier(),
            stateEntry.redirectUri(),
            state
        );

        ProviderSettingsResolver.ProviderRuntimeConfig attemptConfig = authorizationAttemptConfig(
            providerId,
            runtimeConfig,
            googleConsentRetry
        );
        return provider.exchangeCode(attemptConfig.config(), request)
            .thenCompose(tokenResponse -> completeAuthentication(
                providerId,
                provider,
                attemptConfig,
                tokenResponse,
                null,
                googleConsentRetry
            ));
    }

    private CompletableFuture<AuthSession> completeAuthentication(
        String providerId,
        IdentityProvider provider,
        ProviderSettingsResolver.ProviderRuntimeConfig runtimeConfig,
        TokenResponse tokenResponse,
        AuthSession previousSession,
        boolean googleConsentRetry
    ) {
        if (tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("The provider did not return an access token."));
        }
        return provider.fetchUserPrincipal(runtimeConfig.config(), tokenResponse.accessToken())
            .thenCompose(principal -> {
                if (shouldRetryGoogleWithConsent(providerId, tokenResponse, principal)) {
                    if (googleConsentRetry) {
                        return CompletableFuture.failedFuture(tokenManager.googleOfflineAccessError());
                    }
                    return signInWithAuthorizationCode(providerId, provider, runtimeConfig, true);
                }
                return CompletableFuture.completedFuture(
                    persistAuthenticatedSession(providerId, runtimeConfig, tokenResponse, principal, previousSession)
                );
            });
    }

    private AuthSession persistAuthenticatedSession(
        String providerId,
        ProviderSettingsResolver.ProviderRuntimeConfig runtimeConfig,
        TokenResponse tokenResponse,
        UserPrincipal principal,
        AuthSession previousSession
    ) {
        return sessionPersistenceService.persistAuthenticatedSession(
            providerId,
            runtimeConfig,
            tokenResponse,
            principal,
            previousSession
        );
    }

    private IdentityProvider provider(String providerId) {
        return providerRegistry.get(providerId)
            .orElseThrow(() -> new IllegalStateException("No login provider is registered for '" + providerId + "'."));
    }

    private ProviderSettingsResolver.ProviderRuntimeConfig resolveConfig(String providerId, IdentityProvider provider) {
        try {
            return settingsResolver.resolve(providerId, provider.descriptor());
        } catch (RuntimeException exception) {
            authState.set(AuthState.ERROR);
            throw exception;
        }
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private ProviderSettingsResolver.ProviderRuntimeConfig authorizationAttemptConfig(
        String providerId,
        ProviderSettingsResolver.ProviderRuntimeConfig runtimeConfig,
        boolean googleConsentRetry
    ) {
        if (!googleConsentRetry || !"google".equals(providerId)) {
            return runtimeConfig;
        }
        Map<String, String> authorizationParameters = new LinkedHashMap<>(runtimeConfig.config().authorizationParameters());
        authorizationParameters.put("prompt", "consent");
        ProviderConfig config = runtimeConfig.config().withAuthorizationParameters(authorizationParameters);
        return new ProviderSettingsResolver.ProviderRuntimeConfig(
            config,
            runtimeConfig.workspaceDomain(),
            runtimeConfig.preferDeviceFlow()
        );
    }

    private boolean shouldRetryGoogleWithConsent(String providerId, TokenResponse tokenResponse, UserPrincipal principal) {
        return tokenManager.shouldRetryGoogleWithConsent(providerId, tokenResponse, principal);
    }

    @FunctionalInterface
    interface BrowserLauncher {

        void open(URI uri) throws Exception;

        static BrowserLauncher desktop() {
            return uri -> {
                if (!Desktop.isDesktopSupported()) {
                    throw new IllegalStateException("Desktop browser integration is not available on this platform.");
                }
                Desktop desktop = Desktop.getDesktop();
                if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                    throw new IllegalStateException("Desktop browser integration is not available on this platform.");
                }
                desktop.browse(uri);
            };
        }
    }

    @FunctionalInterface
    interface CallbackServerFactory {

        CallbackServerHandle start() throws IOException;

        static CallbackServerFactory loopback() {
            return () -> {
                LoopbackCallbackServer server = LoopbackCallbackServer.start();
                return new CallbackServerHandle() {
                    @Override
                    public String redirectUri() {
                        return server.getRedirectUri();
                    }

                    @Override
                    public CompletableFuture<Map<String, String>> callbackParams() {
                        return server.getCallbackParams();
                    }

                    @Override
                    public void close() {
                        server.close();
                    }
                };
            };
        }
    }

    interface CallbackServerHandle extends AutoCloseable {

        String redirectUri();

        CompletableFuture<Map<String, String>> callbackParams();

        @Override
        void close();
    }

    record SessionTokenState(
        String accessToken,
        String refreshToken,
        String tokenType,
        String idToken,
        ProviderConfig config
    ) {
    }
}
