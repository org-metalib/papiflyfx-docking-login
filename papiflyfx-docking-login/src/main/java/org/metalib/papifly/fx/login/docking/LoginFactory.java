package org.metalib.papifly.fx.login.docking;

import javafx.scene.Node;
import org.metalib.papifly.fx.docking.api.ContentFactory;
import org.metalib.papifly.fx.login.api.AuthSessionBroker;
import org.metalib.papifly.fx.login.idapi.ProviderRegistry;
import org.metalib.papifly.fx.login.runtime.LoginRuntime;
import org.metalib.papifly.fx.login.ui.LoginDockPane;

public class LoginFactory implements ContentFactory {

    public static final String FACTORY_ID = "login";

    private final AuthSessionBroker broker;
    private final ProviderRegistry registry;

    public LoginFactory(AuthSessionBroker broker, ProviderRegistry registry) {
        this.broker = broker;
        this.registry = registry;
    }

    public LoginFactory(LoginRuntime runtime) {
        this(runtime.broker(), runtime.providerRegistry());
    }

    public LoginFactory() {
        this(LoginRuntime.createDefault());
    }

    @Override
    public Node create(String factoryId) {
        if (!FACTORY_ID.equals(factoryId)) {
            return null;
        }
        return new LoginDockPane(broker, registry);
    }
}
