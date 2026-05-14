package org.metalib.papifly.fx.login.runtime;

import org.metalib.papifly.fx.login.api.AuthSessionBroker;
import org.metalib.papifly.fx.login.core.DefaultAuthSessionBroker;
import org.metalib.papifly.fx.login.idapi.ProviderRegistry;
import org.metalib.papifly.fx.settings.api.SettingsServicesProvider;

import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * Default broker factory that optionally reuses settings-backed storage services when available.
 */
public final class DefaultAuthSessionBrokerFactory implements AuthSessionBrokerFactory {

    private final Supplier<Optional<SettingsServicesProvider>> settingsProviderSupplier;

    public DefaultAuthSessionBrokerFactory() {
        this(() -> ServiceLoader.load(SettingsServicesProvider.class).findFirst());
    }

    DefaultAuthSessionBrokerFactory(Supplier<Optional<SettingsServicesProvider>> settingsProviderSupplier) {
        this.settingsProviderSupplier = Objects.requireNonNull(settingsProviderSupplier, "settingsProviderSupplier");
    }

    @Override
    public AuthSessionBroker create(ProviderRegistry registry) {
        Optional<SettingsServicesProvider> settingsProvider = settingsProviderSupplier.get();
        if (settingsProvider.isPresent()) {
            SettingsServicesProvider provider = settingsProvider.get();
            return new DefaultAuthSessionBroker(registry, provider.storage(), provider.secretStore());
        }
        return new DefaultAuthSessionBroker();
    }
}
