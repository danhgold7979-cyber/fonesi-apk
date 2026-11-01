# Android apksig

Maven dependency: `com.android.tools.build:apksig:8.11.1` (Google Maven).

Source project: https://android.googlesource.com/platform/tools/apksig/

License: Apache License 2.0, https://www.apache.org/licenses/LICENSE-2.0 . Copyright belongs to the Android Open Source Project and its contributors. This dependency does not relicense Fonesi or third-party guest applications.

Fonesi links the published Java library to verify actual APK cryptographic signatures and parse binary manifests before candidate import/readback. It does not copy the guest signer, sign a guest APK again, or call a trusted certificate list a signature check. The binary XML parser is an internal apksig API, so its version is pinned; an upgrade must rerun the signed APK cluster fixtures.

The dependency is not a virtualization engine. Verifying a base/split set does not show that SpaceCore can import or execute it. The current candidate still refuses split sets.

Test APKs are built from project-controlled manifests using aapt2 and signed with disposable test keys under ignored build output. These keys and APKs are not committed or used to sign the Fonesi application, SDK candidate, phone payload or a user's application.
