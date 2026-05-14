package org.metalib.papifly.fx.login.core;

import javafx.beans.property.ObjectProperty;
import org.metalib.papifly.fx.login.idapi.DeviceCodeResponse;
import org.metalib.papifly.fx.login.idapi.ProviderConfig;
import org.metalib.papifly.fx.login.idapi.TokenResponse;
import org.metalib.papifly.fx.login.idapi.UserPrincipal;
import org.metalib.papifly.fx.login.session.AuthSession;
import org.metalib.papifly.fx.login.session.AuthState;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class SessionPersistenceService {

    private final Object lock;
    private final Map<String, AuthSession> sessions;
    private final ObjectProperty<AuthState> authState;
    private final ObjectProperty<AuthSession> activeSession;
    private final ObjectProperty<DeviceCodeResponse> deviceCode;
    private final TokenManager tokenManager;

    SessionPersistenceService(
        Object lock,
        Map<String, AuthSession> sessions,
        ObjectProperty<AuthState> authState,
        ObjectProperty<AuthSession> activeSession,
        ObjectProperty<DeviceCodeResponse> deviceCode,
        TokenManager tokenManager
    ) {
        this.lock = lock;
        this.sessions = sessions;
        this.authState = authState;
        this.activeSession = activeSession;
        this.deviceCode = deviceCode;
        this.tokenManager = tokenManager;
    }

    Optional<AuthSession> activeSession() {
        synchronized (lock) {
            return Optional.ofNullable(activeSession.get());
        }
    }

    List<AuthSession> allSessions() {
        synchronized (lock) {
            return List.copyOf(sessions.values());
        }
    }

    void upsertSession(AuthSession session) {
        synchronized (lock) {
            sessions.put(key(session.providerId(), session.subject()), session);
            activeSession.set(session);
            deviceCode.set(null);
            authState.set(session.isExpired(Instant.now()) ? AuthState.EXPIRED : AuthState.AUTHENTICATED);
        }
    }

    void removeSession(String providerId, String subject) {
        synchronized (lock) {
            sessions.remove(key(providerId, subject));
            if (tokenManager != null) {
                tokenManager.removeSessionTokens(providerId, subject);
            }
            AuthSession session = activeSession.get();
            if (session != null && providerId.equals(session.providerId()) && subject.equals(session.subject())) {
                activeSession.set(null);
                deviceCode.set(null);
                authState.set(AuthState.SIGNED_OUT);
            }
        }
    }

    void setActiveSession(String providerId, String subject) {
        synchronized (lock) {
            AuthSession session = sessions.get(key(providerId, subject));
            activeSession.set(session);
            authState.set(session == null ? AuthState.SIGNED_OUT : AuthState.AUTHENTICATED);
        }
    }

    AuthSession persistAuthenticatedSession(
        String providerId,
        ProviderSettingsResolver.ProviderRuntimeConfig runtimeConfig,
        TokenResponse tokenResponse,
        UserPrincipal principal,
        AuthSession previousSession
    ) {
        synchronized (lock) {
            validatePrincipal(providerId, runtimeConfig, principal);

            Instant now = Instant.now();
            Instant expiresAt = tokenResponse.expiresIn() > 0
                ? now.plusSeconds(tokenResponse.expiresIn())
                : (previousSession != null ? previousSession.expiresAt() : null);
            Set<String> scopes = tokenResponse.scope() != null && !tokenResponse.scope().isBlank()
                ? Set.copyOf(List.of(tokenResponse.scope().trim().split("\\s+")))
                : Set.copyOf(runtimeConfig.config().scopes());

            AuthSession session = new AuthSession(
                previousSession != null ? previousSession.sessionId() : UUID.randomUUID().toString(),
                providerId,
                principal.subject(),
                principal,
                AuthState.AUTHENTICATED,
                previousSession != null ? previousSession.createdAt() : now,
                expiresAt,
                scopes
            );

            if (tokenManager != null) {
                tokenManager.storeSessionTokens(providerId, principal, tokenResponse, runtimeConfig.config());
            }
            upsertSession(session);
            return session;
        }
    }

    AuthSession signInStub(String providerId) {
        synchronized (lock) {
            authState.set(AuthState.INITIATING_AUTH);
            AuthSession session = sessions.values().stream()
                .filter(candidate -> providerId.equals(candidate.providerId()))
                .findFirst()
                .orElseGet(() -> createStubSession(providerId, providerId + "-user"));
            upsertSession(session);
            return session;
        }
    }

    AuthSession refreshStub() {
        synchronized (lock) {
            AuthSession session = activeSession.get();
            if (session == null) {
                authState.set(AuthState.ERROR);
                throw new IllegalStateException("No active session.");
            }
            authState.set(AuthState.REFRESHING);
            AuthSession refreshed = new AuthSession(
                session.sessionId(),
                session.providerId(),
                session.subject(),
                session.principal(),
                AuthState.AUTHENTICATED,
                session.createdAt(),
                Instant.now().plusSeconds(3600),
                session.scopes()
            );
            upsertSession(refreshed);
            return refreshed;
        }
    }

    private void validatePrincipal(
        String providerId,
        ProviderSettingsResolver.ProviderRuntimeConfig runtimeConfig,
        UserPrincipal principal
    ) {
        if (!"google".equals(providerId) || runtimeConfig.workspaceDomain() == null) {
            return;
        }
        String email = principal.email();
        String expectedDomain = runtimeConfig.workspaceDomain().toLowerCase(Locale.ROOT);
        if (email == null || !email.toLowerCase(Locale.ROOT).endsWith("@" + expectedDomain)) {
            throw new IllegalStateException("The signed-in Google account must belong to " + runtimeConfig.workspaceDomain() + '.');
        }
    }

    private AuthSession createStubSession(String providerId, String subject) {
        Instant now = Instant.now();
        return new AuthSession(
            UUID.randomUUID().toString(),
            providerId,
            subject,
            new UserPrincipal(subject, subject, subject + "@local", ""),
            AuthState.AUTHENTICATED,
            now,
            now.plusSeconds(3600),
            Set.of("openid")
        );
    }

    private String key(String providerId, String subject) {
        return providerId + ':' + subject;
    }
}
