# Timekeep Android Auth Shell

This repository now contains the first-sprint Android auth-only shell for the HaloITSM mobile workflow.

The current slice intentionally stops at:

1. App launch and local config check
2. Halo agent sign-in entry
3. Authorization-code callback handling
4. Token exchange and secure local session persistence
5. Authenticated bootstrap through `GET /api/Agent/me`
6. Placeholder authenticated home
7. Local sign-out

## Source Of Truth

- Platform: Android only
- Halo sandbox: `https://newformaholdingsdev.haloitsm.com/`
- Verified tenant discovery facts from Thursday, July 16, 2026 live in `docs/halo-tenant-discovery.md`

## Included In This Sprint

- Compose-based Android shell
- Browser-based Halo OAuth sign-in flow
- Runtime OIDC discovery from the tenant auth server
- Secure token persistence with encrypted shared preferences
- One-refresh session recovery path
- Authenticated placeholder home driven by `/api/Agent/me`
- Folder-level markdown docs for each created code area

## Explicitly Out Of Scope In This Repo Revision

- Camera scanning
- Manual serial entry
- Asset lookup screens
- Device reassignment UI
- Device registration UI
- Ticket flows
- Bulk operations

## Local Configuration

Put these values in user Gradle properties (`%USERPROFILE%/.gradle/gradle.properties`) or environment variables before running the app:

```properties
HALO_CLIENT_ID=878f661c-9f8d-42a1-bf85-a0b0037c76c0
HALO_CLIENT_SECRET=
HALO_REDIRECT_URI=timekeep://auth/callback
HALO_SCOPES=openid profile offline_access
```

These tenant defaults are already prefilled from live discovery and can be overridden if needed:

```properties
HALO_ENVIRONMENT_LABEL=Newforma DEV Sandbox
HALO_RESOURCE_SERVER_URL=https://newformaholdingsdev.haloitsm.com/api
HALO_AUTH_SERVER_URL=https://newformaholdingsdev.haloitsm.com/auth
HALO_TENANT_ID=newformaholdingsdev
HALO_USE_PKCE=true
HALO_INCLUDE_TENANT_ON_TOKEN_REQUESTS=true
```

## Registered App Notes

- Current Halo sandbox client ID: `878f661c-9f8d-42a1-bf85-a0b0037c76c0`
- Current registered login redirect URL in Halo: `timekeep://auth/callback`
- The Android manifest and Gradle defaults already match that callback.
- The previous Postman callback can still be useful for separate manual OAuth testing, but it is no longer the app redirect used by this Android shell.

## Important Security Note

The live tenant OIDC metadata currently advertises `client_secret_basic` and `client_secret_post` token authentication methods only. This scaffold therefore supports sandbox secret injection so the auth shell can be exercised, but production mobile delivery should move the code-for-token exchange behind a trusted backend or broker instead of shipping a reusable client secret inside the APK.

## Verification Status

- Halo sandbox Swagger, AuthInfo, and OIDC discovery were verified on July 16, 2026
- The local Codex shell did not expose Java or Android SDK tooling on PATH during this run, so the project files were scaffolded carefully but not compiled inside this session



