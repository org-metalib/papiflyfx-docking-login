package org.metalib.papifly.fx.login.docking;

import javafx.scene.Node;
import org.metalib.papifly.fx.docking.api.ContentStateAdapter;
import org.metalib.papifly.fx.docking.api.LeafContentData;
import org.metalib.papifly.fx.login.runtime.LoginRuntime;
import org.metalib.papifly.fx.login.ui.LoginDockPane;

import java.util.Map;
import java.util.Objects;

public class LoginStateAdapter implements ContentStateAdapter {

    public static final int VERSION = 1;
    private final LoginFactory factory;

    public LoginStateAdapter() {
        this(new LoginFactory());
    }

    public LoginStateAdapter(LoginRuntime runtime) {
        this(new LoginFactory(runtime));
    }

    public LoginStateAdapter(LoginFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    @Override
    public String getTypeKey() {
        return LoginFactory.FACTORY_ID;
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public Map<String, Object> saveState(String contentId, Node content) {
        if (content instanceof LoginDockPane) {
            return Map.of("version", VERSION);
        }
        return Map.of();
    }

    @Override
    public Node restore(LeafContentData content) {
        return factory.create(LoginFactory.FACTORY_ID);
    }
}
