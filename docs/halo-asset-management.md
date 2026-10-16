# HaloITSM asset management contract

Verified on 2026-10-16 against the Newforma tenant OpenAPI document at
`https://servicedesk.newforma.com/api/swagger/v2/swagger.json` and the request shape tested by the
workflow owner. The ticket-to-asset mapping was confirmed from a known-good request captured from
the tenant's Halo web UI.

- Product: HaloITSM
- OpenAPI: 3.0.1
- API version: v2
- Halo application version: not exposed by the public Swagger document
- Resource server used for verification: `https://servicedesk.newforma.com/api/`
- Authorisation server discovered from the tenant: `https://servicedesk.newforma.com/auth`

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

## Asset-change workflow tickets

The app does not update `POST /Asset`. It creates an Asset Change ticket through `POST /Tickets`.
The tenant documents an array of `Faults` records and a `201` response. The workflow owner verified
ticket type `79` and these tenant-specific custom fields:

| Meaning | Field ID | Value |
|---|---:|---|
| Action | `564` | `1` for Deploying; `2` for Returning to Inventory |
| Account number | `289` | Selected Halo user/account ID on deploy; omitted on return |
| Office location | `404` | Selected Halo site ID |

Both actions send the asset ID in `asset_number` and in the `assets` collection. The latter creates
the Related CI relationship that the workflow operates on. The ticket also receives Halo's system
`client_id` and `site_id` values for the selected office. Deploying adds the selected account number:

```json
[
  {
    "tickettype_id": 79,
    "asset_number": 42,
    "assets": [{ "id": 42 }],
    "client_id": 12,
    "site_id": 7,
    "customfields": [
      { "id": 564, "value": "1" },
      { "id": 289, "value": "9" },
      { "id": 404, "value": "7" }
    ]
  }
]
```

Returning to Inventory creates one ticket and omits account-number field `289`, matching the
known-good Halo web request:

```json
[
  {
    "tickettype_id": 79,
    "asset_number": 42,
    "assets": [{ "id": 42 }],
    "client_id": 12,
    "site_id": 7,
    "customfields": [
      { "id": 564, "value": "2" },
      { "id": 404, "value": "7" }
    ]
  }
]
```

UI transport and form-state fields such as `files`, `attachments`, `form_id`, `utcoffset`,
`return_this`, `dont_do_rules`, and `donotapplytemplateintheapi` are not replayed. Unlike the Halo
web client, the app has not locally applied the complete ticket template and therefore allows the
server to apply the ticket type's normal template, rules, and workflow.

## Can the actions run without fetching the asset?

Not from only `WLT-xxx` or a scanned serial number. The scan lookup is still required to resolve and
confirm the correct asset and obtain the numeric Halo asset ID used by `asset_number` and `assets`.

After that lookup, both actions submit exactly one `POST /Tickets` HTTP request without an additional
asset read. Each action creates exactly one ticket. Unit tests assert that neither action invokes
asset search during submission. A successful response confirms ticket creation; the configured Halo
workflow remains responsible for applying the asset change.

No live tenant mutation was made during this code change. The workflow owner separately supplied a
known-valid request. Validate both action paths with authorized test records before production release.

## Authentication and permissions

The tenant OAuth discovery document advertises `read:assets`, `edit:tickets`, `read:users`, and
`read:customers`. The tenant Swagger itself does not map individual operations to OAuth scopes, so
the API application's Permissions tab and the technician's effective Halo role must allow each call.

Use the following configured request as the least-privilege starting point:

```properties
HALO_SCOPES=openid profile offline_access read:assets edit:tickets read:users read:customers
```

Halo roles and customer/site access restrictions remain authoritative even when the token contains
those scopes. If `GET /Users` or `POST /Tickets` returns `403` after a complete sign-out and sign-in,
confirm the corresponding application permission and technician role with the Halo administrator.

After changing requested scopes, rebuild/reinstall the app and completely sign out and sign in again. An existing authorization or refresh token does not automatically gain newly requested scopes. If Halo returns `401` again after the repository successfully refreshes the token, the app reports an authorization rejection instead of incorrectly describing it as a generic expired session.
