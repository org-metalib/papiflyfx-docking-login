package org.metalib.papifly.fx.login.security;

import java.util.Optional;

public interface SecureSecretStore {

    void put(String key, byte[] secret);

    Optional<byte[]> get(String key);

    void delete(String key);
}
