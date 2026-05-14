package org.metalib.papifly.fx.login.api;

import javafx.beans.property.ReadOnlyObjectProperty;
import org.metalib.papifly.fx.login.idapi.DeviceCodeResponse;
import org.metalib.papifly.fx.login.session.AuthSession;
import org.metalib.papifly.fx.login.session.AuthState;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface AuthSessionBroker {

    CompletableFuture<AuthSession> signIn(String providerId);

    CompletableFuture<AuthSession> signInWithDeviceFlow(String providerId);

    CompletableFuture<AuthSession> refresh(boolean force);

    CompletableFuture<Void> logout(boolean revokeRemote);

    Optional<AuthSession> activeSession();

    List<AuthSession> allSessions();

    void setActiveSession(String providerId, String subject);

    ReadOnlyObjectProperty<AuthState> authStateProperty();

    ReadOnlyObjectProperty<AuthSession> sessionProperty();

    ReadOnlyObjectProperty<DeviceCodeResponse> deviceCodeProperty();
}
