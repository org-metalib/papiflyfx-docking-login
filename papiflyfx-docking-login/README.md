# papiflyfx-docking-login

`papiflyfx-docking-login` contributes authentication-oriented settings to the docking runtime.

It provides:

- `AuthenticationCategory` for provider configuration, active session state, and refresh-token inventory.
- `SecureSecretStore` plus `SecretStoreSecureAdapter` so login-specific byte[] secrets delegate to the shared settings `SecretStore`.
- A lightweight in-memory `AuthSessionBroker` runtime used by the settings UI and samples.

Add the module alongside `papiflyfx-docking-settings` so the Authentication category is discovered through `ServiceLoader`.
