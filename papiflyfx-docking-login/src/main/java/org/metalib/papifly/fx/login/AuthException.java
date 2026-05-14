package org.metalib.papifly.fx.login;

public class AuthException extends RuntimeException {

    private final AuthErrorCode errorCode;

    public AuthException(AuthErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AuthException(AuthErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public AuthErrorCode errorCode() {
        return errorCode;
    }

    public boolean isRecoverable() {
        return switch (errorCode) {
            case NETWORK_ERROR, CALLBACK_TIMEOUT, REFRESH_FAILED, DEVICE_FLOW_TIMEOUT -> true;
            case USER_CANCELLED, STATE_MISMATCH, TOKEN_EXCHANGE_FAILED,
                 TOKEN_VALIDATION_FAILED, PROVIDER_NOT_REGISTERED,
                 DEVICE_FLOW_NOT_SUPPORTED, SECRET_STORE_FAILURE -> false;
        };
    }
}
