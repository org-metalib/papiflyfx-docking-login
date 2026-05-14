package org.metalib.papifly.fx.login.session;

import java.util.List;
import java.util.Optional;

public interface SessionStore {

    void save(SessionMetadata metadata);

    Optional<SessionMetadata> load(String providerId, String subject);

    List<SessionMetadata> loadAll();

    void remove(String providerId, String subject);

    void clear();
}
