# Auth Logic

This package isolates everything that is Halo-OAuth-specific.

- `HaloEnvironmentConfig` converts build-time values into runtime-safe config.
- `AuthRepository` handles OIDC discovery, auth URL creation, code exchange, and refresh.
- `SecureSessionStore` keeps tokens and pending auth state encrypted on-device.
- `PkceUtil` keeps verifier, challenge, and state generation in one place.
