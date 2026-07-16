# Model Types

This package keeps the small value types that the rest of the app shares.

- Auth session and pending authorization state are serializable because they are persisted securely.
- Agent summary is intentionally small because the current authenticated home is only a placeholder shell.
