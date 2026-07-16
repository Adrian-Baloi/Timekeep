# UI Shell

This package holds the minimal auth-only presentation layer.

- `TimekeepRoot` reacts to the app state machine and keeps browser launch handling in one place.
- The signed-out screen explains discovery, PKCE, and client-secret readiness instead of hiding setup blockers.
- The authenticated screen stays intentionally narrow so the app does not creep into scan or device features before the next sprint.
