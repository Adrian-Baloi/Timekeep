# HaloITSM asset management contract

Verified on 2026-08-11 against the production tenant OpenAPI document at
`https://newformaholdings.haloitsm.com/api/swagger/v2/swagger.json`.

- Product: HaloITSM
- OpenAPI: 3.0.1
- API version: v2
- Halo application version: not exposed by the public Swagger document
- Resource server used for verification: `https://newformaholdings.haloitsm.com/api/`
- Authorisation server discovered from the tenant: `https://newformaholdings.haloitsm.com/auth`

The checked-in build remains pointed at DEV by default. Configure production URLs and tenant name through ignored `local.properties`, Gradle properties, or environment variables; do not commit credentials or tokens.

## Active user and agent search

The production tenant documents `GET /Users` with server-side `search`, pagination, active/inactive filters, and agent inclusion controls. The app starts a debounced request only after four non-whitespace characters:

```http
GET /Users?search={query}&pageinate=true&page_size=25&page_no=1
    &includeactive=true&includeinactive=false
    &exclude_agents=false&listagentuserfirst=true
```

The response is `Users_View`, whose `users` property contains `Users_List` records. The repository follows `record_count` across result pages and deduplicates by Halo user ID. A positive `linked_agent_id` identifies a linked agent; otherwise the option is shown as an end user. Inactive records are filtered again as a defensive check.

## Active site list

The production tenant documents `GET /Site` with pagination and active/inactive filters:

```http
GET /Site?pageinate=true&page_size=100&page_no={page}
    &includeactive=true&includeinactive=false&order=name
```

The response is `Site_View`. The repository follows `record_count`, deduplicates by Halo site ID, and removes inactive records defensively.

The management UI then applies a case-insensitive allowlist and displays only these sites, in this order:

1. Boston Office
2. EleVia Office
3. Levis Office
4. Levis Office (Bimone)
5. London Office
6. Manchester Office
7. Montreal Office
8. Remote | CA
9. Remote | International
10. Remote | US

## Minimal asset writes

The production tenant documents `POST /Asset` with an array of `Device` objects. The `Device` schema has no required properties, includes nullable `site_id`, and represents assignment with a `users` array of `Users_List` records. It does not document `user_id` as an asset property.

Deploying sends only the already-resolved Halo asset ID, destination site ID, and selected Halo user ID:

```json
[
  {
    "id": 42,
    "site_id": 7,
    "users": [
      { "id": 9 }
    ]
  }
]
```

Returning to Inventory sends the destination site and an explicitly empty users array:

```json
[
  {
    "id": 42,
    "site_id": 7,
    "users": []
  }
]
```

No status, hostname, serial, tag, customer, or unrelated asset fields are sent.

## Can the actions run without fetching the asset?

Not from only `WLT-xxx` or a scanned serial number. The tenant Swagger exposes asset updates through `POST /Asset` using the Halo numeric asset `id`; it does not document a write route keyed by inventory number or serial. The scan lookup is therefore still required to resolve and confirm the correct asset and obtain its ID.

After that lookup, both actions can use a minimal update without an additional `GET /Asset/{id}` before or after the write. The repository update method performs only `POST /Asset`, and unit tests assert that neither Deploying nor Returning to Inventory invokes the asset search during the update. The UI applies the accepted values locally after Halo returns success.

This is contract-level verification, not a live production mutation. No bearer token was supplied and no production asset was changed. A reversible live test with an authorized test asset is still required to confirm tenant runtime behavior, especially whether an empty `users` array clears assignment exactly as documented by the request schema.

## Authentication and permissions

The production OAuth discovery document advertises `read:assets`, `edit:assets`, `read:users`, and `read:customers`, but the application's current Permissions tab does not expose or grant `read:users`. The permissions explicitly enabled for this app are `read:assets`, `edit:assets`, and `read:customers`. The tenant Swagger itself does not map individual operations to OAuth scopes, so `GET /Users` under this exact grant still needs a runtime check with a restricted technician account.

Use the following configured request as the least-privilege starting point:

```properties
HALO_SCOPES=openid profile offline_access read:assets edit:assets read:customers
```

Halo roles and customer/site access restrictions remain authoritative even when the token contains those scopes. If `GET /Users` alone still returns `403` after a complete sign-out and sign-in, confirm with the Halo administrator whether this tenant can expose `read:users` to the native application or whether the signed-in technician's role/customer boundaries deny user lookup.

After changing requested scopes, rebuild/reinstall the app and completely sign out and sign in again. An existing authorization or refresh token does not automatically gain newly requested scopes. If Halo returns `401` again after the repository successfully refreshes the token, the app reports an authorization rejection instead of incorrectly describing it as a generic expired session.
