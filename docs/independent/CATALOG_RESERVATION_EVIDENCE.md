# Shared reservation — local source / limited HOST evidence

CatalogReservation is a real CAS ownership primitive with private opaque owner-thread
tokens and ACTIVE/QUARANTINED/RELEASED transitions. CatalogProcessGate owns the sole
production instance. ExistingCatalogRead now acquires that token before observations,
retains it through the lease, releases only after confirmed cleanup and quarantines
uncertain resources. No force reset, new file opener, auth grant or UI caller exists.
The dormant raw writer still requires a correctly exclusive caller-owned handle;
future writer/bootstrap/restore entry points must adopt the singleton before anyone
claims all-writer participation. This slice does not establish that claim.

65 owned concurrency/lifecycle assertions PASS locally in LIMITED_HOST mode using
JDK21 module compiler source17/target17. Two-thread races use latches and bounded joins,
not sleeps; the winning acquiring thread releases its own token. Tests cover stale
release not clearing a new owner, wrong-thread operations, sticky quarantine, actual
one-winner CAS competition and a dead owner remaining busy. No Android or SQLite
execution is represented by these assertions, and no new JDK17 result is pre-filled.

Affected source/integration checks PASS: five lease, thirteen shell, three reservation
integration and six baseline negatives (27 cases). The shell has one explicit new
package-private Java allowlist entry; MainActivity/manifest/assets are unchanged.
Mandatory HOST runs the reservation script exactly once, rejects missing/zero/duplicate,
limited or wrong-tier evidence, and records its actual positive count. New packaged
core/app inputs force a fresh compile. Expected future total excluding the blocked
comparison draft is141Python cases plus83/128/151/60/269/65synthetic assertions.

Actual Android lease/file cleanup, process lifecycle, live auth/lifecycle broker,
all writers and device tests remain NOT_RUN. No real user database was touched.
Pending local Git-fetch and AndroidX dependency-download denials were not retried
or replaced by another import/download route. This milestone needs independent source
review and genuine new HOST/COMPILE; compile cannot prove runtime ownership.
