package org.metalib.papifly.fx.login.session.lifecycle;

import org.metalib.papifly.fx.login.session.AuthState;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public final class SessionStateManager {

    private static final Map<AuthState, Set<AuthState>> TRANSITIONS = new EnumMap<>(AuthState.class);

    static {
        TRANSITIONS.put(AuthState.UNAUTHENTICATED, Set.of(AuthState.INITIATING_AUTH));
        TRANSITIONS.put(AuthState.INITIATING_AUTH, Set.of(AuthState.AWAITING_CALLBACK, AuthState.POLLING_DEVICE, AuthState.ERROR, AuthState.UNAUTHENTICATED));
        TRANSITIONS.put(AuthState.AWAITING_CALLBACK, Set.of(AuthState.EXCHANGING_CODE, AuthState.ERROR, AuthState.UNAUTHENTICATED));
        TRANSITIONS.put(AuthState.POLLING_DEVICE, Set.of(AuthState.EXCHANGING_CODE, AuthState.ERROR, AuthState.UNAUTHENTICATED));
        TRANSITIONS.put(AuthState.EXCHANGING_CODE, Set.of(AuthState.AUTHENTICATED, AuthState.ERROR, AuthState.UNAUTHENTICATED));
        TRANSITIONS.put(AuthState.AUTHENTICATED, Set.of(AuthState.REFRESHING, AuthState.EXPIRED, AuthState.SIGNED_OUT, AuthState.ERROR));
        TRANSITIONS.put(AuthState.REFRESHING, Set.of(AuthState.AUTHENTICATED, AuthState.EXPIRED, AuthState.ERROR, AuthState.SIGNED_OUT));
        TRANSITIONS.put(AuthState.EXPIRED, Set.of(AuthState.INITIATING_AUTH, AuthState.SIGNED_OUT, AuthState.UNAUTHENTICATED));
        TRANSITIONS.put(AuthState.SIGNED_OUT, Set.of(AuthState.UNAUTHENTICATED, AuthState.INITIATING_AUTH));
        TRANSITIONS.put(AuthState.ERROR, Set.of(AuthState.UNAUTHENTICATED, AuthState.INITIATING_AUTH, AuthState.SIGNED_OUT));
    }

    private AuthState currentState;

    public SessionStateManager() {
        this.currentState = AuthState.UNAUTHENTICATED;
    }

    public SessionStateManager(AuthState initialState) {
        this.currentState = initialState;
    }

    public synchronized AuthState current() {
        return currentState;
    }

    public synchronized boolean canTransition(AuthState target) {
        Set<AuthState> allowed = TRANSITIONS.get(currentState);
        return allowed != null && allowed.contains(target);
    }

    public synchronized AuthState transition(AuthState target) {
        if (!canTransition(target)) {
            throw new IllegalStateException("Cannot transition from " + currentState + " to " + target);
        }
        currentState = target;
        return currentState;
    }

    public synchronized AuthState forceTransition(AuthState target) {
        currentState = target;
        return currentState;
    }
}
