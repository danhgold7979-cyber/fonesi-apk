# V2 smallest feasibility gate — NOT RUN

## Prerequisites and authoritative references

Owner scope agreement + independent ADR/minimal-source/security review; explicitly authorized disposable emulator provisioning through visible OS consent; existing approved SDK/image/toolchain; owned fixtures only, no real accounts. No ADB owner-grant shortcut, existing MDM takeover, hidden API or new license acceptance.

- [Build a DPC](https://developer.android.com/work/dpc/build-dpc), [managed-profile provisioning](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#ACTION_PROVISION_MANAGED_PROFILE)
- [installExistingPackage affiliation constraint](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#installExistingPackage(android.content.ComponentName,%20java.lang.String))
- [PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller), [LauncherApps](https://developer.android.com/reference/android/content/pm/LauncherApps)
- [Work-profile data separation](https://developer.android.com/work/managed-profiles), [shared app update guidance](https://support.google.com/work/android/answer/9350374?hl=en)
- [Quiet-mode caller restriction](https://developer.android.com/reference/android/os/UserManager#requestQuietModeEnabled(boolean,%20android.os.UserHandle))
- [Suspend access](https://developer.android.com/work/dpc/security#disable_access_to_apps), [hide](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setApplicationHidden(android.content.ComponentName,%20java.lang.String,%20boolean))

These links establish API contracts only; the independent feasibility report read them. No runtime result is inferred.

## Ordered experiment / decisive failures

| Step | Exact inputs and output/readback | Negative/race/cancel tests; stop condition |
|---|---|---|
| 1 Personal baseline | Owned signed fixture V1 with base+required split; write synthetic markerA. Record package/version/signers/digests and personal profile serial. | Incomplete/foreign-signed/mixed-generation snapshot denied; source unreadable means unsupported. No permission expansion/download fallback. |
| 2 Visible provisioning | Managed-user capability + allowed provisioning; system consent; verify receiver callback, actual owner component, enabled distinct profile serial. | Existing profile/MDM/restriction or cancel: no ready state. Reboot during provisioning reconciles, no auto-reprovision. |
| 3 Original-source transport/install | Personal snapshot sent through reviewed bounded OS cross-profile transport; work-local PackageInstaller session; signed base/split membership independently read back. | Wrong caller/signature/profile, stale operation, oversized stream/revoked URI grant rejected. User-action-required staged visibly. Lost result/partial failure stays uncertain; no installExistingPackage affiliation workaround. |
| 4 Correct launch/data | Personal-side accessible profile/component targeted; real Activity/service/receiver callbacks and work markerB; personal markerA unchanged. | Stale/recycled userhandle, missing profile/component, locked profile cannot fall back personal. Managed/native/URI cross-read attempts fail; mere directory names never count. |
| 5 Restart/durability | Restart manager/fixture and reboot; reconcile exact serial/generation and retainedA/B. | Profile removal/recreation invalidates all old commands/shortcuts; unknown state not reported stopped. |
| 6 Truthful controls | Suspend/resume and hide/unhide with OS readback; observe launch/notification behavior. Owner uses system work-profile pause. | Do not infer process/network termination. No programmatic quiet-mode/default-launcher escalation. Manager lock bypass via normalOSlauncher is disclosed, not false universal lock. |
| 6b Complete shared scope | Demonstrate a supported public route proving the complete device-wide set of affected installations, not only LauncherApps-accessible profiles. Record source of completeness evidence and role restrictions. | Existing inaccessible users/profiles or inability to establish completeness blocks Fonesi updates. No hidden API, privileged/ADB enumeration or scope guessing; profile list alone is insufficient. |
| 7 Shared update | Owned V2 same signer/complete splits; preview personal+work shared version impact; normal install/update; both codeversions observed and markers preserved. | Unknown additional affected profiles blocks. Cancel beforecommit harmless; interruptedcommit reconciles. Independently change/remove/update personal package through OS while Fonesi locked/absent: invalidate preview/snapshots, then reconcile without replay. No downgrade/data reset to call rollback. |
| 8 Personal uninstall survival | Owner-confirmed personal-only fixture uninstall; prove work package/signer/version and markerB remain and work launch succeeds (or report actual unsupported state). Reinstall personal fixture normally with no copied work data and write a fresh markerA2; markerA is not falsely claimed retained after personal uninstall. | Reject all-users uninstall or unknown target scope. Preserve work metadata, invalidate stale personal source, and require a fresh readable signed-source snapshot before future updates. This is the explicit P068/RM-16b proof. |
| 9 Scoped uninstall | Owner-confirmed work-local app uninstall; workmarkerB gone, reinstalled personalinstallation/fresh markerA2 remain. | Wrongprofile or unknown scope blocks; never wholeprofile wipe as appdelete. Disposableprofile teardown requires separate explicit consent. |

Receipts include exact manager/DPC/fixture source and artifact hashes, environmentAPI/ABI/page-size/OEM, caller/owner/profile principals, signer/member identities, operation generation, before/after states, commands, cancellation outcomes and redacted diagnostics. HOST/COMPILE/ANDROID/PHONE remain separate. First modernAPI31+ fixture proof then boundaryAPI26/28 and currentAPI37 coverage only with available authorized environments; no platform-version migration implied for current target36.

GO means all nine numbered fixture stages plus shared-scope gate6b pass including falsification cases and independent review. It qualifies only the measured fixture/platform route, not eight target apps, phone support or release. NO-GO means a concrete incompatibility/permission/source-transport/identity/data-loss issue; preserve evidence and stop dependent product implementation until resolved or scope explicitly changed.
