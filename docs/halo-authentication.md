# HaloITSM native sign-in

This project uses AppAuth for Android with Authorization Code Flow and S256 PKCE. Credentials are entered only in an approved external browser or Custom Tab; the app does not contain an embedded credential form.

## Halo API application registration

In the target HaloITSM tenant, open `Configuration > Integrations > Halo API > View Applications` and create a separate application for this Android environment.

- Authentication method: `Authorisation Code (Native Application)`.
- Login identity: the interactive Halo user. Apply only the roles and customer/team boundaries needed by the mobile workflow.
- Redirect URI: `timekeep://auth/callback` for the current DEV configuration. This value must match `HALO_REDIRECT_URI` byte-for-byte.
- Requested scopes for this tenant's enabled application permissions: `openid profile offline_access read:assets edit:tickets read:users read:customers`.
- Halo permissions: enable only the matching asset-read, ticket-create/edit, user-read, and site/customer-read permissions needed by the technician. The signed-in technician's own Halo role and access boundaries still apply, so both layers must allow each operation.
- `Use a client secret for the token request`: **off**. A native/public app cannot protect a client secret.

Record the tenant's displayed Resource Server URL, Authorisation Server URL, tenant name, Agent Portal URL, HaloITSM version, and `/api/swagger` document. The default DEV URLs in the build came from the supplied `Timekeep-dev` reference project; the tenant version was not available in source.

The harmless authenticated bootstrap is `GET {resource server}/Agent/me`. It was exercised by the supplied reference project, but it must be reconfirmed in this tenant's `/api/swagger` after tenant or Halo upgrades.

## Local configuration

Keep the public client ID outside Git. Add it to the ignored root `local.properties`, a user Gradle properties file, or an environment variable:

```properties
HALO_CLIENT_ID=replace-with-the-native-application-client-id
```

Optional overrides use the same locations:

```properties
HALO_RESOURCE_SERVER_URL=https://tenant.example/api/
HALO_AUTH_SERVER_URL=https://tenant.example/auth/
HALO_TENANT_ID=tenant-name
HALO_REDIRECT_URI=timekeep://auth/callback
HALO_SCOPES=openid profile offline_access read:assets edit:tickets read:users read:customers
HALO_INCLUDE_TENANT_ON_TOKEN_REQUESTS=true
USE_FAKE_ASSET_REPOSITORY=false
```

For `HALO_SCOPES`, this project's ignored `local.properties` deliberately takes precedence over the user-wide Gradle property. This prevents an unrelated global scope setting from silently reducing the permissions requested by this app.

Do not add access tokens, refresh tokens, authorization codes, passwords, or client secrets to any project file.

## Runtime behavior

- AppAuth generates and validates OAuth state and generates an S256 PKCE verifier/challenge.
- Discovery must advertise S256. Halo's discovery metadata describes the authentication methods supported by the server as a whole, so it is not used to infer whether this individual API application requires a secret.
- Authorization-code exchange and token refresh are sent as a public client using PKCE and no client secret.
- AppAuth state, including access and refresh tokens, is AES-GCM encrypted with an Android Keystore key. Android backup is disabled for the app.
- Before an API call, AppAuth returns a fresh access token and performs a single-flight refresh when necessary.
- An API `401` forces one refresh and one retry. A second `401`, a `403`, or a failed refresh clears the local session and returns to sign-in.
- Sign out clears the encrypted local authorization state and in-memory bearer token. It does not claim to terminate the Halo browser SSO session.

If token exchange returns `invalid_client`, first confirm that `Use a client secret for the token request` is off and that the API application was saved. If the tenant still requires `client_secret_basic`, `client_secret_post`, or another confidential-client method and cannot register a public native client, use a small backend-for-frontend for code exchange and refresh. Never add the secret to this Android project.
