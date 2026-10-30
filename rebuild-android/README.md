# Fresh Android manager scaffold

Independent native Java17 presentation project, not an engine or imported old app.
Distinct applicationId `vn.fonesi.rebuild.debug`; all unsupported actions disabled.
No requested permissions, storage migration, provider, Java21 kernel import or user-data access.

Only the inventory-gated hosted CI experiment is authorized for build. It requires
preinstalled API36/build-tools36.0.0/JDK17; no SDK installation or license action.
The root verified Gradle8.13 wrapper is reused with `-p rebuild-android`; root
historical Gradle settings are not evaluated. Debug APK is CI review only, not release.
Source tests and APK inspection do not substitute for device/UI/isolation acceptance.

## Metadata presentation model (R5.1)

`ManagerModel` is new dependency-free Java17-compatible source compiled inside the
Android project. It models immutable metadata, refresh ordering, accent-insensitive
search and visible-only selection with stale revision rejection. All entries remain
unverified/quarantined/unavailable; selection never enables operations. Activity
renders the initial unconnected count only. No stored entries, fake sample rows,
provider calls or configuration changes are connected.

`./rebuild-android/check-model.sh` runs direct-source contract tests. Installed
JDK17 CI uses `--release 17`; the limited local JDK21 fallback establishes behavior
only, and prints that boundary. Android compile/lint additionally verifies source
and API compatibility. UI event wiring, persistence, rotation, accessibility,
authentication, shortcuts and device execution remain separate unaccepted work.

## Native presentation wiring (R5.2)

Foreground-bound `ManagerController` drives native search, exact-group and
visible-selection controls. Epoch/revision tokens reject stale UI events; pause
invalidates pending refresh tickets, clears selection and renders hidden metadata.
Controller and Activity both guard render-triggered callbacks. Source ingestion is
not connected: the real Activity stays truthfully unconnected, with no sample rows.
Original import/clone/launch/migration buttons remain disabled.

Fake-view lifecycle tests are not Android UI/device tests. Input/page budgets,
normalized search caching and large-list virtualization are mandatory before a
storage adapter is connected; current filtering applies to loaded metadata only.

Manager row identity is the exact opaque `(engineKey, cloneId)` pair, matching the retained schema key rather than assuming globally unique UUIDs. It is display-only and carries no executable provider authority. No catalog reader is connected; external-data budgets/decoding/quarantine and real persistence remain separate gates. Empty/whitespace opaque keys are preserved according to the schema, not normalized into a different identity.
