# Standalone original-scope style core evidence — 2026-11-01 11:26 UTC

Fresh source implements the bounded `STYLE_CORE_CONTRACT.md` validation component only, mapped to original SUP-S01/S02/S03 / APK-P6-T25. No V2 architecture activation or whole requirement/task acceptance.

## Exact reviewed inputs

- `core/src/main/java/vn/fonesi/apk/foundation/StyleValue.java`: SHA256 `b50ff9917a66af6ac6fdcaedfe7d37ea8212d6f5521c965701753ac6bde78b87`.
- `core/src/test/java/vn/fonesi/apk/foundation/StyleValueTest.java`: SHA256 `1c5e863232c8aeb8924b3aa67482d292c300ebe0265638a9673ddd40312e44d8`.
- `tools/test_style_core.sh`: SHA256 `bce3f662785b35a6e15c97e01e83bfa328577a2a2cf939cd187ae3ee56d7c577`.
- Contract SHA256 `22be4c48fa00f98e73e1c104b4cc86fb4db79cfb2b1d673178ae5c385bb4af19`.

Independent reviewer `/root/review_apk_proposal_contracts` reported SOURCE-CLEAR for those exact three hashes at11:26:45UTC after full source/test/script inspection against the contract. Reviewer reused the author's affected local test evidence; no claim of independently rerun tests.

## Actual command and qualification

`bash tools/test_style_core.sh --limited-jre`: exit0, **128 owned in-memory assertions PASS**. Actual runtime OpenJDK21.0.12.1, Debian build21.0.12.1+1-1-deb13u1; module compiler `-source17 -target17`, not `--release17`. Therefore **LIMITED_HOST only**, not Java17 API verification, Android Unicode tables/rendering or packaged-app evidence. Production core avoids Java9 Set.of; this precaution is not an Android runtime proof.

Only affected style checks ran. Existing UpdatePreflight checks, accepted P0 APK/CI and unrelated tests were not repeated. Existing hosted command list/counts and build/manifest/workflow remain unchanged; this new standalone script is not falsely reported as already run by CI.

## Implemented versus open

Implemented: exact six accents, explicit new default, strict internal typed fields, forbidden raw Unicode categories before trim, bounded inert display badge, unchanged raw text and immutable results. No external serialization format, resource interpretation, old implementation import or new dependency.

Open: Android/JDK17 integration, actual renderer/launcher, persistence/migrations/explicit validated repair and row-level concurrency/ownership. No catalog identity, app branding, manifest permission, profile state, provider or runtime dispatch was changed. Canonical V1 progress remains5/32 tasks; V2 accepted0 and all eight app compatibility results UNKNOWN.
