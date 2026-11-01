# R4 synthetic runtime and production evidence design

Specification only; no Android execution performed by the rebuild yet. Earlier E78–E83 receipts are historical and must remain attributed to their source SHA.

## Receipt envelope

Each receipt must bind format version, canonical random run UUID, exact source revision, provider revision, host/payload APK SHA-256, immutable cluster identity, Android API (strict integer), ABI/page size, positive app UID and exact process identity. Record observed event sequence, not mere requested commands. Strict boolean fields distinguish host-stub lifecycle, guest registration, third-party execution, isolation and physical device. Reject unknown schema, duplicate fields, coercions, stale run, missing events, forged success after failure, wrong source/artifact or environment.

## Synthetic phases

1. Controlled signed fixture only. Two UUID namespaces A/B, original untouched. Establish baseline files/preferences/SQLite/resources/version and negative cross-read cases.
2. Constructor-only proof must assert null Context/Application/Window; run only on main Looper with exact private classloader/constructor count. No claim of lifecycle.
3. Host-stub framework lifecycle has onCreate/onResume/Context/Window, but remains host package/UID; guest flags stay false.
4. Actual provider proof must exercise guest Application/Activity/Service/Provider, explicit/implicit intents, Binder, permissions, notifications, URI grants and native resources. A host proxy alone cannot pass.
5. Hostile A attempts B access via absolute/relative paths, symlinks, dir FDs, direct native syscalls, shared memory, IPC/providers/grants/notifications. Positive independent owner inspection confirms B unchanged. Same-UID namespace is not assumed secure; failure keeps isolation unaccepted.
6. Process kill/restart, device reboot, lock/foreground loss and provider disable; no duplicate dispatch, foreign namespace mutation or lost source ownership. Synthetic marker survives where expected.
7. Frozen full split update with dependency/config resources, native ABI/4k/16k where supported; fault injection before/after staging, publication, native entry and verification. Verify all affected peers, exact bytes and retained data; uncertain outcome remains review-only, never automatic replay.

## Real-app/device gate

Only after synthetic/provider isolation passes: user installs APK and logs into test accounts privately. Required apps individually version-bound, original + two guests, shortcut routing, session retention, media/notifications/permissions/background and update preservation. Samsung SM-A525F OS/ABI must be measured, not inferred; second OEM before multi-OEM claim. Native 16KB static alignment is not runtime proof. Release signing and in-place upgrade are separate gates.
