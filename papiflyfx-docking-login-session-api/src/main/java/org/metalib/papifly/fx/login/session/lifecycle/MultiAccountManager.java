package org.metalib.papifly.fx.login.session.lifecycle;

import org.metalib.papifly.fx.login.session.AuthSession;
import org.metalib.papifly.fx.login.session.SessionPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MultiAccountManager {

    private final Map<String, AuthSession> sessions = new LinkedHashMap<>();
    private final SessionPolicy policy;
    private String activeKey;

    public MultiAccountManager(SessionPolicy policy) {
        this.policy = policy;
    }

    public MultiAccountManager() {
        this(SessionPolicy.DEFAULT);
    }

    public synchronized void addSession(AuthSession session) {
        String key = key(session.providerId(), session.subject());
        sessions.put(key, session);
        if (sessions.size() > policy.maxConcurrentSessions()) {
            String oldest = sessions.keySet().iterator().next();
            sessions.remove(oldest);
            if (oldest.equals(activeKey)) {
                activeKey = sessions.isEmpty() ? null : sessions.keySet().iterator().next();
            }
        }
        if (activeKey == null) {
            activeKey = key;
        }
    }

    public synchronized void removeSession(String providerId, String subject) {
        String key = key(providerId, subject);
        sessions.remove(key);
        if (key.equals(activeKey)) {
            activeKey = sessions.isEmpty() ? null : sessions.keySet().iterator().next();
        }
    }

    public synchronized Optional<AuthSession> activeSession() {
        return activeKey == null ? Optional.empty() : Optional.ofNullable(sessions.get(activeKey));
    }

    public synchronized boolean setActive(String providerId, String subject) {
        String key = key(providerId, subject);
        if (sessions.containsKey(key)) {
            activeKey = key;
            return true;
        }
        return false;
    }

    public synchronized List<AuthSession> allSessions() {
        return List.copyOf(sessions.values());
    }

    public synchronized void updateSession(AuthSession session) {
        String key = key(session.providerId(), session.subject());
        if (sessions.containsKey(key)) {
            sessions.put(key, session);
        }
    }

    public synchronized void clear() {
        sessions.clear();
        activeKey = null;
    }

    private String key(String providerId, String subject) {
        return providerId + ":" + subject;
    }
}
