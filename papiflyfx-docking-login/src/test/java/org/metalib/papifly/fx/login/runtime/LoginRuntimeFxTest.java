package org.metalib.papifly.fx.login.runtime;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metalib.papifly.fx.docking.api.LeafContentData;
import org.metalib.papifly.fx.login.docking.LoginFactory;
import org.metalib.papifly.fx.login.docking.LoginStateAdapter;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ApplicationExtension.class)
class LoginRuntimeFxTest {

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
        stage.setScene(new Scene(new StackPane(), 800, 600));
        stage.show();
    }

    @Test
    void loginFactoryUsesRuntimeDiscoveredProviders() {
        runFx(() -> show(new LoginFactory().create(LoginFactory.FACTORY_ID)));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(hasButtonWithText(stage.getScene().getRoot(), "Sign in with Google"));
        assertTrue(hasButtonWithText(stage.getScene().getRoot(), "Sign in with GitHub"));
    }

    @Test
    void loginStateAdapterRestoreUsesRuntimeDiscoveredProviders() {
        LoginStateAdapter adapter = new LoginStateAdapter();

        runFx(() -> show(adapter.restore(LeafContentData.of(LoginFactory.FACTORY_ID, "login:main", LoginStateAdapter.VERSION))));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(hasButtonWithText(stage.getScene().getRoot(), "Sign in with Google"));
        assertTrue(hasButtonWithText(stage.getScene().getRoot(), "Sign in with GitHub"));
    }

    private void show(Node content) {
        StackPane root = (StackPane) stage.getScene().getRoot();
        root.getChildren().setAll(content);
    }

    private boolean hasButtonWithText(Node node, String text) {
        if (node instanceof Button button && text.equals(button.getText())) {
            return true;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (hasButtonWithText(child, text)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void runFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
