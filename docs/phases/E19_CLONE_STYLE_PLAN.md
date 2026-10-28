# E19 — Clone accent and badge personalization: Phase 6 step 25 (plan before code)

Date 2026-10-28. Branch `phase/e19-clone-style-metadata-20261028` from E18 `b1215755d26c9098b5bd243f210e6f32c6b8002c`. E18 integration CI is a separate acceptance gate and must not be presumed successful. Preserve `main@c055df6`.

## Master plan and gap analysis

Reviewed `docs/ROADMAP.md`, `docs/EXECUTION_PLAN.md`, `docs/STATUS.md`, `CloneMetadataRepository`, `FonesiDatabase`, `AppsViewModel`, `SavedCloneCard`, `AndroidCloneShortcuts`, storage tests and configuration backup. The original 8-phase/32-task roadmap still has unfinished Phase 6 step 25: custom accent color and badge per clone. The current database schema version 3 stores only label/group, all pinned shortcuts use a fixed green badge "F"; existing saved clone cards cannot customize color or badge. This slice implements metadata and UI **without claiming any guest engine functionality**.

## Implementation

1. Define `CloneVisualStyle` / `CloneStylePolicy` in Android-free Kotlin package: exactly six allowed accent keys, bounded 1–2 Unicode code point visible badge, reject newline, control, path/signature or overlong values. Empty badge is allowed and normalizes to "F". Never accept arbitrary CSS, URL or raw bitmap data.
2. Add two optional columns with stable schema: `accentKey TEXT NOT NULL DEFAULT 'teal'`, `badgeText TEXT DEFAULT NULL` to `clone_metadata`. Increment Room schema from 3 to 4 with explicit `MIGRATION_3_4`; preserve all clone UUIDs/engine namespaces, launch tokens, tasks, pause and SDK reservations. **No destructive fallback**.
3. Add namespaced row update under existing metadata path, no SDK dependency. Keep actual `create/delete/rename` unchanged. Style changes require a valid saved row, not a new guest; after writing style update pinned shortcut when possible. Style is not engine identity and does not modify installed APK bytes.
4. `SavedCloneCard`: show an accent circle and initial badge; allow user to edit accent and badge in a dialog with clear limit and readback. Ensure disabled/busy states cannot mutate. `AndroidCloneShortcuts`: render style using only validated color keys and bounded text; update pinned shortcut after style changes (without forcing update on every app refresh).
5. Integration tests with native SQLite/Robolectric: style default, validation/normalization, persisted style across DB reopen, namespace isolation, unchanged IDs/token and sibling clones, invalid mutation rejection, migration from version 3 to 4 without destructive data loss. Current `unknownSchema` test must be shifted from version 4 to 5, **preserving its same fail-closed assertion**.
6. Re-run all cloud/candidate tests/build/lint after source changes, build/check integrated debug candidate and ZIP, plus separate source-native checks. Record results/limitations in `docs/phases/E19_RESULTS.md` only when CI proves them. If toolchain lacks a changed generated Room schema JSON, export it without committing fake identity hashes.
7. Backup format remains names/groups only as documented; do not silently export custom badges or engine/session data. A later explicit opt-in versioned config format change is necessary to include presentation metadata.

## Critical gates and limits

- CI/Room migration and relevant tests must truly pass before marking E19 achieved. Hardware launcher rendering remains unverified pending device.
- **No guest accounts are created, opened, isolated or authenticated** by style changes. SpaceCore/synthetic source runtime capabilities unchanged.
- Do not merge as production-ready until signed APK, Android app matrix and account/physical device gates satisfy the original roadmap.
