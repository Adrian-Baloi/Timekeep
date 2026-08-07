# Asset data layer

The app defaults to `FakeAssetRepository`, so the existing screens remain usable without a live HaloITSM tenant. Both fake and real implementations use the same `AssetRepository` interface and return only app-level `Asset` models.

## Select the real repository

Set these Gradle properties for a non-production or sandbox HaloITSM environment:

```properties
USE_FAKE_ASSET_REPOSITORY=false
HALO_RESOURCE_SERVER_URL=https://your-tenant.example/api/
```

Use the Resource Server URL displayed in `Configuration > Integrations > Halo API`. Do not store an OAuth client secret or a bearer token in this project, committed Gradle properties, or `BuildConfig`.

After delegated sign-in succeeds, pass the short-lived access token to the runtime provider and clear it on sign-out:

```kotlin
AppDependencies.container.accessTokenProvider.update(accessToken)
AppDependencies.container.accessTokenProvider.clear()
```

The prototype sign-in screen does not yet perform delegated OAuth. With the real repository selected and no runtime token, calls intentionally return the app-level `Unauthenticated` error.

## Verify the tenant contract before live use

`HaloApi` uses the classic `/Asset` route, `search` filter, and array-shaped POST convention as an explicitly unverified starting point. Before enabling the real implementation, inspect the target HaloITSM tenant's `{Agent Portal URL}/api/swagger` and confirm:

- the exact Asset route casing;
- the serial-number query/filter and pagination envelope;
- the asset response fields used by `HaloAssetDto`;
- the accepted update fields and whether the POST body is an array;
- the minimum application scopes and agent/application-identity permissions;
- the response body returned by a successful update.

Update only the Retrofit DTOs and mappers when the tenant schema differs. Compose must continue to receive app models through `AssetRepository`.

## Error boundary

`HaloAssetRepository` translates network, timeout, HTTP, validation, server, parsing, and unexpected failures into `AssetDataError`. The ViewModel displays only the safe technician message and never a server body, exception message, or stack trace.
