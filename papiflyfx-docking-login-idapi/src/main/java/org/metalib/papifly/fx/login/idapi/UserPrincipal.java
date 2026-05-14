package org.metalib.papifly.fx.login.idapi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record UserPrincipal(
    String subject,
    String displayName,
    String email,
    String avatarUrl,
    Map<String, Object> rawClaims
) {

    public UserPrincipal {
        if (rawClaims == null || rawClaims.isEmpty()) {
            rawClaims = Map.of();
        } else {
            Map<String, Object> copy = new LinkedHashMap<>();
            rawClaims.forEach((key, value) -> {
                if (key != null) {
                    copy.put(key, value);
                }
            });
            rawClaims = Collections.unmodifiableMap(copy);
        }
    }

    public UserPrincipal(String subject, String displayName, String email, String avatarUrl) {
        this(subject, displayName, email, avatarUrl, Map.of());
    }
}
