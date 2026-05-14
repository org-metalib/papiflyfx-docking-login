package org.metalib.papifly.fx.login.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class ErrorView extends VBox {

    private final Label errorLabel;
    private final Button retryButton;
    private final Button backButton;

    public ErrorView(Runnable onRetry, Runnable onBack) {
        setSpacing(12);
        setPadding(new Insets(24));
        setAlignment(Pos.CENTER);

        Label title = new Label("Authentication Error");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #c00;");

        errorLabel = new Label();
        errorLabel.setStyle("-fx-font-size: 12px;");
        errorLabel.setWrapText(true);

        retryButton = new Button("Retry");
        retryButton.setOnAction(e -> onRetry.run());

        backButton = new Button("Back");
        backButton.setOnAction(e -> onBack.run());

        getChildren().addAll(title, errorLabel, retryButton, backButton);
    }

    public void setError(String message) {
        errorLabel.setText(message);
    }

    public void setRetryVisible(boolean visible) {
        retryButton.setVisible(visible);
        retryButton.setManaged(visible);
    }
}
