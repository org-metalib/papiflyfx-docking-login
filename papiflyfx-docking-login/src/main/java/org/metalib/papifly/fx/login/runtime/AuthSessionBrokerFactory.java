package org.metalib.papifly.fx.login.runtime;

import org.metalib.papifly.fx.login.api.AuthSessionBroker;
import org.metalib.papifly.fx.login.idapi.ProviderRegistry;

/**
 * Creates the broker implementation for a resolved provider registry.
 */
@FunctionalInterface
public interface AuthSessionBrokerFactory {

    AuthSessionBroker create(ProviderRegistry registry);
}
