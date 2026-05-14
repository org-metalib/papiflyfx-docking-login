package org.metalib.papifly.fx.login.session.store;

import org.metalib.papifly.fx.login.session.SessionMetadata;
import org.metalib.papifly.fx.login.session.SessionStore;
import org.metalib.papifly.fx.settings.api.SettingScope;
import org.metalib.papifly.fx.settings.api.SettingsStorage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class SettingsSessionStore implements SessionStore {

    private static final String KEY_PREFIX = "login.session.";
    private static final String SESSIONS_INDEX = "login.sessions";

    private final SettingsStorage storage;

    public SettingsSessionStore(SettingsStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public void save(SessionMetadata metadata) {
        String prefix = prefix(metadata.providerId(), metadata.subject());
        storage.putString(SettingScope.APPLICATION, prefix + ".displayName", metadata.displayName() != null ? metadata.displayName() : "");
        storage.putString(SettingScope.APPLICATION, prefix + ".email", metadata.email() != null ? metadata.email() : "");
        storage.putString(SettingScope.APPLICATION, prefix + ".avatarUrl", metadata.avatarUrl() != null ? metadata.avatarUrl() : "");
        storage.putString(SettingScope.APPLICATION, prefix + ".scopes", metadata.scopes() != null ? String.join(" ", metadata.scopes()) : "");
        if (metadata.lastAuthenticated() != null) {
            storage.putString(SettingScope.APPLICATION, prefix + ".lastAuthenticated", metadata.lastAuthenticated().toString());
        }
        if (metadata.expiresAt() != null) {
            storage.putString(SettingScope.SESSION, prefix + ".expiresAt", metadata.expiresAt().toString());
        }
        addToIndex(metadata.providerId(), metadata.subject());
        storage.save();
    }

    @Override
    public Optional<SessionMetadata> load(String providerId, String subject) {
        String prefix = prefix(providerId, subject);
        String displayName = storage.getString(SettingScope.APPLICATION, prefix + ".displayName", "");
        if (displayName.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(readMetadata(providerId, subject, prefix));
    }

    @Override
    public List<SessionMetadata> loadAll() {
        List<SessionMetadata> result = new ArrayList<>();
        String index = storage.getString(SettingScope.APPLICATION, SESSIONS_INDEX, "");
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
        String prefix = prefix(providerId, subject);
        storage.putString(SettingScope.APPLICATION, prefix + ".displayName", "");
        storage.putString(SettingScope.APPLICATION, prefix + ".email", "");
        storage.putString(SettingScope.APPLICATION, prefix + ".avatarUrl", "");
        storage.putString(SettingScope.APPLICATION, prefix + ".scopes", "");
        storage.putString(SettingScope.APPLICATION, prefix + ".lastAuthenticated", "");
        storage.putString(SettingScope.SESSION, prefix + ".expiresAt", "");
        removeFromIndex(providerId, subject);
        storage.save();
    }

    @Override
    public void clear() {
        String index = storage.getString(SettingScope.APPLICATION, SESSIONS_INDEX, "");
        if (!index.isBlank()) {
            for (String entry : index.split(",")) {
                String[] parts = entry.split(":", 2);
                if (parts.length == 2) {
                    remove(parts[0], parts[1]);
                }
            }
        }
        storage.putString(SettingScope.APPLICATION, SESSIONS_INDEX, "");
        storage.save();
    }

    private SessionMetadata readMetadata(String providerId, String subject, String prefix) {
        String displayName = storage.getString(SettingScope.APPLICATION, prefix + ".displayName", "");
        String email = storage.getString(SettingScope.APPLICATION, prefix + ".email", "");
        String avatarUrl = storage.getString(SettingScope.APPLICATION, prefix + ".avatarUrl", "");
        String scopesStr = storage.getString(SettingScope.APPLICATION, prefix + ".scopes", "");
        String lastAuthStr = storage.getString(SettingScope.APPLICATION, prefix + ".lastAuthenticated", "");
        String expiresStr = storage.getString(SettingScope.SESSION, prefix + ".expiresAt", "");

        Set<String> scopes = scopesStr.isBlank() ? Set.of() : new LinkedHashSet<>(Arrays.asList(scopesStr.split("\\s+")));
        Instant lastAuth = lastAuthStr.isBlank() ? null : Instant.parse(lastAuthStr);
        Instant expires = expiresStr.isBlank() ? null : Instant.parse(expiresStr);

        return new SessionMetadata(providerId, subject, displayName, email, avatarUrl, scopes, lastAuth, expires);
    }

    private void addToIndex(String providerId, String subject) {
        String entry = providerId + ":" + subject;
        String index = storage.getString(SettingScope.APPLICATION, SESSIONS_INDEX, "");
        if (index.contains(entry)) return;
        String updated = index.isBlank() ? entry : index + "," + entry;
        storage.putString(SettingScope.APPLICATION, SESSIONS_INDEX, updated);
    }

    private void removeFromIndex(String providerId, String subject) {
        String entry = providerId + ":" + subject;
        String index = storage.getString(SettingScope.APPLICATION, SESSIONS_INDEX, "");
        String updated = Arrays.stream(index.split(","))
            .filter(e -> !e.equals(entry))
            .reduce((a, b) -> a + "," + b)
            .orElse("");
        storage.putString(SettingScope.APPLICATION, SESSIONS_INDEX, updated);
    }

    private String prefix(String providerId, String subject) {
        return KEY_PREFIX + providerId + "." + subject;
    }
}
