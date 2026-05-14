package org.metalib.papifly.fx.login.session;

import org.metalib.papifly.fx.login.idapi.UserPrincipal;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record AuthSession(
    String sessionId,
    String providerId,
    String subject,
    UserPrincipal principal,
    AuthState state,
    Instant createdAt,
    Instant expiresAt,
    Set<String> scopes
) {

    public AuthSession {
        scopes = scopes == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(scopes));
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public AuthSession withState(AuthState newState) {
        return new AuthSession(sessionId, providerId, subject, principal, newState, createdAt, expiresAt, scopes);
    }

    public AuthSession withExpiry(Instant newExpiresAt) {
        return new AuthSession(sessionId, providerId, subject, principal, state, createdAt, newExpiresAt, scopes);
    }
}
