package org.metalib.papifly.fx.login.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.metalib.papifly.fx.login.idapi.UserPrincipal;
import org.metalib.papifly.fx.login.session.AuthSession;

import java.util.function.Consumer;

public class AccountStatusWidget extends VBox {

    private final Label nameLabel;
    private final Label emailLabel;
    private final Label providerLabel;
    private final Label scopesLabel;
    private final Button logoutButton;
    private final Button refreshButton;

    public AccountStatusWidget(Consumer<String> onAction) {
        setSpacing(8);
        setPadding(new Insets(16));
        setAlignment(Pos.TOP_LEFT);

        Label title = new Label("Account");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        nameLabel = new Label();
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        emailLabel = new Label();
        emailLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
        providerLabel = new Label();
        providerLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        scopesLabel = new Label();
        scopesLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #aaa;");

        logoutButton = new Button("Sign Out");
        logoutButton.setOnAction(e -> onAction.accept("logout"));
        refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> onAction.accept("refresh"));

        HBox actions = new HBox(8, refreshButton, logoutButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox info = new VBox(4, nameLabel, emailLabel, providerLabel, scopesLabel);
        VBox.setVgrow(info, Priority.ALWAYS);

        getChildren().addAll(title, info, actions);
    }

    public void update(AuthSession session) {
        if (session == null) {
            nameLabel.setText("Not signed in");
            emailLabel.setText("");
            providerLabel.setText("");
            scopesLabel.setText("");
            return;
        }

        UserPrincipal principal = session.principal();
        if (principal != null) {
            nameLabel.setText(principal.displayName() != null ? principal.displayName() : principal.subject());
            emailLabel.setText(principal.email() != null ? principal.email() : "");
        } else {
            nameLabel.setText(session.subject());
            emailLabel.setText("");
        }
        providerLabel.setText("via " + session.providerId());
        scopesLabel.setText(session.scopes().isEmpty() ? "" : "Scopes: " + String.join(", ", session.scopes()));
    }
}
