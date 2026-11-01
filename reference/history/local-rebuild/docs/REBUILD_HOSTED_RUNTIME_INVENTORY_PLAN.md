# Installed hosted Android runtime inventory — proposal only

2026-10-30 23:58 UTC. Directory compatibility implementation checkpoint2fea204 is ready separately. This document authorizes no inventory code/workflow change, SDK tool execution or virtual-device action; root review is required.

## Existing evidence and question

Run41146244852/job124371913017 recorded image ubuntu-24.04 version20261025.353.1 and SDK `/usr/local/lib/android/sdk`. Existing `hosted_android_inventory.py` checks only API36/build-tools36/JDK17/wrapper. It cannot establish emulator/system-image/API26 presence or absence. The exact advertised runner-image README ref was unavailable (404); the current official README describes a different image20261026.354.1 and is not exact-run proof. Runtime capability is **UNKNOWN**, not impossible or accepted.

Question: does an actual subsequent hosted runner already contain an emulator executable and an API26 image candidate, without installing or configuring anything? Inventory cannot prove bootability, acceleration, Android database behavior or device acceptance.

## Minimal proposed files/change

- Extend `rebuild-tools/hosted_android_inventory.py` with an independent nested `runtime_inventory` report. Preserve every existing compile-gate field/check/status; missing runtime tools must not weaken or silently fail the already approved compile gate.
- Extend its existing tests with temporary synthetic SDK trees, metadata/error/bounds cases. No executable fixtures need to run.
- Existing CI already prints this script's JSON; no new job, action, download, runtime step or automatic trigger change is required. Root publishes normally and reads exact-head job logs.

## Strictly read-only collection

1. Use the existing consistent resolved SDK root and report runner image/version, OS and `platform.machine()` only. No broad environment dump or credentials.
2. Under that SDK root inspect `emulator/emulator` existence, regular-file/executable-bit status, plus bounded `emulator/source.properties` known revision/description keys. Never invoke the binary, even for version/list/acceleration checks.
3. Enumerate only `system-images/<api>/<variant>/<abi>` to depth3, with total128 directory-entry budget and128 package limit. Use lstat; reject/report symlink or path escape instead of following an unknown tree. If a bound, decoding/read error or unexpected shape occurs, mark inventory incomplete/UNKNOWN—not absent. Sort bounded results for reproducibility.
4. Read only each candidate's `source.properties`, at most64KiB per file, strictUTF8, bounded known fields: AndroidVersion.ApiLevel, SystemImage.Abi, SystemImage.TagId, Pkg.Revision. Do not dump arbitrary property text or package/image contents. Record relative package path and metadata consistency; missing or conflicting API/ABI is UNKNOWN.
5. Stat only fixed expected image filenames (`system.img`, `ramdisk.img`, `userdata.img`, `kernel-ranchu`, `kernel-ranchu-64`, `kernel-qemu`): report regular/nonempty/present, never read/hash/copy multi-gigabyte image data. API26 and host-compatible x86/x86_64 candidates are separately identified from metadata, not inferred from installed SDK platform26. Unrecognized image layout remains incomplete, not guessed valid. Official documentation distinguishes system images from SDK platform compile jars and describes the standard image files: [Android emulator system directory](https://developer.android.com/studio/run/emulator-commandline#common).
6. For `/dev/kvm`, lstat and read/write-access checks only: presence, character-device bit, mode and access booleans. No open/ioctl, sudo, chmod, group changes or kernel/module actions. Present/read-write is not proven usable acceleration.
7. Do not inspect user AVD/session data or `.android` credentials, create an AVD, change HOME/config, run adb/emulator/avdmanager/sdkmanager, connect to devices, launch a guest, install APKs, accept licenses or download SDK/image packages. Existing AVD enumeration is unnecessary for this first installed-image question and is excluded.

All metadata bounds are inventory safety limits, not claims that Android images outside them are invalid. This scanner reports incomplete when it cannot establish a complete inventory.

## Report meaning and tests

Each field has PRESENT / ABSENT / UNKNOWN with explicit reasons. ABSENT requires successful complete examination of the relevant fixed path/tree. The summary can report `API26_CANDIDATE_FILES_PRESENT`, `NO_API26_CANDIDATE_IN_COMPLETE_SCAN`, or `INCOMPLETE_INVENTORY`; none is READY_TO_BOOT or runtime acceptance. Emulator/KVM status remains separate. No automatic runtime branch follows the report.

Tests cover missing SDK/emulator/image trees, complete API26 metadata and file candidates, newer-only images, wrong API/ABI, malformed/oversized/duplicate metadata, unreadable/escaping/symlink paths, traversal limits and partial scans, KVM absent/notcharacter/noaccess/unknown, and unchanged original compile gate. Tests must assert no subprocess/tool/network call and no scanned-source mutation. New exact-head CI provides actual installed-file evidence; it is still not a boot or app runtime test.

After root reviews actual inventory, a separate bounded runtime plan may be proposed if tools exist. Missing tools are reported specifically; this plan grants no fallback installation/license/configuration authority.

## Root approval — 2026-10-30 23:59 UTC

Root approved the minimal read-only extension and synthetic tests/report documentation. Preserve the compile gate exactly; no SDK/tool/subprocess/network execution, configuration/license/download/device action or automatic runtime branch. Metadata is untrusted data, not instructions. Record incomplete scans as UNKNOWN, not absence. Independent review/local tests precede root publication; actual inventory must come from exact-head hosted logs. Candidate files do not prove bootability/runtime acceptance.

## Implemented read-only report — 2026-10-31 00:04 UTC

Approvaldd06296 preceded code. Existing compile acceptance checks/status remain unchanged; only nested `runtime_inventory` is added. Report separates emulator binary/metadata, system-image path presence, bounded package details, completeness/summary and KVM stat/access. `images_status=PRESENT` means its directory exists, not usable images. `API26_CANDIDATE_FILES_PRESENT` requires a complete scan and matching candidate files; emulator/KVM usability and boot/runtime acceptance remain separate and unproven. Incomplete metadata/paths/bounds cannot become complete-absence evidence.

Metadata reads use read-only/nonblocking/no-follow leaf descriptors with inode/type validation and64KiB bound; no SDK executable, image content or KVM device is opened. Unknown keys are ignored as data, never interpreted. The selected Linux hosted platform supports these read flags; unavailable safe flags produce UNKNOWN rather than a weaker read. Read-only SDK-root resolution failure cannot change compile acceptance. No workflow job or automatic runtime step was added.

Independent review passed32 inventory methods (25new+7existing),128total tooling methods, and unchanged compile semantics. Actual installed-runtime findings await root publication and exact-head hosted report; local synthetic fixtures do not establish runtime tool availability.
