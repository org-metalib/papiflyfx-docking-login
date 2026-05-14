package org.metalib.papifly.fx.login.idapi;

import java.util.List;

public record ProviderDescriptor(
    String providerId,
    String displayName,
    String iconResource,
    List<String> defaultScopes,
    ProviderCapabilities capabilities
) {

    public ProviderDescriptor {
        defaultScopes = defaultScopes == null ? List.of() : List.copyOf(defaultScopes);
    }
}
