package vn.fonesi.app.storage

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "clone_metadata", primaryKeys = ["engineKey", "cloneId"],
    indices = [Index(value = ["launchToken"], unique = true)])
data class CloneMetadataRow(
    val engineKey: String,
    val cloneId: String,
    val packageName: String,
    val label: String,
    val groupName: String?,
    val createdAt: Long,
    val changedAt: Long,
    @ColumnInfo(defaultValue = "''") val launchToken: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "'teal'") val accentKey: String = "teal",
    val badgeText: String? = null,
)

@Entity(tableName = "update_tasks", indices = [
    Index(value = ["taskId"], unique = true), Index(value = ["activeKey"], unique = true),
    Index(value = ["packageName", "state"]),
])
data class UpdateTaskRow(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val taskId: String,
    val packageName: String,
    val state: String,
    val revision: Long,
    val activeKey: String?,
    val payload: String,
)

@Entity(tableName = "queue_control")
data class QueueControlRow(@PrimaryKey val id: Int = 1, val paused: Boolean)

@Dao
interface CloneMetadataDao {
    @Query("SELECT * FROM clone_metadata ORDER BY createdAt, cloneId") fun observe(): Flow<List<CloneMetadataRow>>
    @Query("SELECT * FROM clone_metadata ORDER BY createdAt, cloneId") fun list(): List<CloneMetadataRow>
    @Query("SELECT * FROM clone_metadata WHERE engineKey = :engine AND cloneId = :id")
    fun find(engine: String, id: String): CloneMetadataRow?
    @Query("SELECT * FROM clone_metadata WHERE launchToken = :token")
    fun findByLaunchToken(token: String): CloneMetadataRow?
    @Insert fun insert(row: CloneMetadataRow)
    @Query("UPDATE clone_metadata SET label = :label, groupName = :groupName, changedAt = :now WHERE engineKey = :engine AND cloneId = :id")
    fun rename(engine: String, id: String, label: String, groupName: String?, now: Long): Int
    @Query("UPDATE clone_metadata SET accentKey = :accent, badgeText = :badge, changedAt = :now WHERE engineKey = :engine AND cloneId = :id")
    fun restyle(engine: String, id: String, accent: String, badge: String?, now: Long): Int
    @Query("DELETE FROM clone_metadata WHERE engineKey = :engine AND cloneId = :id") fun delete(engine: String, id: String): Int
}

@Dao
interface UpdateTaskDao {
    @Query("SELECT * FROM update_tasks ORDER BY sequence") fun list(): List<UpdateTaskRow>
    @Query("SELECT * FROM update_tasks ORDER BY sequence") fun observe(): Flow<List<UpdateTaskRow>>
    @Query("SELECT * FROM update_tasks WHERE activeKey = :key") fun findActive(key: String): UpdateTaskRow?
    @Query("SELECT * FROM update_tasks WHERE taskId = :id") fun find(id: String): UpdateTaskRow?
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insert(row: UpdateTaskRow): Long
    @Query("UPDATE update_tasks SET state=:state, revision=:revision, activeKey=:activeKey, payload=:payload WHERE taskId=:id AND revision=:expectedRevision AND state=:expectedState")
    fun replace(id: String, expectedRevision: Long, expectedState: String, state: String, revision: Long, activeKey: String?, payload: String): Int
    @Query("SELECT paused FROM queue_control WHERE id = 1") fun paused(): Boolean?
    @Query("SELECT paused FROM queue_control WHERE id = 1") fun observePaused(): Flow<Boolean?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun setControl(row: QueueControlRow)
}

@Database(entities = [CloneMetadataRow::class, UpdateTaskRow::class, QueueControlRow::class, EngineInstanceRow::class], version = 4, exportSchema = true)
abstract class FonesiDatabase : RoomDatabase() {
    abstract fun clones(): CloneMetadataDao
    abstract fun tasks(): UpdateTaskDao
    abstract fun instances(): EngineInstanceDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clone_metadata ADD COLUMN accentKey TEXT NOT NULL DEFAULT 'teal'")
                db.execSQL("ALTER TABLE clone_metadata ADD COLUMN badgeText TEXT DEFAULT NULL")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS engine_instances (sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, cloneId TEXT NOT NULL, engineKey TEXT NOT NULL, packageName TEXT NOT NULL, state TEXT NOT NULL, launchPending INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX index_engine_instances_cloneId ON engine_instances (cloneId)")
                db.execSQL("CREATE INDEX index_engine_instances_engineKey ON engine_instances (engineKey)")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clone_metadata ADD COLUMN launchToken TEXT NOT NULL DEFAULT ''")
                val identities = db.query("SELECT engineKey, cloneId FROM clone_metadata").use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) }
                }
                identities.forEach { (engine, id) ->
                    db.execSQL("UPDATE clone_metadata SET launchToken = ? WHERE engineKey = ? AND cloneId = ?",
                        arrayOf(UUID.randomUUID().toString(), engine, id))
                }
                db.execSQL("CREATE UNIQUE INDEX index_clone_metadata_launchToken ON clone_metadata (launchToken)")
            }
        }

        fun open(context: Context, name: String = "fonesi.db"): FonesiDatabase =
            Room.databaseBuilder(context.applicationContext, FonesiDatabase::class.java, name)
                .openHelperFactory(PreservingOpenHelperFactory())
                .setJournalMode(JournalMode.TRUNCATE)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL("PRAGMA synchronous=FULL")
                    }
                })
                // No destructive migration fallback: unknown schemas must not erase metadata/history.
                .build()
    }
}
