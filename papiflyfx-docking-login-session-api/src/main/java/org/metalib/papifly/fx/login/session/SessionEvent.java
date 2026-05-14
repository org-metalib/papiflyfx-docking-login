package org.metalib.papifly.fx.login.session;

import java.time.Instant;

public record SessionEvent(
    EventType eventType,
    AuthSession session,
    Instant timestamp,
    Throwable error
) {

    public enum EventType {
        CREATED,
        AUTHENTICATED,
        REFRESHED,
        EXPIRED,
        DESTROYED,
        ERROR
    }

    public static SessionEvent of(EventType type, AuthSession session) {
        return new SessionEvent(type, session, Instant.now(), null);
    }

    public static SessionEvent error(AuthSession session, Throwable error) {
        return new SessionEvent(EventType.ERROR, session, Instant.now(), error);
    }
}
