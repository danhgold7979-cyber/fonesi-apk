# Existing-only style write entry — proposed next source boundary

This is original SUP-S01/S02/S03/P055/RM-13b storage work for the independently
adopted manager-catalog v1 format. It does not adopt V2, migrate legacy Room data,
bootstrap a catalog or activate a UI action. No existing user DB is touched by
source authoring/compile. Actual writes require a future real auth/lifecycle broker
and explicit owned Android validation; no fake broker is provided.

## Purpose and dependencies
OwnedCatalogWriter already implements guarded conditional style update/readback and
commit uncertainty, but accepts a caller-exclusively-owned raw handle. The next dormant
package-private entry should establish that ownership from the fixed existing private
file using CatalogProcessGate, then delegate to OwnedCatalogWriter without reimplementing
its transaction/SQL/state machine. No UPSERT, new row, schema/version/hash change,
package/source mutation, account/session operation or provider call.

The immutable request binds exact Snapshot generation/revision, CatalogKey and raw
StyleValue. A required future broker must receive these same objects before any
framework directory resolution, canonical/stat/sidecar observation or SQLite open,
and again through both staging checks and atCommit. Constructor validation/ownership
reservation alone never grants access. No default/allow-all broker implementation.
Cancellation and a bounded observed1..5000ms monotonic read/open budget are checked
before opening; do not silently add a deadline that overrides the reviewed transaction
COMMIT_ENTERED/INDETERMINATE semantics. Once commit entry occurs, late cancellation or
expiry requires committed/readback versus INDETERMINATE, never unconditional rollback.

## Existing-only path and handle acquisition
Use the same credential-protected application noBackup fixed manager-catalog-v1.sqlite
path, unlocked-user/applicationContext checks, canonical final-parent identity,
UID/GID/mode policy, existing regular nonempty leaf, no leaf symlink, and ENOENT-only
absence distinction as the current read opener. Reject journal/WAL/SHM before open.
Acquire the sole process reservation before first protected observation. Wrong-thread,
missing boundary, unavailable context, absent source or busy reservation fail without
creating a catalog, changing permissions or taking over another profile/database.

Factor the independently authored current path observations into a package-private
read-only helper if needed; preserve all existing read predicates and keep the public
read-only entry unable to request writable flags. No arbitrary caller path or broad
boolean-mode escape hatch. Before any writable open, the write entry must validate the existing source through
OPEN_READONLY with the same throwing non-deleting corruption handler, while retaining
the SAME acquired process token. Run the reviewed exact-format inspection under access/
cancel/path checks; reject unknown/malformed sources without proceeding to RW. Confirm
the read-only handle is closed, then recheck unchanged file/parent identities, no
sidecars, context and exact request access. RO close uncertainty quarantines and stops.
Do not call the public read opener then release/reacquire: that breaks the ownership
handoff. Any internal helper accepts only the already-owned private token and cannot
change mode on a returned read lease or return a raw handle to callers.

Only after that RO preflight and confirmed close does the new write entry use
OPEN_READWRITE with
NO_LOCALIZED_COLLATORS, without CREATE_IF_NECESSARY/openOrCreate/helper/bootstrap.
A throwing non-deleting corruption handler remains mandatory. API open side effects
and native recovery behavior are Android evidence gates, not claimed solved by flags.

Recheck identities/context/access/cancel before transferring control to the writer.
Repeated exact format validation inside the writer transaction before staging remains
mandatory even after RO preflight. The process token prevents participating app openers
from changing the source between phases; it is not proof against external same-UID or
other-process mutation. Public SQLite open-by-path cannot make RO-close/RW-open an atomic
file-descriptor handoff. Do not claim such a guarantee or RW native recovery harmless;
actual Android/source-race and all-writer gates remain required before activation.
Require open non-read-only handle and no unexpected transaction, while remembering
inTransaction is current-thread state rather than an ownership proof. The singleton
reservation and no handle escape establish this slice's process ownership precondition;
other processes or malicious same-UID writers are NOT excluded by it.

## Delegation and result/cleanup precedence
Invoke OwnedCatalogWriter.apply with the exact request Snapshot/Key/Style and required
broker. Preserve its budget/strict schema/no attached or TEMP schemas, generation and
revision CAS, exact existing-row update and bounded readback rules. Any wrapper must
not transform raw badge or opaque keys, invent defaults or retry stale rows. No handle,
statement, Cursor or token escapes. The writer's commit boundary remains required.

The outer entry retains the process token until native DB close is confirmed. If the
writer returns INDETERMINATE, native open has unknown outcome, resource close fails,
or handle state cannot be reconciled, quarantine the exact owner token before any
release; do not force-unlock/reopen/replay. Return only generic typed outcomes with no
private path/key/payload/cause. Missing existing source is not a successful empty write.

Do not erase known commit facts: if writer reports COMMITTED with verified committed
revision but outer close is uncertain, return an explicit COMMITTED_RESOURCE_UNCERTAIN
outcome retaining that revision (not clean success, not rolled back). If commit is
unknown, return INDETERMINATE without fabricating a revision. Only confirmed closed
resources permit normal COMMITTED/ROLLED_BACK/rejection statuses; quarantine always
prevents another acquisition. No later cancellation check may overwrite known commit
or resource uncertainty. Future UI must reconcile before showing/allowing more actions.

## Evidence and activation limits
Review source/static tests for exact before-observation request binding, no CREATE or
permission changes, same path predicates, singleton ownership, writer delegation,
status precedence and generic failures. Reuse unchanged SQL/pure-core evidence; run
only affected source/receipt tests plus the genuinely required new compile aggregate.
No local JDK17/Android runtime is assumed. API docs and actual min26 compilation are
necessary but insufficient for lifecycle/native close/SQLite recovery behavior.

Owned Android tests must later prove absent/malformed/unknown sources retained,
exact key/raw badge updates, stale and ABA rejection, cancellation before/at/after
commit, committed-revision retention under close uncertainty, exclusive read/write
contention, locked-user denial and real auth/lifecycle revocation. All writers still
need adoption and the actual broker before activation. AndroidX artifact inspection
and local Git fetch remain separately held after denial; this draft authorizes no
retry, alternate import, dependency download, device operation or user-data mutation.
