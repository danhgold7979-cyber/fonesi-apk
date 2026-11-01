# Legacy cleanup proposal — READ ONLY, no deletion authorized/executed

Audit baseline local `b409f6056b3add75c71242c6751f97859f518f65`; new build acceptance at remote `f5a11ac7aa014a67b913ab910d9c1bb69efbe101`, run41130512434 SUCCESS. This proposal closes the original clean-rebuild/source-disposal question without pretending the product has been replaced.

## Exact inventory

`REBUILD_LEGACY_CLEANUP_MANIFEST.json` enumerates **134 tracked paths** with original Git blob, mode, byte size and role:10 legacy build-orchestration files,83 implementation/probe files,21 regression-oracle files,3 main-era Room schemas,1 third-party compatibility patch,16 command/fixture-builder scripts. These paths are unchanged from preserved mainc055df6; current tree still carries main-era source, not E82 source. E82 and divergent-head protections live in preserved history/oracle ledger and must not be forgotten because main-era files are removed.

| Legacy area | Files | Why retained / replacement gap |
|---|---:|---|
| app |55| Old auth/config/shortcuts/Room/update flows and their tests. Fresh manager metadata controls do not replace these capabilities. Main has only schemas1–3. |
| core |23| Queue/planner/access/engine contracts and negative tests; fresh capability/state port is not equivalent to the whole core. |
| apk-runtime |5| Real signed base/split audit/private-store code and tests; new module does not yet replace this validation. |
| engine-source |17| Native/JNI/code-layout/library-preparation experiments and Dobby patch; synthetic, not accepted hostile isolation. Preserve provenance/oracle, do not enable it. |
| engine-probe / native-runtime-probe / probe-payload |6 /5 /4| Historical synthetic APK/lifecycle probes, not fresh accepted provider. |
| scripts |16| Includes old bootstrap/license instructions, SDK fetch/source preparation, fixture builders and old build/probe runners. None executed during this audit. They are not fresh build dependencies, but some are unique fixture/probe oracles. |
| root settings/build/properties |3| Active legacy project graph and Kotlin/KSP plugin declarations; independent fresh Android build uses its own settings/build/properties via explicit `-p rebuild-android`. |

Preserve all docs/spec/history and three `third_party/*/NOTICE.md` files. Do not remove Dobby patch/source provenance while that experimental source remains. No committed APK/AAR/keystore/database found in audited legacy directories; this is not authority to delete ignored/untracked/signing/user data.

## Required fresh infrastructure and oracle references

- Root `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` and `.properties` are used by fresh hosted compile and hash-pinned inventory. **Keep byte-identical**, including official provenance; do not replace wrapper scripts to hide the legacy graph.
- Keep `.gitignore` (signing keys/local properties/build outputs ignored), `.github/workflows/rebuild-kernel.yml`, all `rebuild-*`, `spec/**`, `docs/**`, README/current-vs-historical boundary, notices and all Git refs/history.
- `rebuild-storage/spec/legacy-room/{1..6}.json` and manifest are already independent, immutable E82 schemas. Reverified every byte against a fresh restored E82 tree; removal of old app paths must not alter those six copies or turn fixture tests into real migration claims.
- No fresh source/build workflow references `scripts/`, old module opt-in properties, `../app`/`../core`, or third_party code paths. `app/` substrings in fresh checks resolve inside `rebuild-android/`; historical `git_path` in schema manifest is provenance, not a runtime filesystem dependency. Fresh build explicitly invokes `./gradlew -p rebuild-android` with SDK/Java autodownload disabled.

## Narrow R0 acceptance assessment

**R0 meets the current master gate; accept R0 only.** On2026-10-30 21:25UTC:

1. Rehashed full bundle: `d0516e03126dd60b20e5dc954065dd694469d0d6b3689691cbc7e4717866404a` at `/workspace/shared/fonesi-apk-recovery-20261030/all-refs.bundle`.
2. Initialized a fresh empty bare repository `/tmp/fonesi-r0-audit-gpat6qjq/restore.git`; `git bundle verify`, explicit wildcard fetch `+refs/*:refs/*`, exact comparison with the64-entry `ref-manifest.json`, then `git fsck --full --no-reflogs` all passed. Existing independent restore also matches all64 IDs. Bundle HEAD advertisement is not a65th ref in this manifest.
3. Restored main is `c055df600f87ee9ddf0cd5d6bfc67d80d145e79d`; E82 is `2fac016e3b28e4d9f21390c221cad1df7a382c86`. Remote main read-only check also returns c055df6. Local rebuild/history are intentionally newer; never reset them to the old manifest.
4. Existing `REBUILD_RECONCILIATION.md` records semantic review of14 divergent heads and unique negative assertions. This is the required divergent-head oracle, not blanket equivalence of the new product.

R0 does not accept R1–R7, engine feasibility, Android durability or source replacement. Bundle is local/shared recovery evidence, not proof of an independently durable offsite backup. Retain bundle/manifest and originals; no percentages.

## Smallest recoverable removal proposal (Stage A, root review required)

Only propose removal of **`build.gradle.kts` and `gradle.properties`** and replacement of **`settings.gradle.kts`** with a tiny fail-closed root-build guard explaining the exact fresh `-p rebuild-android` command. This removes obsolete root plugin/configuration activation without discarding any implementation, test oracle, schema, native patch or capability requirement. Preserve wrapper bytes. Add a source regression test for the guard and absence of legacy project includes/plugins; exercise a root Gradle invocation on the already authorized hosted toolchain and require the specific refusal, then require normal fresh compile/lint/package CI to remain green.

This is intentionally **not full cleanup** and not proof all old scripts are inert: the16 scripts still exist and some directly fetch/build fixtures before root Gradle. Do not run them as part of this cleanup. A separate reviewed entrypoint-quarantine/removal manifest is required to retire those commands without losing fixture provenance; there must be no claim that Stage A alone prevents every manual legacy invocation. An empty root project that silently succeeds is not an acceptable guard.

Current master says remove superseded implementation only after equivalent-or-stronger replacement guards. Therefore deletion of83 legacy implementation/probe files or21 regression files **is not justified now**: provider, APK audit, auth, shortcuts, configuration, full migration/update paths lack replacement acceptance. Backups alone do not satisfy that gate. If root wishes to archive-only relocate unfinished reference source earlier, it must explicitly amend that criterion and retain readable source/test/notice provenance; it must not call the missing features complete.

## Review / verification commands before any future mutation

```sh
# Verify manifest baseline and no unnoticed local edits first.
git status --short
git diff --name-only c055df600f87ee9ddf0cd5d6bfc67d80d145e79d -- app core apk-runtime engine-source engine-probe native-runtime-probe probe-payload scripts build.gradle.kts settings.gradle.kts gradle.properties
# Inspect references; classify historical documentation/provenance rather than blindly removing matches.
rg -n 'scripts/|includeSourceEngine|includeEngineProbe|includeNativeRuntimeProbe|\.\./app|\.\./core|third_party' rebuild-* .github/workflows/rebuild-kernel.yml
./rebuild-kernel/check.sh
./rebuild-logic/check.sh
python3 rebuild-storage/check.py
./rebuild-android/check-model.sh
python3 -m unittest discover -s rebuild-tools -p 'test_*.py'
```

For proposed Stage A: add exact root-refusal test and preserve expected wrapper digests; hosted inventory must still find only preinstalled toolchains, installed JDK17 parity and `./gradlew -p rebuild-android ... :app:assembleDebug :app:lintDebug` plus packaged audit must pass. No SDK license/install fallback. Compare before/after changed path list against root-approved manifest. Recover any approved removed file with `git show b409f6056b3add75c71242c6751f97859f518f65:<exact-path>` into a separate review location first, never broad checkout/reset over new work. No main merge/force push/device/signing/user data actions.

**Decision requested:** accept R0 evidence as narrowly complete; approve or reject the exact two-file Stage A removal/one-file root guard. No deletion or build-config change has occurred in this proposal.
