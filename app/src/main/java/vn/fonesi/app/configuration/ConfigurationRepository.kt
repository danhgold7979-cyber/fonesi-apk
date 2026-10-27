package vn.fonesi.app.configuration

import vn.fonesi.app.storage.FonesiDatabase
import vn.fonesi.core.CloneEngine
import vn.fonesi.core.EngineAvailability
import vn.fonesi.core.update.QueueLock

/** Metadata-only import. Every target is explicitly selected and verified; never creates guests. */
class ConfigurationRepository(
    private val db: FonesiDatabase,
    private val engineKey: String,
    private val engine: CloneEngine,
    private val lock: QueueLock,
    private val authorized: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun export(): ByteArray {
        check(authorized())
        val rows = db.clones().list()
        val archive = ConfigurationArchive(rows.mapIndexed { index, row ->
            ConfigurationEntry(index + 1, row.packageName, row.label, row.groupName)
        })
        return ConfigurationCodec.encode(archive).also { check(authorized()) }
    }
    fun apply(archive: ConfigurationArchive, mappings: Map<Int, String>): Int {
        ConfigurationCodec.validate(archive)
        val selected = mappings.toMap()
        require(selected.isNotEmpty() && selected.values.distinct().size == selected.size)
        val entries = archive.entries.associateBy { it.reference }
        require(entries.keys.containsAll(selected.keys))
        check(authorized())
        val lease = checkNotNull(lock.acquire()) { "Another engine operation is in progress" }
        return lease.use {
            check(authorized() && engine.availability is EngineAvailability.Ready)
            val live = engine.listClones()
            val byId = live.associateBy { it.id }
            check(byId.size == live.size)
            val targets = selected.map { (reference, token) ->
                val row = checkNotNull(db.clones().findByLaunchToken(token))
                val entry = entries.getValue(reference)
                val current = byId[row.cloneId]
                check(row.engineKey == engineKey && row.packageName == entry.packageName && current != null &&
                    current.packageName == entry.packageName && current.versionCode >= 0 && current.signerDigests.isNotEmpty())
                row to entry
            }
            check(authorized())
            db.runInTransaction {
                check(authorized())
                for ((row, entry) in targets) {
                    check(db.clones().findByLaunchToken(row.launchToken)?.cloneId == row.cloneId)
                    check(db.clones().rename(engineKey, row.cloneId, entry.label, entry.group, clock()) == 1)
                }
                check(authorized())
            }
            targets.size
        }
    }
}
