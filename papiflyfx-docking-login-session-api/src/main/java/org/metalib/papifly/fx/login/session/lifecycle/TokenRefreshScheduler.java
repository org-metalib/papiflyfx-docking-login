package org.metalib.papifly.fx.login.session.lifecycle;

import org.metalib.papifly.fx.login.session.AuthSession;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class TokenRefreshScheduler implements AutoCloseable {

    private final ScheduledExecutorService scheduler;
    private final Duration refreshBefore;
    private volatile ScheduledFuture<?> pendingRefresh;

    public TokenRefreshScheduler(Duration refreshBefore) {
        this.refreshBefore = refreshBefore;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "token-refresh-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    public void schedule(AuthSession session, Consumer<AuthSession> onRefreshNeeded) {
        cancel();
        if (session == null || session.expiresAt() == null) {
            return;
        }
        Instant refreshAt = session.expiresAt().minus(refreshBefore);
        Duration delay = Duration.between(Instant.now(), refreshAt);
        if (delay.isNegative() || delay.isZero()) {
            onRefreshNeeded.accept(session);
            return;
        }
        pendingRefresh = scheduler.schedule(
            () -> onRefreshNeeded.accept(session),
            delay.toMillis(),
            TimeUnit.MILLISECONDS
        );
    }

    public void cancel() {
        ScheduledFuture<?> future = pendingRefresh;
        if (future != null) {
            future.cancel(false);
            pendingRefresh = null;
        }
    }

    @Override
    public void close() {
        cancel();
        scheduler.shutdownNow();
    }
}
