package org.metalib.papifly.fx.login.idapi;

public record AuthorizationRequest(
    String authUrl,
    String state,
    String nonce,
    String codeVerifier,
    String redirectUri
) {
}
