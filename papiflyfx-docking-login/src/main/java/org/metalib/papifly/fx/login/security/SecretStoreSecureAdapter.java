package org.metalib.papifly.fx.login.security;

import org.metalib.papifly.fx.settings.api.SecretStore;

import java.util.Objects;
import java.util.Optional;

public class SecretStoreSecureAdapter implements SecureSecretStore {

    private final SecretStore secretStore;

    public SecretStoreSecureAdapter(SecretStore secretStore) {
        this.secretStore = Objects.requireNonNull(secretStore, "secretStore");
    }

    @Override
    public void put(String key, byte[] secret) {
        secretStore.putBytes(key, secret);
    }

    @Override
    public Optional<byte[]> get(String key) {
        return secretStore.getBytes(key);
    }

    @Override
    public void delete(String key) {
        secretStore.clearSecret(key);
    }
}
