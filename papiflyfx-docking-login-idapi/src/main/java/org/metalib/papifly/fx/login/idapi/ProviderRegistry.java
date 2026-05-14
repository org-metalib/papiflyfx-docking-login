package org.metalib.papifly.fx.login.idapi;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ProviderRegistry {

    private final Map<String, IdentityProvider> providers = new LinkedHashMap<>();
    private final List<ProviderRegistryListener> listeners = new CopyOnWriteArrayList<>();

    public ProviderRegistry() {
    }

    public void discoverProviders() {
        ServiceLoader<IdentityProvider> loader = ServiceLoader.load(IdentityProvider.class);
        for (IdentityProvider provider : loader) {
            register(provider);
        }
    }

    public synchronized void register(IdentityProvider provider) {
        Objects.requireNonNull(provider, "provider");
        String id = provider.descriptor().providerId();
        providers.put(id, provider);
        for (ProviderRegistryListener listener : listeners) {
            listener.onProviderAdded(provider);
        }
    }

    public synchronized void unregister(String providerId) {
        IdentityProvider removed = providers.remove(providerId);
        if (removed != null) {
            for (ProviderRegistryListener listener : listeners) {
                listener.onProviderRemoved(removed);
            }
        }
    }

    public synchronized Optional<IdentityProvider> get(String providerId) {
        return Optional.ofNullable(providers.get(providerId));
    }

    public synchronized Collection<IdentityProvider> all() {
        return List.copyOf(providers.values());
    }

    public synchronized List<ProviderDescriptor> descriptors() {
        return providers.values().stream()
            .map(IdentityProvider::descriptor)
            .toList();
    }

    public void addListener(ProviderRegistryListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(ProviderRegistryListener listener) {
        listeners.remove(listener);
    }
}
