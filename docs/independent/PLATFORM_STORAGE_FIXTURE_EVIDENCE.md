# Platform fixture — revised separate owned test APK, runtime NOT_RUN

## Retained failed first entry

Public source ea485ea94e22635c8ed3eded43a741741ea1bd76, run41326131467:
HOST170 Python and production COMPILE succeeded independently. Both initial fixture APKs assembled, but fixture acceptance FAILED: MissingApplicationIcon; test merger report not in the assumed decision-tree location; actual AGP-generated test manifest injected a required platform android.test.runner library. Empty Gradle runtime configurations did not prove absence of this platform classloader dependency. No fixture PASS receipt or accepted pair was emitted; diagnostics retained. No device/install/runtime action occurred.

Official already-pinned AGP8.13.2 source archive was inspected after separate explicit approval, SHA2560693aa79c9231ffd5917be45ca8c5e25049bba3d9503ce60ada8d56c7b9ed1b4. ProcessTestManifest demonstrates generated temp manifests, final blame output and deletion; it delegates generator content to a separate builder class, whose source was not inspected. Actual packaged output establishes the observed library. No upstream manifest/library is stripped or whitelisted.

## Coherent repair source

Two regular first-party app modules now avoid the generated androidTest pipeline entirely. `storage-fixture-test` owns an explicit platform Instrumentation manifest targeting only the separate `storage-fixture`; no permissions, exported components, platform uses-library, shared UID or external runtime dependency. Production code/UI/manifest and the five-case target harness remain unchanged in this repair. Owned fixture-only icons fix lint rather than suppress it.

Test compilation consumes only the target task's actual API classes JAR as compileOnly, with explicit task dependency. Pinned AGP sources InternalArtifactType/PublishingSpecs/BundleAllClasses identify COMPILE_APP_CLASSES_JAR as an external compile/API artifact and classes.jar output. Exact path remains fail-closed until actual compilation. Receipt requires fresh same-source JAR, SHA/full member hashes and exact singleton compile-only dependency; both runtime configurations must be empty. Test APK actual DEX class definitions must exclude all target storage/core prefixes including nested classes, not merely omit a top-level name. No stub API, source duplication or reflective invocation.

Both regular-module merger reports must be fresh and hash-bound; only each authored manifest origin is allowed. The exact observed pinned-AGP injection suffix `reason: use-sdk injection requested` is recognized for INJECTED records only. No generated temp/external origin fallback. Both fixture lint reports must have ZERO issues; no finite Hint classification remains. Every Hint/Warning/Error and Gradle failure remains fatal. TXT/XML/HTML, signatures, resource binding, exact package/testOnly/debug/backup/target checks and actual APK assets/classes remain required.

Local affected proof: fourteen owned verifier/aggregate negative cases and six source-boundary predicates PASS. No Android test execution or new fixture COMPILE acceptance is claimed. Fresh hosted build and independent artifact review remain required. The always-after-HOST fixture job remains conservative until an exact accepted fixture comparison baseline exists. Failure retains diagnostics only; no PASS receipt/accepted pair before every gate succeeds.

Actual runtime additionally requires separate authorization and an available owned Android environment. Original362 assertions, V1 acceptance, inactive V2 proposal, auth/provider/device gates and task counts are unchanged.
