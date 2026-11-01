# Shared process reservation — proposed original-scope prerequisite

The current read opener's private AtomicBoolean cannot establish participation by
future write/bootstrap/restore openers. This proposal extracts a small real process
ownership primitive; it is NOT an authentication token, file ownership proof,
SQLite transaction, cross-process lock or product/task acceptance. It creates no DB,
opens no file and grants no authority to read/write/launch. V2 stays inactive.

## Core contract and deployment
A first-party Java CatalogReservation instance has one AtomicReference to an opaque
private-constructed token or null. A pure primitive may be instantiated for isolated
owned tests, but production must use exactly one package-private CatalogProcessGate
static final instance for all manager-catalog paths. No per-call lock creation, public
setter/reset or force-release API. Future writer/bootstrap/restore adapters must
adopt that same instance before claiming all-writer coordination. The current raw
writer's explicit caller-exclusive-handle precondition remains until an opener exists.
Do not report this primitive as already proving every writer participates.

tryAcquire is nonblocking: return an opaque owner-thread token only after CAS from
null to that exact new token; otherwise BUSY with no token and no change. Same-thread
reentrant acquire is BUSY too, never recursive ownership. Acquisition alone does not
perform access/cancellation/deadline checks; callers must still run the real broker
before protected observations, as ExistingCatalogRead already requires.

Token methods require Thread.currentThread equal the immutable acquiring Thread.
Wrong-thread inspect/release/quarantine throws only a generic typed failure and does
not alter legitimate ownership. Active validation requires identity equality with the
current atomic token, not an epoch integer that can wrap or a public boolean.
No public token constructor, token cloning, equality override or deserialization.

Confirmed release is allowed only for the current active owner token and CASes that
same token to null. A second owner-thread release of the already RELEASED token is
idempotent and MUST NOT inspect/clear a newer token. A stale token can never release
or quarantine a new owner; state and identity both matter. Unknown CAS mismatch is
an invariant failure, never force-clears the current reference. No automatic retries.

Quarantine is owner-only, active-token-only and must occur BEFORE confirmed release.
It marks sticky resource uncertainty and retains ownership forever for that process
instance. Later release/close reports RESOURCE_UNCERTAIN without making acquisition
available. Calls after RELEASED reject without touching any newer owner. There is no
unquarantine API. A dead worker remains held/busy; no timer guesses cleanup succeeded.
Process restart clears in-memory locks but is not evidence that persisted state is
safe or an uncertain commit was rolled back; normal reconciliation still applies.

## Bounded integration proposal
Replace ExistingCatalogRead's private boolean with the singleton and retain its same
acquire-before-observation ordering. Its successful close releases the exact token;
open failure confirmed clean releases; native-open uncertainty or Cursor/DB close
uncertainty quarantines. No returned raw ownership token to untrusted callers.
The lease still owns the token until close; an external Cursor quarantine cannot
poison a released/replaced token. Preserve cancellation/path/access checks verbatim.
No writer opener, bootstrap, auth broker or UI activation is introduced in this slice.

## Positive / negative / race evidence
Owned HOST tests: first acquire, busy/reentrant acquire, confirmed release/reacquire,
old-token repeated release with new owner still held, old-token quarantine rejection,
wrong-thread operations preserve owner, quarantine blocks all future acquisitions,
foreign thread races acquire (exactly one winner), release-then-race reacquisition,
and dead owner remains busy rather than unsafe recovery. Tests must release only on
the owning test thread; no fake grant, sleeps as synchronization, or database claims.
Use bounded latches/joins with explicit failure if the harness cannot complete.

Source integration tests require exact singleton use/no reset/force-clear, unchanged
before-observation access ordering and cleanup uncertainty precedence. Package-private
app integration and packaged core changes require a fresh exact-source compile after
independent review. Neither HOST concurrency nor compile proves Android/process
lifecycle, all-writer adoption, actual filesystem handles, or authentication.

## Exact token transition rules
The token states are ACTIVE, QUARANTINED and RELEASED. Nonowner rejection precedes
all state/reference mutation. ACTIVE validation checks exact current reference;
QUARANTINED validation throws RESOURCE_UNCERTAIN; RELEASED validation throws STATE.
ACTIVE->RELEASED occurs only after successful exact-token CAS to null. A mismatched
CAS must not mark RELEASED or clear/replace any current reference. ACTIVE->QUARANTINED
retains the exact current token. Repeated owner quarantine is idempotent while that
same token remains current; it never releases it. Quarantine after RELEASED rejects
without inspecting or touching the next owner's reference. Release after QUARANTINED
always reports RESOURCE_UNCERTAIN; repeated owner release after RELEASED is idempotent
without reading the current reference. No other transitions exist.
