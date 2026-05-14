package org.metalib.papifly.fx.login.session;

import java.time.Instant;
import java.util.Set;

public record SessionMetadata(
    String providerId,
    String subject,
    String displayName,
    String email,
    String avatarUrl,
    Set<String> scopes,
    Instant lastAuthenticated,
    Instant expiresAt
) {
}
