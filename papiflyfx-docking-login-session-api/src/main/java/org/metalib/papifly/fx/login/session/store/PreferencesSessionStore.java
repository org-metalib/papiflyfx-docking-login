package org.metalib.papifly.fx.login.session.store;

import org.metalib.papifly.fx.login.session.SessionMetadata;
import org.metalib.papifly.fx.login.session.SessionStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.prefs.Preferences;

public class PreferencesSessionStore implements SessionStore {

    private static final String INDEX_KEY = "login.sessions.index";
    private final Preferences prefs;

    public PreferencesSessionStore() {
        this.prefs = Preferences.userNodeForPackage(PreferencesSessionStore.class);
    }

    public PreferencesSessionStore(Preferences prefs) {
        this.prefs = prefs;
    }

    @Override
    public void save(SessionMetadata metadata) {
        Preferences node = prefs.node(nodeKey(metadata.providerId(), metadata.subject()));
        node.put("displayName", metadata.displayName() != null ? metadata.displayName() : "");
        node.put("email", metadata.email() != null ? metadata.email() : "");
        node.put("avatarUrl", metadata.avatarUrl() != null ? metadata.avatarUrl() : "");
        node.put("scopes", metadata.scopes() != null ? String.join(" ", metadata.scopes()) : "");
        if (metadata.lastAuthenticated() != null) {
            node.put("lastAuthenticated", metadata.lastAuthenticated().toString());
        }
        if (metadata.expiresAt() != null) {
            node.put("expiresAt", metadata.expiresAt().toString());
        }
        addToIndex(metadata.providerId(), metadata.subject());
    }

    @Override
    public Optional<SessionMetadata> load(String providerId, String subject) {
        Preferences node = prefs.node(nodeKey(providerId, subject));
        String displayName = node.get("displayName", "");
        if (displayName.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(readMetadata(providerId, subject, node));
    }

    @Override
    public List<SessionMetadata> loadAll() {
        List<SessionMetadata> result = new ArrayList<>();
        String index = prefs.get(INDEX_KEY, "");
        if (index.isBlank()) return result;
        for (String entry : index.split(",")) {
            String[] parts = entry.split(":", 2);
            if (parts.length == 2) {
                load(parts[0], parts[1]).ifPresent(result::add);
            }
        }
        return result;
    }

    @Override
    public void remove(String providerId, String subject) {
        try {
            Preferences node = prefs.node(nodeKey(providerId, subject));
            node.removeNode();
        } catch (Exception ignored) {
        }
        removeFromIndex(providerId, subject);
    }

    @Override
    public void clear() {
        String index = prefs.get(INDEX_KEY, "");
        if (!index.isBlank()) {
            for (String entry : index.split(",")) {
                String[] parts = entry.split(":", 2);
                if (parts.length == 2) {
                    try { prefs.node(nodeKey(parts[0], parts[1])).removeNode(); } catch (Exception ignored) {}
                }
            }
        }
        prefs.put(INDEX_KEY, "");
    }

    private SessionMetadata readMetadata(String providerId, String subject, Preferences node) {
        String displayName = node.get("displayName", "");
        String email = node.get("email", "");
        String avatarUrl = node.get("avatarUrl", "");
        String scopesStr = node.get("scopes", "");
        String lastAuthStr = node.get("lastAuthenticated", "");
        String expiresStr = node.get("expiresAt", "");

        Set<String> scopes = scopesStr.isBlank() ? Set.of() : new LinkedHashSet<>(Arrays.asList(scopesStr.split("\\s+")));
        Instant lastAuth = lastAuthStr.isBlank() ? null : Instant.parse(lastAuthStr);
        Instant expires = expiresStr.isBlank() ? null : Instant.parse(expiresStr);

        return new SessionMetadata(providerId, subject, displayName, email, avatarUrl, scopes, lastAuth, expires);
    }

    private void addToIndex(String providerId, String subject) {
        String entry = providerId + ":" + subject;
        String index = prefs.get(INDEX_KEY, "");
        if (index.contains(entry)) return;
        prefs.put(INDEX_KEY, index.isBlank() ? entry : index + "," + entry);
    }

    private void removeFromIndex(String providerId, String subject) {
        String entry = providerId + ":" + subject;
        String index = prefs.get(INDEX_KEY, "");
        String updated = Arrays.stream(index.split(","))
            .filter(e -> !e.equals(entry))
            .reduce((a, b) -> a + "," + b)
            .orElse("");
        prefs.put(INDEX_KEY, updated);
    }

    private String nodeKey(String providerId, String subject) {
        return "session/" + providerId + "/" + subject;
    }
}
