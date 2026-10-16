# HaloITSM asset search contract

Verified against the `newformaholdingsdev` HaloITSM tenant Swagger on 2026-08-10.

## Read operation

```http
GET {resource_server}/Asset
Authorization: Bearer {access_token}
Accept: application/json

search={normalized_identifier}
pageinate=true
page_size=50
page_no={page}
includeuser=true
includechangehistorylastupdated=true
```

The Swagger operation is tagged `Device`, returns `200`, and documents `400`, `401`, and `429`. A technician can also receive `403` from effective Halo permissions even though that status is not listed on this operation.

The `200` body is `Device_View`:

- `page_no`
- `page_size`
- `record_count`
- `assets`, an array of `Device_List`

The app maps only tenant-documented fields. `inventory_number` is presented as the asset tag. Halo's configurable `key_field`, `key_field2`, and `key_field3` values are interpreted using their corresponding `*_name` labels for serial number, hostname/device name, and model. Other mapped fields are `manufacturer_name`, `item_name`, `username`, `status_name`, `site_name`, and `changehistory_last_updated`.

## Match safety

`search` is a broad server-side search because a serial may be stored in any configured key field. Before publishing results, the repository applies a case-insensitive exact comparison against `inventory_number` and all three key fields and deduplicates by Halo asset ID.

- zero exact matches: no-match state;
- one exact match: confirmation/details state;
- two or more exact matches: candidate list requiring technician selection.

No collection operation uses `first()` or `firstOrNull()` to select a search result.

## Permissions and validation

The tenant Swagger requires authentication but does not name the scope on the operation. The tenant's OAuth discovery metadata advertises the least-privilege `read:assets` scope. The Android authorization request must include it, and the native API application's Permissions tab must allow it. The technician's own Halo role and access boundaries also remain authoritative. Validate one known serial, one unknown serial, and one intentionally duplicated serial with a test technician account before release.

## Safe diagnostics

Use the `TimekeepNetwork` Logcat tag for method, sanitized route, status, and duration. Do not log or share bearer tokens, authorization codes, refresh tokens, credentials, query values, personal data, or response bodies. Network Inspector should be used only on the DEV tenant and captures containing those values must not be exported or attached to tickets.
