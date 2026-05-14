package org.metalib.papifly.fx.login.api;

import org.metalib.papifly.fx.login.session.AuthSession;

/**
 * Administrative session operations that are optional for broker implementations.
 *
 * <p>This separates runtime session maintenance concerns from the base
 * authentication broker contract so UI code can depend on a focused capability
 * instead of downcasting to a concrete broker implementation.</p>
 */
public interface AuthSessionAdmin {

    void upsertSession(AuthSession session);

    void removeSession(String providerId, String subject);
}
