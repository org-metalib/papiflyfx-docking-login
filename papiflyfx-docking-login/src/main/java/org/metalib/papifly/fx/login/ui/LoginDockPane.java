package org.metalib.papifly.fx.login.ui;

import javafx.application.Platform;
import javafx.scene.layout.StackPane;
import org.metalib.papifly.fx.docking.api.DisposableContent;
import org.metalib.papifly.fx.login.LoginViewModel;
import org.metalib.papifly.fx.login.api.AuthSessionBroker;
import org.metalib.papifly.fx.login.idapi.DeviceCodeResponse;
import org.metalib.papifly.fx.login.idapi.ProviderRegistry;
import org.metalib.papifly.fx.login.session.AuthState;

public class LoginDockPane extends StackPane implements DisposableContent {

    public static final String FACTORY_ID = "login";

    private final LoginViewModel viewModel;
    private final ProviderRegistry registry;
    private ProviderSelectionView selectionView;
    private AuthProgressView progressView;
    private DeviceFlowView deviceFlowView;
    private AccountStatusWidget statusWidget;
    private ErrorView errorView;
    private String lastProviderId;

    public LoginDockPane(AuthSessionBroker broker, ProviderRegistry registry) {
        this.registry = registry;
        this.viewModel = new LoginViewModel(broker, registry);

        viewModel.authStateProperty().addListener((obs, oldState, newState) ->
            Platform.runLater(() -> showViewForState(newState)));
        viewModel.deviceCodeProperty().addListener((obs, oldDeviceCode, newDeviceCode) -> {
            if (newDeviceCode != null && viewModel.authStateProperty().get() == AuthState.POLLING_DEVICE) {
                Platform.runLater(this::showDeviceFlow);
            }
        });
        viewModel.errorMessageProperty().addListener((obs, oldError, newError) -> {
            if (newError != null && !newError.isEmpty()) {
                Platform.runLater(() -> showError(newError));
            }
        });

        showViewForState(viewModel.authStateProperty().get());
    }

    @Override
    public void dispose() {
        if (deviceFlowView != null) {
            deviceFlowView.close();
        }
    }

    private void showViewForState(AuthState state) {
        switch (state) {
            case UNAUTHENTICATED, SIGNED_OUT -> showProviderSelection();
            case INITIATING_AUTH, AWAITING_CALLBACK, EXCHANGING_CODE -> showProgress("Authenticating...");
            case POLLING_DEVICE -> showDeviceFlow();
            case REFRESHING -> showProgress("Refreshing session...");
            case AUTHENTICATED -> showAccountStatus();
            case EXPIRED -> showProviderSelection();
            case ERROR -> showError(viewModel.errorMessageProperty().get());
        }
    }

    private void showProviderSelection() {
        if (selectionView == null) {
            selectionView = new ProviderSelectionView(
                viewModel.availableProviders(),
                providerId -> {
                    lastProviderId = providerId;
                    viewModel.signIn(providerId);
                }
            );
        }
        getChildren().setAll(selectionView);
    }

    private void showProgress(String message) {
        if (progressView == null) {
            progressView = new AuthProgressView();
        }
        progressView.setStatus(message);
        getChildren().setAll(progressView);
    }

    private void showDeviceFlow() {
        DeviceCodeResponse response = viewModel.deviceCodeProperty().get();
        if (response == null) {
            showProgress("Waiting for device authorization...");
            return;
        }
        if (deviceFlowView == null) {
            deviceFlowView = new DeviceFlowView();
        }
        deviceFlowView.show(response);
        getChildren().setAll(deviceFlowView);
    }

    private void showAccountStatus() {
        if (statusWidget == null) {
            statusWidget = new AccountStatusWidget(action -> {
                switch (action) {
                    case "logout" -> viewModel.logout();
                    case "refresh" -> viewModel.refresh();
                }
            });
        }
        statusWidget.update(viewModel.sessionProperty().get());
        getChildren().setAll(statusWidget);
    }

    private void showError(String message) {
        if (errorView == null) {
            errorView = new ErrorView(
                () -> {
                    if (lastProviderId != null) {
                        viewModel.signIn(lastProviderId);
                    } else {
                        showProviderSelection();
                    }
                },
                this::showProviderSelection
            );
        }
        errorView.setError(message != null ? message : "An unknown error occurred.");
        getChildren().setAll(errorView);
    }
}
