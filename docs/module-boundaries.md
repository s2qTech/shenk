# Module Boundaries

Updated: 2026-07-13

## Shenk Web

The accepted desktop UI remains in `src/app.js`. It owns DOM events, rendering,
view state, and feature composition.

Reusable browser-independent modules:

- `src/recommendation-engine.js`: deterministic fallback recommendation logic.
- `src/sync-profile-core.js`: migration-code validation, encrypted profile payloads,
  profile ID derivation, and configuration payload validation.
- `src/snapshot-storage.js`: IndexedDB snapshot storage with localStorage fallback.

The app passes browser APIs into the adapters. Modules must not read or mutate DOM,
calendar state, cloud records, or routine classification. The shared-record model,
normalization, patch preview, and merge behavior remain covered by the existing
Node integration tests while further extraction is deferred to sync v2.

## Timer

The timer page in `index.html` owns DOM rendering, event wiring, audio, wake lock,
and catalog presentation.

Reusable modules:

- `timer-execution-core.js`: `steps[].execution` normalization and runtime expansion.
- `timer-preview-core.js`: groups expanded runtime steps into one user-facing action
  and calculates action progress.
- `timer-session-core.js`: active, elapsed, paused, and interrupted session timing.

Timer catalog loading preserves the template-provided `scene` value. No module may
infer, overwrite, delete, or migrate routine metadata without an explicitly approved
data-change task.

## Compatibility Rules

- Both sites remain static GitHub Pages deployments and keep their existing URLs.
- Existing templates without `execution` remain `simple` actions.
- Existing snapshots continue to use IndexedDB first and localStorage as fallback.
- Cloud API ownership and entity write boundaries are unchanged.
