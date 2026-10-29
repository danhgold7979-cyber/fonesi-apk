# E55 — inspect the pinned candidate SDK for a safe split-import contract (pre-code plan)

Date: 2026-10-29. Repository `danhgold7979-cyber/fonesi-apk`. Product scope remains **Phase 4, steps 17–20**; E55 is a technical increment, not a product Phase.

Parent: E54 branch `phase/e54-unblocked-queue-work-selection-20261029`, E54 code/test SHA `6b03fbca63d0e4bc83e96296f568e9aad13eec51`, [CI #40861102493](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40861102493) **SUCCESS**. E54 PR #28 remains Draft and `main@c055df600f87ee9ddf0cd5d6bfc67d80d145e79d` is unchanged.

## Problem / evidence already established

- `AndroidSourceCatalog` collects `sourceDir + splitSourceDirs`; `FileSourceSnapshotter` copies all APK members with per-file digest and rechecks the source; `ApkSetAuditor` validates package/version/signing identity, signed binary manifests, split identity/dependency graph, required split types and mutation.
- The candidate install path is still single-APK only: `ManagedGuestEngine.checkSource` rejects `apkPaths.size != 1`; `SpaceCoreBackend.checkedInput` requires exactly one APK and calls `FCore.installPackageAsUser(File, userId)`; `RuntimeBindings.supportsSplits=false`; `EngineCapabilities.splitClusterImportSupported=false`. The UI already blocks split-cluster creation.
- Do **not** infer split support from APK-set validation or from the existence of a multi-file snapshot. It is only safe to enable after a supported SDK API installs the complete cluster atomically and runtime readback confirms the exact package/version/signers for the installed guest.

## Implementation plan

1. Inspect the exact E54 parent and current pinned SDK contract. Keep the AAR checksum pinned to `4e45a474a5ab5764ad80818c2855c4fbd8540f0a540f975cf85a6f0dd8809b1f` and commit `3826a2fa1ac492fbbe7435ccb52074c1e2b702de`; do not commit the AAR or any credentials.
2. Add a candidate-only API-surface probe against the AAR's actual `com.fvbox.lib.FCore` public methods using the pinned JDK/toolchain. Report method names and parameter/return types relevant to install, split/session, package inspection, launch, stop and uninstall. Avoid logging private device/account data. Do not rely on internet search or SDK README claims as runtime proof.
3. Add a deterministic audit script and tests that assert: (a) the audited SDK hash matches the pinned value; (b) the probe output is tied to that exact SDK hash; (c) multi-APK sources remain rejected before any guest user/registry/native mutation unless an explicit reviewed split-install adapter is present; (d) `supportsSplits` and `splitClusterImportSupported` remain false when no complete supported API is established; and (e) single-APK preflight/install/readback behavior remains unchanged.
4. If the pinned SDK exposes a documented, usable atomic multi-APK import API, write down its exact signature and transaction/readback semantics and implement only the narrow adapter/tests that can be proven in cloud. Do not turn the capability on solely because a method name contains “split” or “install”.
5. If no safe API exists, record that negative result with exact SDK hash and method-surface evidence. Keep split import disabled, do not discard split members, do not route to the original app, and make the next decision an explicit alternative-engine evaluation rather than a fake pass.
6. Run the dedicated E55 pinned cloud/candidate workflow with E40–E54 safety audits, tests, build, lint, APK signature/permission checks, and a guard against enabling unsupported split/provider/lifecycle claims. Update the E55 results and status only with receipts from the exact source SHA.

## Acceptance criteria

- The exact pinned AAR and its inspected API surface are identified in a reproducible report.
- Tests prove split input still fails before native mutation if no safe multi-APK API is available; all single-APK and queue-safety tests remain passing.
- No capability flag is enabled without verified atomic import and guest readback evidence.
- CI passes on the exact E55 code/test SHA. If the SDK cannot support the requirement, E55 may PASS as a **capability investigation**, but the product split-import blocker remains open and Phase 4 is not complete.

## Explicit non-goals / safety boundaries

No real guest Activity lifecycle, account isolation, separate kernel UID, full split installation, or phone runtime is claimed by an API reflection probe. Do not change `supportsSplits=false` or `splitClusterImportSupported=false` without actual support. Keep the PR Draft; do not merge/release the product as complete. Product readiness remains **28.00%**, with **0** accepted real third-party clones.
