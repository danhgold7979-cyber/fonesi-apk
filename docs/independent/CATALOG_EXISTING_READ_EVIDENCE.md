# Existing-file read entry — source only, no Android observation

New independently authored package-private ExistingCatalogRead implements a dormant
fixed-path READONLY lease. It has no runtime caller or AccessBoundary implementation.
No manager/guest DB is opened in this workspace. No bootstrap, create, repair,
permission change, writable transition or UI/data publication is introduced.

First access/cancel check and app-owned nonblocking reservation precede framework
path resolution. Parent/leaf role/UID/GID/write-mode and dev/inode mappings are
checked around open; only ENOENT means a sidecar is absent. Native open failure
without a returned handle and close failure quarantine the process reservation;
no unobserved cleanup success or blind retry. Returned handle is unvalidated and
read-only. Actual source schema, bounded query consistency, broker association,
Android lifecycle/path/corruption behavior and all-writer integration remain open.

Focused local checks: four static read-entry source predicates, thirteen shell
contracts, six HOST routing/count fixtures and six baseline negative fixtures PASS.
A first shell exact-file allowlist failed on alphabetical expected order after the
new source addition; corrected only that ordered list and reran the failing method.
A final close-uncertainty guard was checked with its one affected source predicate.
These are SOURCE/synthetic fixtures, not executed Android open/close/corruption tests.
The separate ownership helper has sixty LIMITED_HOST assertions, not new JDK17 proof.

Actual new AGP/D8/lint/manifest/signature/source-asset and JDK17 evidence is still
PENDING for these additions. c05ae6b6 remains evidence only for the prior source.
No accepted task counters or higher-tier statuses change. All original assertions,
inactive architecture proposal and unavailable provider state are preserved.

Independent source review found accessor exceptions could leak platform/broker
messages and its private failure type was unusable by callers. Corrected to generic
package-visible typed failure, preserving only status, and explicit owner-finally
close responsibility after accessor invalidation. Only the affected source predicate
was rerun; this is not actual Android exception/corruption behavior evidence.
