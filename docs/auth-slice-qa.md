# Auth Slice QA Checklist

This checklist is the current verification target for the auth-only Android sprint.

## Happy Path

- First launch with no session lands on the signed-out screen.
- Runtime discovery succeeds against the tenant auth server.
- Tapping `Sign in to Halo` launches the browser flow.
- Callback with a valid code exchanges for tokens.
- The app stores the session securely.
- `GET /api/Agent/me` succeeds and renders the placeholder authenticated home.

## Guardrails

- Missing required config fields produce a deterministic blocked state.
- If OIDC discovery says the token endpoint needs a client secret and none is configured, sign-in stays blocked with a clear explanation.
- Malformed callback URIs return the user to a signed-out state with an error.
- State mismatch in the callback clears the pending auth request and blocks token exchange.
- The app never marks the user authenticated until `/api/Agent/me` succeeds.

## Session Recovery

- Relaunch with a valid stored token goes straight into validation.
- A first `401` on `/api/Agent/me` triggers one refresh attempt only.
- Refresh success updates the stored session and retries bootstrap once.
- Refresh failure clears the session and routes back to the signed-out screen.

## Sign-Out

- Local sign-out clears access token, refresh token, and pending auth state.
- Back navigation does not reopen the authenticated shell after sign-out.

## Explicitly Not Part Of This QA Pass

- Camera access
- Barcode, QR, or serial scanning
- Asset search and ambiguity handling
- Device reassignment or creation
- Ticket workflows
