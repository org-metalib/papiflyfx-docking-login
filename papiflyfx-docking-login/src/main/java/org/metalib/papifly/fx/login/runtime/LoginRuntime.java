package org.metalib.papifly.fx.login.runtime;

import org.metalib.papifly.fx.login.api.AuthSessionBroker;
import org.metalib.papifly.fx.login.idapi.ProviderRegistry;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class LoginRuntime {

    private final AuthSessionBrokerFactory brokerFactory;
    private final Supplier<ProviderRegistry> providerRegistrySupplier;
    private final AtomicReference<AuthSessionBroker> broker = new AtomicReference<>();
    private final AtomicReference<ProviderRegistry> providerRegistry = new AtomicReference<>();

    public LoginRuntime(AuthSessionBrokerFactory brokerFactory, Supplier<ProviderRegistry> providerRegistrySupplier) {
        this.brokerFactory = Objects.requireNonNull(brokerFactory, "brokerFactory");
        this.providerRegistrySupplier = Objects.requireNonNull(providerRegistrySupplier, "providerRegistrySupplier");
    }

    public static LoginRuntime createDefault() {
        return new LoginRuntime(new DefaultAuthSessionBrokerFactory(), LoginRuntime::discoverProviders);
    }

    public static LoginRuntime of(AuthSessionBroker broker, ProviderRegistry registry) {
        Objects.requireNonNull(broker, "broker");
        Objects.requireNonNull(registry, "registry");
        return new LoginRuntime(ignored -> broker, () -> registry);
    }

    public AuthSessionBroker broker() {
        AuthSessionBroker existing = broker.get();
        if (existing != null) {
            return existing;
        }

        AuthSessionBroker created = brokerFactory.create(providerRegistry());
        if (broker.compareAndSet(null, created)) {
            return created;
        }
        return broker.get();
    }

    public ProviderRegistry providerRegistry() {
        ProviderRegistry existing = providerRegistry.get();
        if (existing != null) {
            return existing;
        }

        ProviderRegistry created = providerRegistrySupplier.get();
        if (providerRegistry.compareAndSet(null, created)) {
            return created;
        }
        return providerRegistry.get();
    }

    private static ProviderRegistry discoverProviders() {
        ProviderRegistry registry = new ProviderRegistry();
        registry.discoverProviders();
        return registry;
    }
}
