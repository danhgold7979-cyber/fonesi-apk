# E29 — signed private synthetic component ticket dispatch results

Date: 2026-10-28. Workflow: [E29 signed private synthetic component ticket dispatch #40731817711](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40731817711).

## Result

**E29 CI PASS** on source SHA `10fc6b623a42b93e5fb62601b2f849c82b1fd471`. The dedicated workflow passed source-engine ticket tests, native probe build/lint, component/process/permission/ELF audit, APK signature checks, and three fresh AOSP 34 emulator proofs.

- E24 regression: PASS — signed private synthetic DEX created an attached View and received the host lifecycle transcript.
- E25 regression: PASS — private manifest metadata matched the signed synthetic payload; no guest Activity registration was claimed.
- E29: PASS — E25's private component catalog was bound to a UUID-scoped ticket containing package/version/signer/component/path and the exact private APK SHA-256. The ticket was serialized, delivered by an explicit internal Activity intent, reopened against the private store, and revalidated immediately before the existing synthetic private View proof.
- E29 receipt confirms `synthetic_component_dispatch_verified == true`, `dispatch_ticket_private_apk_sha256` equals the built signed payload SHA-256, and `real_guest_activity_registered`, `actual_guest_activity_attached`, `third_party_component_executed`, `kernel_uid_isolation`, and `production_clone_accepted` remain false.
- Test-only artifact: [E29 private component ticket dispatch](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40731817711/artifacts/12835337834). Digest: `sha256:9e09ec9500d8f915b11a18493c58b2cd25f69767b667cb138e7c0b00a8bb0ae0`.

## Implementation

- `SyntheticComponentDispatchTicket` enforces canonical UUIDs, a strict bounded JSON schema, cluster/package/version/signer/component/path identity, private-root containment, and SHA-256 readback.
- Ticket tests cover round-trip validation, stale UUID, application/activity mismatch, signer mismatch, changed hash, altered private bytes, unknown JSON fields and malformed UUID.
- The existing host Activity issues the ticket only after the signed private manifest catalog passes, then starts an explicit internal host Activity instance. The receiving instance reopens the private cluster, rechecks the catalog, validates the ticket and only then runs the existing synthetic DEX/View lifecycle.
- The emulator runner requires the E24/E25 regressions, exact payload hash binding and explicit negative production-acceptance flags.

## Limits / not accepted

E29 is a synthetic-only dispatch bridge. It does **not** execute the declared `PayloadActivity` as a real Android guest Activity; the host Activity still owns the Android Window and the E24 private synthetic View is the rendered object. No real guest Activity/Service/Receiver/Provider registration, arbitrary third-party component dispatch, guest PackageManager/Binder/permission mediation, account/session isolation, split APK production import, Samsung ARM64, session-preserving updates or release signing has been proven.

Product readiness remains **~30% estimated (28.00% weighted rubric)**. Do not raise it because the synthetic dispatch receipt passed.

## Next

E30: provider feasibility and production-engine acceptance gate. Inspect the pinned production candidate and existing source-engine adapter against the actual product requirements. If the provider cannot satisfy split APK cluster import, real guest lifecycle and per-account isolation, keep those capabilities disabled and document the exact blocking APIs; do not substitute a synthetic host Activity for a real guest. See [E1/E2/E3 engine audit](../ENGINE_SPLIT_AUDIT.md) and [product plan](../PLAN.md).
