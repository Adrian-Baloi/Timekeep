# Halo Tenant Discovery Notes

Verified on Thursday, July 16, 2026 against the Newforma sandbox tenant.

## Tenant

- Base portal: `https://newformaholdingsdev.haloitsm.com/`
- Swagger source: `https://newformaholdingsdev.haloitsm.com/api/swagger/v2/swagger.json`
- Auth info endpoint: `https://newformaholdingsdev.haloitsm.com/api/AuthInfo`

## Verified Responses

### `GET /api/AuthInfo`

Observed JSON:

```json
{"auth_url":"https://newformaholdingsdev.haloitsm.com/auth","tenant_id":"newformaholdingsdev","integrationServiceUrl":""}
```

### OIDC discovery

Source: `https://newformaholdingsdev.haloitsm.com/auth/.well-known/openid-configuration`

Verified fields:

- `issuer`: `https://newformaholdingsdev.haloitsm.com/auth`
- `authorization_endpoint`: `https://newformaholdingsdev.haloitsm.com/auth/authorize`
- `token_endpoint`: `https://newformaholdingsdev.haloitsm.com/auth/token`
- `end_session_endpoint`: `https://newformaholdingsdev.haloitsm.com/auth/logout`
- `revocation_endpoint`: `https://newformaholdingsdev.haloitsm.com/auth/revoke`
- `code_challenge_methods_supported`: `S256`
- `grant_types_supported`: includes `authorization_code` and `refresh_token`
- `scopes_supported`: includes `openid`, `profile`, `offline_access`, `read:users`, `read:assets`, `edit:assets`
- `token_endpoint_auth_methods_supported`: `client_secret_basic`, `client_secret_post`

## App Registration Inputs

- Client ID supplied by tenant admin: `878f661c-9f8d-42a1-bf85-a0b0037c76c0`
- Login redirect URL currently configured in Halo: `timekeep://auth/callback`
- Android app callback kept in scaffold: `timekeep://auth/callback`

### Redirect assessment

- The Halo app registration and the Android manifest are now aligned on `timekeep://auth/callback`.
- That removes the previous redirect mismatch that would have blocked the browser sign-in loop from returning to the app.
- If Postman testing is still needed separately, keep `https://oauth.pstmn.io/v1/callback` as an additional redirect only if Halo allows multiple redirect URIs.

## API Shape Relevant To Sprint 1

- `GET /api/Agent/me` exists in the tenant Swagger and is the chosen authenticated bootstrap call.
- The Swagger operation for `/Agent/me` does not declare a detailed response schema.
- The shared `Uname` schema does expose likely agent fields such as `id`, `name`, `email`, `firstname`, `surname`, `jobtitle`, `teams`, and `initials`.

## Implementation Consequences

- The Android app should discover auth endpoints from OIDC metadata instead of hardcoding them.
- PKCE can be enabled because the tenant advertises `S256` support.
- The token endpoint still appears to require a client secret today, so a pure public-mobile OAuth client is not yet cleanly production-safe.
- This scaffold therefore supports sandbox secret injection while keeping the decision isolated to configuration and auth repository code.



