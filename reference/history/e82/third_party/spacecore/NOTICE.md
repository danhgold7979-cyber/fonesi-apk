# SpaceCore candidate: experimental only

Source: https://github.com/FSpaceCore/SpaceCore

Pinned commit: `3826a2fa1ac492fbbe7435ccb52074c1e2b702de`.

Artifact path: `app/libs/flib-release.aar`.

SHA-256: `4e45a474a5ab5764ad80818c2855c4fbd8540f0a540f975cf85a6f0dd8809b1f`.

The author's README at the pinned commit states:

> The SpaceCore SDK is a free product that is NOT open-source, and you are free to use it without notifying the author. It can also be used for commercial purposes.

This is a proprietary binary SDK, not Fonesi source code. This notice records the publicly stated permission; it does not relicense the SDK or establish ownership of all its components. Do not copy the demo application's keystore or signing configuration.

The default cloud/scanner build does not depend on it. The separate `engine-probe` remains an offline synthetic-data experiment. An additional integrated debug candidate can be built with `-PengineCandidate=true`; it includes the actual SDK and requires explicit startup opt-in. The candidate permits Internet/network state, camera/microphone and foreground-service/notification permissions while removing the remaining inherited permissions. Version 0.5 also declares the normal USE_BIOMETRIC/USE_FINGERPRINT permissions for the host access gate; this does not encrypt or independently sandbox SDK guests. Both overlays restrict inherited exported components with an application signature permission. Neither build is a certified production engine. No real social accounts should be used before synthetic data isolation has been verified on the target phone.

The SDK documents ARM32/ARM64 and Android 6–14. Its ARM64 core ELF has 4 KB LOAD alignment. The current probe therefore only enables initialization on ARM64, 4 KB-page devices running API 26–34. This check is a conservative experiment boundary, not a claim the SDK works on every device in that range.

The host still targets API 36. We do not reduce its target SDK to disguise incompatibility. A successful build does not show the SDK will work under that target on a phone. Runtime checks remain pending.

The candidate exposes only the SDK's single-file storage import. It refuses split sets, verifies archive identity/signatures, requires an SDK-owned private copy and does not enable device/SIM/VPN/path spoofing. It does not fulfill the multi-social-app compatibility requirement until a suitable split-capable engine and device validation are available.
