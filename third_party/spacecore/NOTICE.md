# SpaceCore candidate: experimental only

Source: https://github.com/FSpaceCore/SpaceCore

Pinned commit: `3826a2fa1ac492fbbe7435ccb52074c1e2b702de`.

Artifact path: `app/libs/flib-release.aar`.

SHA-256: `4e45a474a5ab5764ad80818c2855c4fbd8540f0a540f975cf85a6f0dd8809b1f`.

The author's README at the pinned commit states:

> The SpaceCore SDK is a free product that is NOT open-source, and you are free to use it without notifying the author. It can also be used for commercial purposes.

This is a proprietary binary SDK, not Fonesi source code. This notice records the publicly stated permission; it does not relicense the SDK or establish ownership of all its components. Do not copy the demo application's keystore or signing configuration.

Fonesi's production/scanner app does not depend on it. Only the separate `engine-probe` application loads this candidate, on explicit opt-in, for an offline synthetic-data experiment. The probe removes inherited permissions except foreground-service/notification permissions and restricts inherited components with an application signature permission. No real social accounts should be used in the probe.

The SDK documents ARM32/ARM64 and Android 6–14. Its ARM64 core ELF has 4 KB LOAD alignment. The current probe therefore only enables initialization on ARM64, 4 KB-page devices running API 26–34. This check is a conservative experiment boundary, not a claim the SDK works on every device in that range.

The host still targets API 36. We do not reduce its target SDK to disguise incompatibility. A successful build does not show the SDK will work under that target on a phone. Runtime checks remain pending.
