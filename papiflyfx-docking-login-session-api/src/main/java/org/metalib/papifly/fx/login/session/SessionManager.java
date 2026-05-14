package org.metalib.papifly.fx.login.session;

import java.util.List;
import java.util.Optional;

public interface SessionManager {

    AuthSession createSession(String providerId, String subject,
                              org.metalib.papifly.fx.login.idapi.UserPrincipal principal,
                              java.util.Set<String> scopes, java.time.Instant expiresAt);

    Optional<AuthSession> restoreSession(String providerId, String subject);

    List<AuthSession> allSessions();

    AuthSession refreshSession(AuthSession session, java.time.Instant newExpiry);

    void expireSession(AuthSession session);

    void destroySession(String providerId, String subject);

    void addListener(SessionLifecycleListener listener);

    void removeListener(SessionLifecycleListener listener);
}
