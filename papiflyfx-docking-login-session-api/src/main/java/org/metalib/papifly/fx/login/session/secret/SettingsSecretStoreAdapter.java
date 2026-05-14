package org.metalib.papifly.fx.login.session.secret;

import org.metalib.papifly.fx.login.session.SecretStore;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class SettingsSecretStoreAdapter implements SecretStore {

    private final org.metalib.papifly.fx.settings.api.SecretStore delegate;

    public SettingsSecretStoreAdapter(org.metalib.papifly.fx.settings.api.SecretStore delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void store(String key, String secret) {
        delegate.setSecret(key, secret);
    }

    @Override
    public Optional<String> retrieve(String key) {
        return delegate.getSecret(key);
    }

    @Override
    public void delete(String key) {
        delegate.clearSecret(key);
    }

    @Override
    public Set<String> listKeys() {
        return delegate.listKeys();
    }
}
