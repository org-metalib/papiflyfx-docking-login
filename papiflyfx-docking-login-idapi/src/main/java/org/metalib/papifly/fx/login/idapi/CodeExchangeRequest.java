package org.metalib.papifly.fx.login.idapi;

public record CodeExchangeRequest(
    String code,
    String codeVerifier,
    String redirectUri,
    String state
) {
}
