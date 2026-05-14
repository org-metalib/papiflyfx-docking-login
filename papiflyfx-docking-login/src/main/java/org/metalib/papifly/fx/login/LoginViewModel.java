package org.metalib.papifly.fx.login;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import org.metalib.papifly.fx.login.api.AuthSessionBroker;
import org.metalib.papifly.fx.login.idapi.DeviceCodeResponse;
import org.metalib.papifly.fx.login.idapi.ProviderDescriptor;
import org.metalib.papifly.fx.login.idapi.ProviderRegistry;
import org.metalib.papifly.fx.login.idapi.UserPrincipal;
import org.metalib.papifly.fx.login.session.AuthSession;
import org.metalib.papifly.fx.login.session.AuthState;

import java.util.List;
import java.util.concurrent.CompletionException;

public class LoginViewModel {

    private final AuthSessionBroker broker;
    private final ProviderRegistry registry;
    private final ReadOnlyStringWrapper statusMessage = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper errorMessage = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(false);

    public LoginViewModel(AuthSessionBroker broker, ProviderRegistry registry) {
        this.broker = broker;
        this.registry = registry;

        broker.authStateProperty().addListener((obs, oldState, newState) -> updateStatus(newState));
        broker.sessionProperty().addListener((obs, oldSession, newSession) -> updateSessionInfo(newSession));
    }

    public ReadOnlyObjectProperty<AuthState> authStateProperty() {
        return broker.authStateProperty();
    }

    public ReadOnlyObjectProperty<AuthSession> sessionProperty() {
        return broker.sessionProperty();
    }

    public ReadOnlyObjectProperty<DeviceCodeResponse> deviceCodeProperty() {
        return broker.deviceCodeProperty();
    }

    public ReadOnlyStringProperty statusMessageProperty() {
        return statusMessage.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty errorMessageProperty() {
        return errorMessage.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty busyProperty() {
        return busy.getReadOnlyProperty();
    }

    public List<ProviderDescriptor> availableProviders() {
        return registry.descriptors();
    }

    public void signIn(String providerId) {
        busy.set(true);
        errorMessage.set("");
        broker.signIn(providerId).whenComplete((session, error) -> {
            busy.set(false);
            if (error != null) {
                errorMessage.set(describeError(error));
            }
        });
    }

    public void signInWithDeviceFlow(String providerId) {
        busy.set(true);
        errorMessage.set("");
        broker.signInWithDeviceFlow(providerId).whenComplete((session, error) -> {
            busy.set(false);
            if (error != null) {
                errorMessage.set(describeError(error));
            }
        });
    }

    public void refresh() {
        busy.set(true);
        broker.refresh(true).whenComplete((session, error) -> {
            busy.set(false);
            if (error != null) {
                errorMessage.set(describeError(error));
            }
        });
    }

    public void logout() {
        broker.logout(false);
    }

    public void switchAccount(String providerId, String subject) {
        broker.setActiveSession(providerId, subject);
    }

    private void updateStatus(AuthState state) {
        statusMessage.set(switch (state) {
            case UNAUTHENTICATED -> "Not signed in";
            case INITIATING_AUTH -> "Starting authentication...";
            case AWAITING_CALLBACK -> "Waiting for browser callback...";
            case POLLING_DEVICE -> "Waiting for device authorization...";
            case EXCHANGING_CODE -> "Exchanging authorization code...";
            case AUTHENTICATED -> "Signed in";
            case REFRESHING -> "Refreshing session...";
            case EXPIRED -> "Session expired";
            case SIGNED_OUT -> "Signed out";
            case ERROR -> "Authentication error";
        });
    }

    private void updateSessionInfo(AuthSession session) {
        if (session != null && session.principal() != null) {
            UserPrincipal p = session.principal();
            String name = p.displayName() != null ? p.displayName() : p.subject();
            statusMessage.set("Signed in as " + name);
        }
    }

    private String describeError(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        String simpleName = current.getClass().getSimpleName();
        return simpleName == null || simpleName.isBlank() ? "Authentication failed." : simpleName;
    }
}
