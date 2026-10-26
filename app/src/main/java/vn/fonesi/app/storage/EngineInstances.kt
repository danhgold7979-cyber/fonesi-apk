package vn.fonesi.app.storage

import androidx.room.*
import vn.fonesi.core.*
import java.util.UUID

@Entity(tableName = "engine_instances", indices = [Index(value = ["cloneId"], unique = true), Index(value = ["engineKey"])])
data class EngineInstanceRow(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val cloneId: String,
    val engineKey: String,
    val packageName: String,
    val state: String,
    val launchPending: Boolean,
)

@Dao interface EngineInstanceDao {
    @Insert fun insert(row: EngineInstanceRow): Long
    @Query("SELECT * FROM engine_instances WHERE engineKey = :key ORDER BY sequence") fun list(key: String): List<EngineInstanceRow>
    @Query("UPDATE engine_instances SET state = :state WHERE engineKey = :key AND cloneId = :id") fun state(key: String, id: String, state: String): Int
    @Query("UPDATE engine_instances SET launchPending = :pending WHERE engineKey = :key AND cloneId = :id") fun pending(key: String, id: String, pending: Boolean): Int
}

class RoomInstanceRegistry(private val db: FonesiDatabase, private val engineKey: String) : InstanceRegistry {
    override fun reserve(packageName: String): GuestInstance {
        val row = EngineInstanceRow(cloneId = UUID.randomUUID().toString(), engineKey = engineKey,
            packageName = packageName, state = InstanceState.RESERVED.name, launchPending = false)
        val sequence = db.instances().insert(row)
        return convert(row.copy(sequence = sequence))
    }
    override fun list() = db.instances().list(engineKey).map(::convert)
    override fun state(id: String, state: InstanceState) { check(db.instances().state(engineKey, id, state.name) == 1) }
    override fun pending(id: String, pending: Boolean) { check(db.instances().pending(engineKey, id, pending) == 1) }
    private fun convert(row: EngineInstanceRow): GuestInstance {
        check(row.sequence in 1..(Int.MAX_VALUE - 10_000).toLong()) { "SDK user ID space exhausted" }
        return GuestInstance(row.cloneId, (row.sequence + 10_000).toInt(), row.packageName,
            InstanceState.valueOf(row.state), row.launchPending)
    }
}
