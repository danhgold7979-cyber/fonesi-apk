# Retained input comparison — verified existing HOST/COMPILE snapshot

Comparison source: `7367a445d83e602aba1fcf9b526b3f7ae35bfc92`.
Exact tree: `3212d99077cf7f3148a924cafb4e8c4f3657e133`.
Successful run: `41323172540`.
Independent actual review SHA256:
`1747b36a15aa6d3413014ab1ca17bc6948019c0e0c683d61d7a941d778686d2e`.
Actual JDK17 HOST evidence: 149 Python cases and 83/128/151/60/269/65
assertions. APK: 64,474 bytes, SHA256
`e11a5aa9f70c40e57c770b82e2bf504fb7fe2e748e763b39f1938516da08ccca`.
This reuses a retained input snapshot, not new task/runtime acceptance.
Historical a3372fa6/41290554652 acceptance and later evidence remain immutable.

Comparison resolves the exact pinned Git tree, requires accepted source ancestry,
and compares all changed paths. Missing object, mismatched tree, failed ancestry,
Git error or invalid filename encoding fails the job; no fallback empty diff and
no implicit Git fetch/import. App/core/assets/config/unknown paths force compile.
Workflow edits also force compile, removing the older narrow patch's filename
exception. Manual verification always compiles. Only reviewed HOST/documentary
changes may skip; the scope receipt says new_COMPILE NOT_RUN and cites the retained
run. A skipped compile job emits no new APK/compile/baseline receipt. HOST gates it.

The earlier fetch denial was respected until new explicit owner approval on
2026-11-01. The exact local phase-01 fetch then succeeded at 16:03 UTC.
Local Git now resolves the exact public source/tree and its remote ancestry;
this is not an Android rebuild. Local implementation commits have separate
history from connector-created public commits: they must not be represented as
public descendants. Publication must use the current public phase-01 parent,
with its exact tree checked, and hosted ancestry checks remain mandatory.

No app/Gradle/SDK/permission change, new COMPILE claim, task completion,
provider activation or V2 approval is introduced by this comparison update.
