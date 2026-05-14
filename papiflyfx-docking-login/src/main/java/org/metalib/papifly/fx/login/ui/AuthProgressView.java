package org.metalib.papifly.fx.login.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;

public class AuthProgressView extends VBox {

    private final Label statusLabel;
    private final ProgressIndicator progressIndicator;

    public AuthProgressView() {
        setSpacing(16);
        setPadding(new Insets(24));
        setAlignment(Pos.CENTER);

        progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(48, 48);

        statusLabel = new Label("Authenticating...");
        statusLabel.setStyle("-fx-font-size: 14px;");

        getChildren().addAll(progressIndicator, statusLabel);
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }
}
