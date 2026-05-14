package org.metalib.papifly.fx.login.runtime;

import org.junit.jupiter.api.Test;
import org.metalib.papifly.fx.login.core.DefaultAuthSessionBroker;
import org.metalib.papifly.fx.login.idapi.ProviderRegistry;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LoginRuntimeTest {

    @Test
    void runtimeCachesInjectedBrokerAndRegistry() {
        AtomicInteger brokerFactoryCalls = new AtomicInteger();
        AtomicInteger registrySupplierCalls = new AtomicInteger();
        ProviderRegistry registry = new ProviderRegistry();
        DefaultAuthSessionBroker broker = new DefaultAuthSessionBroker();

        LoginRuntime runtime = new LoginRuntime(
            ignored -> {
                brokerFactoryCalls.incrementAndGet();
                return broker;
            },
            () -> {
                registrySupplierCalls.incrementAndGet();
                return registry;
            }
        );

        assertSame(registry, runtime.providerRegistry());
        assertSame(registry, runtime.providerRegistry());
        assertSame(broker, runtime.broker());
        assertSame(broker, runtime.broker());
        assertEquals(1, registrySupplierCalls.get());
        assertEquals(1, brokerFactoryCalls.get());
    }
}
