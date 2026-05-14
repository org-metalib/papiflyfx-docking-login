package org.metalib.papifly.fx.login.idapi;

public record ProviderCapabilities(
    boolean supportsAuthCodePkce,
    boolean supportsDeviceFlow,
    boolean supportsTokenRevocation,
    boolean providesOidcIdToken,
    boolean requiresHttpsRedirect
) {
}
