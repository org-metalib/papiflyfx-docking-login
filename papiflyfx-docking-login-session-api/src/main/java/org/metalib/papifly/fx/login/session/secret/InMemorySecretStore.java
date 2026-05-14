package org.metalib.papifly.fx.login.session.secret;

import org.metalib.papifly.fx.login.session.SecretStore;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class InMemorySecretStore implements SecretStore {

    private final Map<String, String> secrets = new LinkedHashMap<>();

    @Override
    public synchronized void store(String key, String secret) {
        secrets.put(key, secret);
    }

    @Override
    public synchronized Optional<String> retrieve(String key) {
        return Optional.ofNullable(secrets.get(key));
    }

    @Override
    public synchronized void delete(String key) {
        secrets.remove(key);
    }

    @Override
    public synchronized Set<String> listKeys() {
        return Set.copyOf(secrets.keySet());
    }
}
