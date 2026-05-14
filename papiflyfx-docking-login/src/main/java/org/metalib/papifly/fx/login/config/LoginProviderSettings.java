package org.metalib.papifly.fx.login.config;

import org.metalib.papifly.fx.login.idapi.providers.GenericOidcProvider;
import org.metalib.papifly.fx.login.idapi.providers.GitHubProvider;
import org.metalib.papifly.fx.login.idapi.providers.GoogleProvider;

public final class LoginProviderSettings {

    public static final String GENERIC_SEGMENT = "generic";
    public static final String GITHUB_ENTERPRISE_URL_LEGACY_KEY = "login.provider.github.enterpriseApiUrl";

    private LoginProviderSettings() {
    }

    public static String enabledKey(String providerId) {
        return prefix(providerId) + ".enabled";
    }

    public static String clientIdKey(String providerId) {
        return prefix(providerId) + ".clientId";
    }

    public static String scopesKey(String providerId) {
        return prefix(providerId) + ".scopes";
    }

    public static String discoveryUrlKey() {
        return prefix(GenericOidcProvider.PROVIDER_ID) + ".discoveryUrl";
    }

    public static String workspaceDomainKey() {
        return prefix(GoogleProvider.PROVIDER_ID) + ".workspaceDomain";
    }

    public static String enterpriseUrlKey() {
        return prefix(GitHubProvider.PROVIDER_ID) + ".enterpriseUrl";
    }

    public static String flowKey(String providerId) {
        return prefix(providerId) + ".flow";
    }

    public static String clientSecretStoreKey(String providerId) {
        return "login:provider:client-secret:" + providerSegment(providerId);
    }

    public static String systemProperty(String providerId, String suffix) {
        return "papiflyfx.login." + providerSegment(providerId) + "." + suffix;
    }

    public static String providerSegment(String providerId) {
        if (GenericOidcProvider.PROVIDER_ID.equals(providerId)) {
            return GENERIC_SEGMENT;
        }
        return providerId;
    }

    private static String prefix(String providerId) {
        return "login.provider." + providerSegment(providerId);
    }
}
