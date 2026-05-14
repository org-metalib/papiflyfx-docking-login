package org.metalib.papifly.fx.login.session.store;

import org.metalib.papifly.fx.login.session.SessionMetadata;
import org.metalib.papifly.fx.login.session.SessionStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemorySessionStore implements SessionStore {

    private final Map<String, SessionMetadata> store = new LinkedHashMap<>();

    @Override
    public synchronized void save(SessionMetadata metadata) {
        store.put(key(metadata.providerId(), metadata.subject()), metadata);
    }

    @Override
    public synchronized Optional<SessionMetadata> load(String providerId, String subject) {
        return Optional.ofNullable(store.get(key(providerId, subject)));
    }

    @Override
    public synchronized List<SessionMetadata> loadAll() {
        return List.copyOf(store.values());
    }

    @Override
    public synchronized void remove(String providerId, String subject) {
        store.remove(key(providerId, subject));
    }

    @Override
    public synchronized void clear() {
        store.clear();
    }

    private String key(String providerId, String subject) {
        return providerId + ":" + subject;
    }
}
