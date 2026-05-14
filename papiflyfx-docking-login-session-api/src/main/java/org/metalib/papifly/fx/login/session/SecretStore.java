package org.metalib.papifly.fx.login.session;

import java.util.Optional;
import java.util.Set;

public interface SecretStore {

    void store(String key, String secret);

    Optional<String> retrieve(String key);

    void delete(String key);

    Set<String> listKeys();

    default boolean hasKey(String key) {
        return retrieve(key).isPresent();
    }
}
