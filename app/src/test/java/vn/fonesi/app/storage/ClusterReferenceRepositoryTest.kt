package vn.fonesi.app.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import vn.fonesi.apk.ClusterOwnerType
import vn.fonesi.apk.ClusterReference
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ClusterReferenceRepositoryTest {
    @Test fun referencesAreIdempotentOwnerScopedDurableAndReconciledWithoutDeletion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "cluster-refs-${UUID.randomUUID()}.db"
        val db = withContext(Dispatchers.IO) { FonesiDatabase.open(context, name) }
        try {
            val repository = ClusterReferenceRepository(db) { 500L }
            val cloneRef = ClusterReference(UUID.randomUUID().toString(), ClusterOwnerType.CLONE, "clone-1", "engine-a", 100L)
            val reviewRef = cloneRef.copy(ownerType = ClusterOwnerType.REVIEW_REQUIRED, ownerId = "task-1", createdAtEpochMs = 200L)
            assertTrue(withContext(Dispatchers.IO) { repository.acquire(cloneRef) })
            assertFalse(withContext(Dispatchers.IO) { repository.acquire(cloneRef.copy(createdAtEpochMs = 999L)) })
            assertTrue(withContext(Dispatchers.IO) { repository.acquire(reviewRef) })
            assertEquals(setOf(cloneRef), withContext(Dispatchers.IO) {
                repository.referencesForOwner(ClusterOwnerType.CLONE, "clone-1", "engine-a")
            })
            assertEquals(setOf(cloneRef, reviewRef), withContext(Dispatchers.IO) { repository.all() })
            val report = withContext(Dispatchers.IO) { repository.reconcile(setOf(cloneRef.clusterId)) }
            assertEquals(setOf(reviewRef.clusterId), report.referencedButMissing)
            assertTrue(report.publishedWithoutReferences.isEmpty())
            assertTrue(withContext(Dispatchers.IO) { repository.release(reviewRef) })
            assertFalse(withContext(Dispatchers.IO) { repository.release(reviewRef) })
            withContext(Dispatchers.IO) { db.close() }
            val reopened = withContext(Dispatchers.IO) { FonesiDatabase.open(context, name) }
            try {
                assertEquals(setOf(cloneRef), withContext(Dispatchers.IO) { ClusterReferenceRepository(reopened).all() })
            } finally {
                withContext(Dispatchers.IO) { reopened.close() }
            }
        } finally {
            runCatching { withContext(Dispatchers.IO) { db.close() } }
            context.deleteDatabase(name)
        }
    }

    @Test fun migration4To5PreservesExistingRowsAndCreatesEmptyReferenceTable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-4-5-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val raw = withContext(Dispatchers.IO) { SQLiteDatabase.openOrCreateDatabase(file, null) }
        try {
            raw.execSQL("CREATE TABLE IF NOT EXISTS clone_metadata (engineKey TEXT NOT NULL, cloneId TEXT NOT NULL, packageName TEXT NOT NULL, label TEXT NOT NULL, groupName TEXT, createdAt INTEGER NOT NULL, changedAt INTEGER NOT NULL, launchToken TEXT NOT NULL DEFAULT '', accentKey TEXT NOT NULL DEFAULT 'teal', badgeText TEXT, PRIMARY KEY(engineKey, cloneId))")
            raw.execSQL("CREATE UNIQUE INDEX index_clone_metadata_launchToken ON clone_metadata (launchToken)")
            raw.execSQL("CREATE TABLE IF NOT EXISTS update_tasks (sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskId TEXT NOT NULL, packageName TEXT NOT NULL, state TEXT NOT NULL, revision INTEGER NOT NULL, activeKey TEXT, payload TEXT NOT NULL)")
            raw.execSQL("CREATE UNIQUE INDEX index_update_tasks_taskId ON update_tasks (taskId)")
            raw.execSQL("CREATE UNIQUE INDEX index_update_tasks_activeKey ON update_tasks (activeKey)")
            raw.execSQL("CREATE INDEX index_update_tasks_packageName_state ON update_tasks (packageName, state)")
            raw.execSQL("CREATE TABLE IF NOT EXISTS queue_control (id INTEGER NOT NULL, paused INTEGER NOT NULL, PRIMARY KEY(id))")
            raw.execSQL("CREATE TABLE IF NOT EXISTS engine_instances (sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, cloneId TEXT NOT NULL, engineKey TEXT NOT NULL, packageName TEXT NOT NULL, state TEXT NOT NULL, launchPending INTEGER NOT NULL)")
            raw.execSQL("CREATE UNIQUE INDEX index_engine_instances_cloneId ON engine_instances (cloneId)")
            raw.execSQL("CREATE INDEX index_engine_instances_engineKey ON engine_instances (engineKey)")
            raw.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            raw.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, 'bab5e9209b974b568780f922ff01622d')")
            raw.execSQL("INSERT INTO clone_metadata (engineKey, cloneId, packageName, label, groupName, createdAt, changedAt, launchToken, accentKey, badgeText) VALUES ('engine-a', 'clone-1', 'vn.test.app', 'Test clone', NULL, 1, 1, 'token-1', 'teal', NULL)")
            raw.execSQL("INSERT INTO update_tasks (sequence, taskId, packageName, state, revision, activeKey, payload) VALUES (1, 'task-1', 'vn.test.app', 'QUEUED', 0, NULL, '{}')")
            raw.execSQL("INSERT INTO queue_control (id, paused) VALUES (1, 1)")
            raw.execSQL("INSERT INTO engine_instances (sequence, cloneId, engineKey, packageName, state, launchPending) VALUES (1, 'clone-1', 'engine-a', 'vn.test.app', 'READY', 0)")
            raw.execSQL("PRAGMA user_version = 4")
        } finally {
            withContext(Dispatchers.IO) { raw.close() }
        }

        val migrated = withContext(Dispatchers.IO) { FonesiDatabase.open(context, name) }
        try {
            assertEquals(1, withContext(Dispatchers.IO) { migrated.clones().list().size })
            assertEquals(1, withContext(Dispatchers.IO) { migrated.tasks().list().size })
            assertEquals(1, withContext(Dispatchers.IO) { migrated.instances().list().size })
            assertTrue(withContext(Dispatchers.IO) { migrated.clusterReferences().list().isEmpty() })
            val version = withContext(Dispatchers.IO) {
                migrated.openHelper.readableDatabase.query("PRAGMA user_version").use { cursor ->
                    assertTrue(cursor.moveToFirst()); cursor.getInt(0)
                }
            }
            assertEquals(5, version)
        } finally {
            withContext(Dispatchers.IO) { migrated.close() }
            context.deleteDatabase(name)
        }
    }
}
