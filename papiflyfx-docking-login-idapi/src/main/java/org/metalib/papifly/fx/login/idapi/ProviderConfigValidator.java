package org.metalib.papifly.fx.login.idapi;

import java.util.List;

public interface ProviderConfigValidator {

    List<String> validate(ProviderConfig config);
}
