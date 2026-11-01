# Style core HOST integration — original scope, not V2 activation

The owned StyleValue suite is required exactly once in the JDK17 HOST aggregate,
with `javac --release 17`, a positive parsed assertion count and a source-bound
receipt/log. LIMITED HOST output cannot produce that receipt. Local evidence is
still 128 assertions on JDK21 using source17/target17; no hosted result is prefilled.

The reviewed style source is detached from `:app`: settings includes only `:app`,
and its source sets do not include `core`. Existing APK proof remains source
`a3372fa6be3b6872bd5f4c402ebd72c233701127`, run `41290554652`.
A conservative Git changed-path gate compares against that ancestor. Only explicit
HOST-only files and documentary paths can skip a redundant APK build. Unknown
paths, app/Gradle/toolchain/packaged-verifier changes require compilation; a missing
ancestor is an error, never an empty diff. Manual workflow dispatch always requests
compilation. The workflow change here only adds routing/receipt plumbing; it does
not change Android steps. Future changes to this routing need independent review.

A skipped compile emits no new APK, COMPILE receipt or baseline acceptance. The
HOST artifact includes `compile-scope.json` naming the original proof source/run,
current SHA and compared paths. A compile on a later changed input must still pass
all existing strict gates, now including the style HOST prerequisite.

Focused integration fixtures cover missing/zero/duplicate/limited/wrong-tier style
output; required command identity; unknown/Android/toolchain changes; manual
verification; and strict workflow ordering. They are synthetic Python tests, not
Android execution. The unchanged 128 Java assertions are not rerun locally merely
for this integration edit; hosted JDK17 execution remains pending publication.

Canonical progress remains 5/32 original-plan tasks. SUP-S01/S02/S03 and T25 are
not fully accepted. All work-profile V2 material remains PROPOSED_NOT_ACTIVE; no
owner tradeoff consent, profile provisioning or eight-app compatibility is claimed.
