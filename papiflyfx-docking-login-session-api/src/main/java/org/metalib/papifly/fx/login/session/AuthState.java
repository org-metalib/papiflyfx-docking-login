package org.metalib.papifly.fx.login.session;

public enum AuthState {
    UNAUTHENTICATED,
    INITIATING_AUTH,
    AWAITING_CALLBACK,
    POLLING_DEVICE,
    EXCHANGING_CODE,
    AUTHENTICATED,
    REFRESHING,
    EXPIRED,
    SIGNED_OUT,
    ERROR
}
