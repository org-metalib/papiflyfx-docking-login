package org.metalib.papifly.fx.login.idapi;

public interface ProviderRegistryListener {

    void onProviderAdded(IdentityProvider provider);

    void onProviderRemoved(IdentityProvider provider);
}
