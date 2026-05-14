package org.metalib.papifly.fx.login.idapi;

public record DeviceCodeResponse(
    String deviceCode,
    String userCode,
    String verificationUri,
    String verificationUriComplete,
    long expiresIn,
    int interval
) {
}
