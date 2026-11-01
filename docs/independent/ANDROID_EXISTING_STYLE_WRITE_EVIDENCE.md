# Existing style entry — local source evidence only

ExistingStyleWrite is dormant and package-private: no caller, broker implementation,
bootstrap, legacy migration, new permissions or real user-data write. It reserves the
same CatalogProcessGate used by the read opener before observations and binds the
exact immutable snapshot/key/raw-style objects to the required broker. It performs
existing-only read-only exact-format preflight, confirms RO close, retains the same
token, rechecks source/access, then opens existing-only RW and delegates to the already
reviewed OwnedCatalogWriter transaction/conditional update/readback state machine.

CatalogPath extracts the current first-party path/ownership observations for both
entries; no arbitrary path or mutable-mode read lease is introduced. Host tests compare
exact observer/verify method hashes with reviewed predecessor b480607, alongside all
sidecar/ENOENT/role/identity predicates. The existing read entry remains OPEN_READONLY.
The existing writer SQL, core value/key/consumer/reservation classes and eight packaged
assets are unchanged and their unrelated suites were not rerun merely for status.

Opening1..5000ms observed deadline ends at writer handoff; no late deadline or cancel
check can erase known commit facts. Unknown native-open/close outcomes quarantine.
Known committed revision is retained as COMMITTED_RESOURCE_UNCERTAIN if later cleanup
is uncertain. Result also retains the known writerOutcome (including confirmed rollback)
without pretending resource uncertainty reverses a commit or invents an unknown commit.
No clean success or token release after uncertainty. No native recovery harmlessness,
atomic ROclose/RWopen descriptor handoff or hostile same-UID protection is claimed.

42 affected local cases PASS:8new style-entry source/retention checks,5read-entry,
13shell,7writer/asset,6page-adapter and3reservation integration cases. These are source
or owned native-SQL predicates, NOT executing Android APIs or database writes. The
mandatory new suite yields an expected future149Python cases excluding blocked comparison
draft, plus unchanged83/128/151/60/269/65synthetic assertions. This is not an actual
receipt until independently reviewed source receives fresh hosted verification.

Actual Android file retention/recovery, lock/close/cancel/commit races, live auth and
lifecycle broker, all-writer participation and device proof remain NOT_RUN. User DBs
were not opened. Pending denied fetch/AndroidX artifact operations were not retried or
substituted. Three comparison-draft files stay excluded from this source milestone.
