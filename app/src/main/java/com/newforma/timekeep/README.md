# App Orchestration

This package wires the Android shell together.

- `TimekeepApplication` builds the long-lived container.
- `AppContainer` owns config, storage, JSON, and HTTP dependencies.
- `AppViewModel` coordinates bootstrap, sign-in, refresh, callback handling, and sign-out.
- `MainActivity` hosts Compose and receives the browser redirect.
- `AppUiState` keeps rendering deterministic so the auth gate stays easy to reason about.
