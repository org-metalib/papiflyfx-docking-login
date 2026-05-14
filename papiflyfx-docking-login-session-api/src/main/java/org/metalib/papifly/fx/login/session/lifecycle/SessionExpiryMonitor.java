package org.metalib.papifly.fx.login.session.lifecycle;

import org.metalib.papifly.fx.login.session.AuthSession;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class SessionExpiryMonitor implements AutoCloseable {

    private static final long CHECK_INTERVAL_SECONDS = 30;

    private final ScheduledExecutorService scheduler;
    private final Consumer<AuthSession> onExpired;
    private volatile AuthSession monitoredSession;
    private volatile ScheduledFuture<?> monitorTask;

    public SessionExpiryMonitor(Consumer<AuthSession> onExpired) {
        this.onExpired = onExpired;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "session-expiry-monitor");
            t.setDaemon(true);
            return t;
        });
    }

    public void monitor(AuthSession session) {
        stop();
        if (session == null || session.expiresAt() == null) {
            return;
        }
        this.monitoredSession = session;
        monitorTask = scheduler.scheduleAtFixedRate(
            this::checkExpiry,
            CHECK_INTERVAL_SECONDS,
            CHECK_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
    }

    public void stop() {
        ScheduledFuture<?> task = monitorTask;
        if (task != null) {
            task.cancel(false);
            monitorTask = null;
        }
        monitoredSession = null;
    }

    @Override
    public void close() {
        stop();
        scheduler.shutdownNow();
    }

    private void checkExpiry() {
        AuthSession session = monitoredSession;
        if (session != null && session.isExpired(Instant.now())) {
            stop();
            onExpired.accept(session);
        }
    }
}
