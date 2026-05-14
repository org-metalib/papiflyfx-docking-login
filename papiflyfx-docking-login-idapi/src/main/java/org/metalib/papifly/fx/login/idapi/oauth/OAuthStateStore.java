package org.metalib.papifly.fx.login.idapi.oauth;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class OAuthStateStore {

    private final Map<String, StateEntry> entries = new ConcurrentHashMap<>();

    public void store(String state, String nonce, String codeVerifier, String redirectUri) {
        entries.put(state, new StateEntry(nonce, codeVerifier, redirectUri));
    }

    public Optional<StateEntry> consume(String state) {
        return Optional.ofNullable(entries.remove(state));
    }

    public void clear() {
        entries.clear();
    }

    public record StateEntry(String nonce, String codeVerifier, String redirectUri) {
    }
}
