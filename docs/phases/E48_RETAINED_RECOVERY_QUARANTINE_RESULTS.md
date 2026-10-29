# E48 — Retained private-source recovery quarantine: CI results

Date: 2026-10-29. Repo [danhgold7979-cyber/fonesi-apk](https://github.com/danhgold7979-cyber/fonesi-apk), branch `phase/e48-retained-recovery-quarantine-20261029`, stacked [Draft PR #22](https://github.com/danhgold7979-cyber/fonesi-apk/pull/22) on E45. Original product Phase 4, steps 18–20. This is a bounded technical reliability slice, not another product Phase.

## Exact-source technical acceptance

- Source SHA **`b02ef1a9b245c211f565cfb16918aa692043cd9a`**, [GitHub Actions #40850652184](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40850652184) **SUCCESS**. GitHub logs explicitly report **395/395 tests cloud** and **395/395 tests candidate**, 0 test failures. Both gates passed builds, lint, APK signature and permission audits.
- E40–E45 and E48 source audits, pinned Android SDK/JDK toolchain, single-APK candidate, split-disabled and non-destructive private APK checks passed.
- Artifact **12886593929**: `fonesi-E48-retained-recovery-quarantine-TEST-ONLY`, containing sanitized test receipts and Room schema v6. [Workflow/artifact](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40850652184).
- **Five new JVM/core tests** cover retained APPLYING false success from an intentionally matching legacy-engine clone version, pre-marker owner-only policy denial, policy readback exception, retained PREPARING snapshot preservation and REVIEW_REQUIRED hold despite matching old engine metadata. Prior legacy process-death recovery test still passes.

## Implemented code and boundary

`UpdateRunner.recoverLocked()` invokes the already existing E45 `mayUseLegacySource(task)` gate **before** legacy snapshot cleanup or `engine.listClones()` for PREPARING/APPLYING/VERIFYING/REVIEW_REQUIRED. For retained or legacy-owned private tasks, APPLYING/VERIFYING are moved only into REVIEW_REQUIRED via the durable journal; PREPARING and REVIEW_REQUIRED remain held without deletion or a fabricated success. E42 Room owner/reference transitions remain atomic. Ordinary unassociated legacy-source recovery still uses its previously verified route.

E48 does not provide a private APK update executor, guest Activity lifecycle, independent UID/account isolation, split APK import into the candidate provider, real social-app update/session proof, physical Samsung testing, or release signing. No change to Room v6 or production provider capability flags. Product weighted estimate **28.00% (~30% rounded)**, accepted real third-party clone applications **0**. Other E46/E47 source-authority branches have independent CI; E49 integration in progress and needs its own acceptance. Keep Draft and `main` unchanged. The report-only commit after this source SHA will need separate CI.
