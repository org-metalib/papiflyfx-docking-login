package org.metalib.papifly.fx.login.session;

import java.time.Duration;

public record SessionPolicy(
    int maxConcurrentSessions,
    Duration idleTimeout,
    Duration refreshBeforeExpiry,
    boolean autoRefresh
) {

    public static final SessionPolicy DEFAULT = new SessionPolicy(5, Duration.ofHours(24), Duration.ofMinutes(5), true);
}
