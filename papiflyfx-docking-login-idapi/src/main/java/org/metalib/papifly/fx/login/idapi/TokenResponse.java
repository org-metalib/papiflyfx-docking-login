package org.metalib.papifly.fx.login.idapi;

public record TokenResponse(
    String accessToken,
    String refreshToken,
    String idToken,
    String tokenType,
    long expiresIn,
    String scope
) {
}
