# App Module

The `app` module contains the first working Android slice only.

- It keeps the project intentionally narrow around authentication.
- Runtime Halo tenant values flow into `BuildConfig` through Gradle properties or environment variables.
- The code is split into small packages so auth, network, model, and UI logic stay easy to extend when scan and device flows arrive later.
