# Independent rebuild kernel

Fresh Java21 capability-policy and operation-transition implementation; not copied from the old engine. No Android, native SDK, network, accounts or production provider. Existing application does not consume this module yet.

Run `./rebuild-kernel/check.sh` from repository root. It uses the installed JDK compiler module and real JVM; no downloads. Standalone Gradle build is provided for future CI (`gradle -p rebuild-kernel check`), not executed in this environment.

Evidence records are policy input, **not attestations**: a future verifier must authenticate and inspect receipts. Capability approval requires exact provider/environment/scope; synthetic evidence cannot grant third-party claims. Separate UID alone never substitutes for hostile-guest testing.

Operation records are pure transition values, **not durable storage or locks**. A persistence adapter must enforce compare-and-swap and fsync/transaction commit of DISPATCHED before SDK entry. Restart after dispatch enters REVIEW_REQUIRED; no replay method exists. A later explicit reconciliation protocol is needed; this module intentionally cannot fake rollback/retry or delete guest data.

Local pass proves only JVM contracts. Java21 host compilation does not prove Android SDK/desugaring compatibility; that integration gate remains open.

Evidence applicability is exact cluster SHA-256, provider revision and environment, not universal app compatibility. Per-peer readback is bound to operation UUID and dispatch revision. Before dispatch the future adapter must independently check authorization, foreground state, READY/stopped peers and consent, and atomically compare-and-swap persisted revision; passing identity alone is not proof of any of those. Public records are value representations, not trusted state constructors.

For an uncertain journal publish (including after rename but before directory fsync), a visible reread is **not** a durability certificate. Do not invoke provider side effects. Read only to diagnose and durably move DISPATCHED to REVIEW_REQUIRED; stop if that cannot be committed. Lock special files are rejected before open. The private directory and its namespace must remain owned by trusted host code; this journal is not a hostile-filesystem sandbox.

Journal CAS is **not an execution lease** across different operation IDs. Before integration the trusted dispatcher must hold/reserve overlapping package and peer mutations through SDK entry/readback; perform live authorization/READY/stopped checks; and restrict verified-state writes to validated observations. Root and lock namespace replacement is prohibited for cooperating writers. Root file identity checks detect ordinary directory replacement but do not establish a hostile-filesystem security guarantee.

Current fresh verification: 64 kernel checks and 60 Linux journal checks. The filesystem suite creates only temporary synthetic metadata and test markers, exercises second-JVM contention and injected publication failures, and never calls a provider. Android/Gradle integration remains unverified.
