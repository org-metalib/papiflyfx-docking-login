package org.metalib.papifly.fx.login.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import org.metalib.papifly.fx.login.idapi.DeviceCodeResponse;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DeviceFlowView extends VBox implements AutoCloseable {

    private final Label userCodeLabel;
    private final Hyperlink verificationLink;
    private final Label countdownLabel;
    private final ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> countdownTask;
    private volatile long remainingSeconds;

    public DeviceFlowView() {
        setSpacing(12);
        setPadding(new Insets(24));
        setAlignment(Pos.CENTER);

        Label title = new Label("Device Authorization");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label instruction = new Label("Enter the code below at the verification URL:");
        instruction.setStyle("-fx-font-size: 12px;");

        userCodeLabel = new Label();
        userCodeLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-font-family: monospace;");

        verificationLink = new Hyperlink();

        countdownLabel = new Label();
        countdownLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(32, 32);

        getChildren().addAll(title, instruction, userCodeLabel, verificationLink, countdownLabel, spinner);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "device-flow-countdown");
            t.setDaemon(true);
            return t;
        });
    }

    public void show(DeviceCodeResponse response) {
        userCodeLabel.setText(response.userCode());
        String uri = response.verificationUriComplete() != null ? response.verificationUriComplete() : response.verificationUri();
        verificationLink.setText(uri);
        verificationLink.setOnAction(e -> {
            try {
                java.awt.Desktop desktop = java.awt.Desktop.isDesktopSupported() ? java.awt.Desktop.getDesktop() : null;
                if (desktop != null && desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                    desktop.browse(java.net.URI.create(uri));
                }
            } catch (Exception ignored) {
            }
        });

        remainingSeconds = response.expiresIn();
        startCountdown();
    }

    @Override
    public void close() {
        stopCountdown();
        scheduler.shutdownNow();
    }

    private void startCountdown() {
        stopCountdown();
        updateCountdownLabel();
        countdownTask = scheduler.scheduleAtFixedRate(() -> {
            remainingSeconds--;
            if (remainingSeconds <= 0) {
                stopCountdown();
            }
            Platform.runLater(this::updateCountdownLabel);
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void stopCountdown() {
        ScheduledFuture<?> task = countdownTask;
        if (task != null) {
            task.cancel(false);
            countdownTask = null;
        }
    }

    private void updateCountdownLabel() {
        if (remainingSeconds <= 0) {
            countdownLabel.setText("Code expired");
        } else {
            long minutes = remainingSeconds / 60;
            long seconds = remainingSeconds % 60;
            countdownLabel.setText("Expires in " + minutes + ":" + String.format("%02d", seconds));
        }
    }
}
