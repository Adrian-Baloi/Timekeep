# Asset data layer

The app defaults to `HaloAssetRepository`. `FakeAssetRepository` remains available for explicit offline UI development. Both implementations use the same `AssetRepository` interface and expose only app-level asset, user-option, and site-option models.

## Select the real repository

Set these Gradle properties for a non-production or sandbox HaloITSM environment:

```properties
USE_FAKE_ASSET_REPOSITORY=false
HALO_RESOURCE_SERVER_URL=https://your-tenant.example/api/
```

Use the Resource Server URL displayed in `Configuration > Integrations > Halo API`. Do not store an OAuth client secret or a bearer token in this project, committed Gradle properties, or `BuildConfig`.

The AppAuth-based delegated sign-in supplies fresh short-lived access tokens to the runtime provider and clears them on sign-out. With no valid authenticated session, repository calls intentionally return the app-level `Unauthenticated` error.

## Confirmed DEV tenant read contract

On 2026-08-10, the DEV tenant OpenAPI document at `/api/swagger/v2/swagger.json` confirmed:

- `GET /Asset` with `search`, `pageinate`, `page_size`, and `page_no`;
- `includeuser` for assigned-user data;
- `includechangehistorylastupdated` for the latest asset change timestamp;
- a `Device_View` response containing `record_count` and `assets`;
- `Device_List` fields used by the app: `id`, `inventory_number`, configurable `key_field*` values and labels, `manufacturer_name`, `item_name`, `username`, `status_name`, `site_name`, and `changehistory_last_updated`.

Halo key fields are configurable. The mapper uses their returned labels to identify serial number, hostname/device name, and model. The generic Halo search can return partial or related matches, so the repository keeps only case-insensitive exact matches across `inventory_number` and `key_field`, `key_field2`, and `key_field3`. It never chooses a first result.

The OpenAPI operation does not name its scope, but the DEV tenant OAuth discovery metadata advertises `read:assets`. The authorization request includes that scope, and the DEV API application's Permissions tab must also allow it. The technician's effective Halo role remains an additional restriction. Re-check this contract after Halo upgrades.

The user, site, and asset-change ticket contract was updated on 2026-09-27. Confirmed changes use `POST /Tickets` with ticket type `79`; the app no longer writes to `POST /Asset`. See `docs/halo-asset-management.md` for the exact request shapes, custom-field mappings, scope configuration, and the no-extra-fetch limitation.

Update only the Retrofit DTOs and mappers when the tenant schema differs. Compose must continue to receive app models through `AssetRepository`.

## Error boundary

`HaloAssetRepository` translates network, timeout, HTTP, validation, server, parsing, and unexpected failures into `AssetDataError`. The ViewModel displays only the safe technician message and never a server body, exception message, or stack trace.

Debug Logcat output uses the `TimekeepNetwork` tag and contains only HTTP method, sanitized route, status, and duration. It never logs hosts, query values, authorization headers, credentials, bodies, exception messages, or stack traces. In Android Studio Network Inspector, inspect only timing/status while developing with real data; do not capture, export, or share authorization headers, query values, personal data, or response bodies.
