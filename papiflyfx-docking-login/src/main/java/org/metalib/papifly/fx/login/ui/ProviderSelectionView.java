package org.metalib.papifly.fx.login.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.metalib.papifly.fx.login.idapi.ProviderDescriptor;

import java.util.List;
import java.util.function.Consumer;

public class ProviderSelectionView extends VBox {

    private final Consumer<String> onProviderSelected;

    public ProviderSelectionView(List<ProviderDescriptor> providers, Consumer<String> onProviderSelected) {
        this.onProviderSelected = onProviderSelected;
        setSpacing(12);
        setPadding(new Insets(24));
        setAlignment(Pos.CENTER);

        Label title = new Label("Sign In");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        getChildren().add(title);

        Label subtitle = new Label("Choose a provider to continue. Configure client IDs in Settings > Authentication or via papiflyfx.login.* JVM properties.");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(320);
        getChildren().add(subtitle);

        for (ProviderDescriptor descriptor : providers) {
            Button button = new Button("Sign in with " + descriptor.displayName());
            button.setMaxWidth(280);
            button.setPrefHeight(36);
            button.setOnAction(e -> onProviderSelected.accept(descriptor.providerId()));
            getChildren().add(button);
        }
    }
}
