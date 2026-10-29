package vn.fonesi.app.storage

import vn.fonesi.core.CloneEngine
import vn.fonesi.core.EngineAvailability
import vn.fonesi.core.InstalledSource
import vn.fonesi.core.update.QueueLock
import vn.fonesi.core.update.TaskState

/** Metadata only. Never invents clones when an engine is unavailable. Call on an IO dispatcher. */
class CloneMetadataRepository(
    private val db: FonesiDatabase,
    private val engineKey: String,
    private val engine: CloneEngine,
    private val operationLock: QueueLock,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observe() = db.clones().observe()

    fun create(source: InstalledSource, label: String, group: String? = null): CloneMetadataRow = mutateEngine(source.packageName) {
        require(engine.availability is EngineAvailability.Ready) { "Engine chưa sẵn sàng" }
        val normalized = normalize(label, group)
        val before = engine.listClones().map { it.id }.toSet()
        val created = engine.create(source, normalized.first)
        check(created.id !in before) { "Engine reused an existing clone ID" }
        val observed = engine.listClones().singleOrNull { it.id == created.id }
        check(observed != null && observed.packageName == source.packageName &&
            observed.versionCode == source.versionCode && observed.signerDigests == source.signerDigests) {
            "Created clone has not been verified by readback"
        }
        val now = clock()
        val row = CloneMetadataRow(engineKey, created.id, source.packageName, normalized.first, normalized.second, now, now)
        db.clones().insert(row)
        row
    }

    fun rename(id: String, label: String, group: String?) {
        val normalized = normalize(label, group)
        check(db.clones().rename(engineKey, id, normalized.first, normalized.second, clock()) == 1) {
            "Saved clone metadata does not exist"
        }
    }

    /** Offline-safe presentation setting; does not call the SDK or mutate guest account data. */
    fun restyle(id: String, accentKey: String, badgeText: String?) {
        val normalized = CloneStylePolicy.normalize(accentKey, badgeText)
        check(db.clones().restyle(engineKey, id, normalized.accentKey, normalized.badgeText, clock()) == 1) {
            "Saved clone metadata does not exist"
        }
    }

    fun delete(id: String, expectedToken: String? = null) {
        val saved = checkNotNull(db.clones().find(engineKey, id))
        mutateEngine(saved.packageName) {
            require(engine.availability is EngineAvailability.Ready) { "Engine chưa sẵn sàng" }
            check(db.clones().find(engineKey, id)?.launchToken == saved.launchToken) { "Clone metadata changed" }
            check(expectedToken == null || saved.launchToken == expectedToken) { "Stale delete request" }
            // Fail closed before any irreversible provider mutation if owner state is corrupt.
            val ownership = CloneClusterOwnershipRepository(db, clock)
            ownership.preflightDeletion(engineKey, id, saved.launchToken)
            val records = engine.listClones()
            check(records.map { it.id }.distinct().size == records.size) { "Ambiguous engine IDs" }
            val live = records.singleOrNull { it.id == id }
            if (live != null) {
                check(live.packageName == saved.packageName && live.running == false) { "Clone is busy, unknown or has changed identity" }
                engine.delete(id)
            }
            check(engine.listClones().none { it.id == id }) { "Engine still reports the clone; metadata preserved" }
            // Release a CLONE reference only after provider-confirmed absence, atomically with metadata.
            ownership.deleteMetadataAfterProviderConfirmed(engineKey, id, saved.launchToken)
        }
    }

    /** Recover real SDK instances after an interrupted create/metadata write; never invent guests. */
    fun importDiscovered(): Int {
        val lease = checkNotNull(operationLock.acquire())
        return lease.use {
            require(engine.availability is EngineAvailability.Ready)
            val live = engine.listClones()
            check(live.map { it.id }.distinct().size == live.size)
            var added = 0
            for (clone in live) if (db.clones().find(engineKey, clone.id) == null) {
                check(clone.signerDigests.isNotEmpty() && clone.versionCode >= 0)
                val now = clock()
                db.clones().insert(CloneMetadataRow(engineKey, clone.id, clone.packageName,
                    "Bản khôi phục ${++added}", null, now, now))
            }
            added
        }
    }

    private fun <T> mutateEngine(packageName: String, action: () -> T): T {
        val lease = checkNotNull(operationLock.acquire()) { "Another engine operation is in progress" }
        return lease.use {
            check(RoomUpdateTaskStore(db).list().none { it.packageName == packageName && it.state in setOf(
                TaskState.PREPARING, TaskState.APPLYING, TaskState.VERIFYING, TaskState.REVIEW_REQUIRED,
            ) }) { "An update still needs reconciliation" }
            action()
        }
    }

    private fun normalize(label: String, group: String?): Pair<String, String?> {
        val name = label.trim()
        val grouping = group?.trim()?.takeIf { it.isNotEmpty() }
        require(name.length in 1..80 && (grouping == null || grouping.length <= 40))
        return name to grouping
    }
}
