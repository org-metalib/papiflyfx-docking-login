package org.metalib.papifly.fx.login.settings;

import org.metalib.papifly.fx.settings.api.SettingsMigrator;

import java.util.LinkedHashMap;
import java.util.Map;

public class LoginSettingsMigrator implements SettingsMigrator {

    @Override
    public Map<String, Object> migrate(Map<String, Object> data, int fromVersion) {
        Map<String, Object> result = new LinkedHashMap<>(data);
        if (fromVersion < 2) {
            migrateV1ToV2(result);
        }
        if (fromVersion < 3) {
            migrateV2ToV3(result);
        }
        return result;
    }

    private void migrateV1ToV2(Map<String, Object> data) {
        Map<String, Object> renames = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("login.oauth.refresh.")) {
                String newKey = key.replace("login.oauth.refresh.", "login:oauth:refresh:");
                renames.put(key, newKey);
            }
        }
        for (Map.Entry<String, Object> rename : renames.entrySet()) {
            Object value = data.remove(rename.getKey());
            data.put((String) rename.getValue(), value);
        }
    }

    private void migrateV2ToV3(Map<String, Object> data) {
        // v2 -> v3: no-op placeholder for avatar-url field addition
        // New fields are added with defaults, no migration needed for existing data
    }
}
