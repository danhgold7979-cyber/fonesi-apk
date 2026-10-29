package vn.fonesi.app.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import vn.fonesi.apk.ClusterOwnerType
import vn.fonesi.core.InstalledSource
import vn.fonesi.core.UpdateScope
import vn.fonesi.core.UpdateTarget
import vn.fonesi.core.update.*
import java.util.UUID

/**
 * Room/native-SQLite test-only owner rows, not signed APK execution or production guest proof.
 * E41 public acquisition separately requires cryptographic PrivateApkStore.open readback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RoomRetainedJournalTransitionTest {
    private lateinit var context: Context
    private lateinit var db: FonesiDatabase
    private lateinit var name: String
    private val engineKey = "engine-a"
    private val source = InstalledSource("test.social", 4L, setOf("a".repeat(64)), listOf("/fixture-only.apk"))

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        name = "e42-atomic-" + UUID.randomUUID() + ".db"
        db = FonesiDatabase.open(context, name)
    }

    @After fun tearDown() {
        io { db.close() }
        context.deleteDatabase(name)
    }

    private fun job(): UpdateTask = UpdateTask.create(
        engineKey, UpdateTarget(source, listOf("clone-1")), UpdateScope.PER_CLONE, 10L,
    ).also { io { assertEquals(it, RoomUpdateTaskStore(db).insertOrGetActive(it)) } }

    private fun owner(job: UpdateTask, type: ClusterOwnerType = ClusterOwnerType.UPDATE_TASK,
                      clusterId: String = UUID.randomUUID().toString(),
                      ownerId: String = job.id, engine: String = engineKey): String {
        io {
            assertTrue(db.clusterReferences().insert(
                ClusterReferenceRow(clusterId, type.name, ownerId, engine, 11L),
            ) > 0)
        }
        return clusterId
    }

    private fun owners(job: UpdateTask, type: ClusterOwnerType): List<String> = io {
        db.clusterReferences().forOwner(type.name, job.id, engineKey).map { it.clusterId }
    }

    private fun state(job: UpdateTask): UpdateTask = io {
        TaskCodec.decode(checkNotNull(db.tasks().find(job.id)))
    }

    private fun applying(job: UpdateTask): UpdateTask {
        val first = job.move(TaskState.PREPARING, 11L)
        assertTrue(io { RoomUpdateTaskStore(db).replace(job, first) })
        val next = first.move(TaskState.APPLYING, 12L,
            affectedIds = listOf("clone-1"), fingerprint = "f".repeat(64))
        assertTrue(io { RoomUpdateTaskStore(db).replace(first, next) })
        return next
    }

    @Test fun reviewJournalMarkerAndOwnerPromotionCommitTogetherAcrossReopen() {
        val task = job()
        val id = owner(task)
        owner(task, ClusterOwnerType.CLONE, id, ownerId = "clone-1")
        owner(task, ClusterOwnerType.ROLLBACK, id, ownerId = "rollback-1")
        val applying = applying(task)
        val review = applying.move(TaskState.REVIEW_REQUIRED, 13L, TaskIssue.INTERRUPTED)

        assertTrue(io { RoomUpdateTaskStore(db).replace(applying, review) })
        assertEquals(review, state(task))
        assertTrue(owners(task, ClusterOwnerType.UPDATE_TASK).isEmpty())
        assertEquals(listOf(id), owners(task, ClusterOwnerType.REVIEW_REQUIRED))
        assertEquals(setOf(ClusterOwnerType.CLONE.name, ClusterOwnerType.ROLLBACK.name,
            ClusterOwnerType.REVIEW_REQUIRED.name), io {
            db.clusterReferences().forCluster(id).map { it.ownerType }.toSet()
        })
        io { db.close(); db = FonesiDatabase.open(context, name) }
        assertEquals(review, state(task))
        assertEquals(listOf(id), owners(task, ClusterOwnerType.REVIEW_REQUIRED))
        assertFalse(io { RoomUpdateTaskStore(db).replace(applying, review) })
    }

    @Test fun terminalMarkerOnlyReleasesUpdateTaskOwnerAndPreservesOtherOwners() {
        val task = job()
        val id = owner(task)
        owner(task, ClusterOwnerType.REVIEW_REQUIRED, id)
        owner(task, ClusterOwnerType.CLONE, id, ownerId = "clone-1")
        owner(task, ClusterOwnerType.ROLLBACK, id, ownerId = "rollback-1")
        val cancelled = task.move(TaskState.CANCELLED, 21L)
        assertTrue(io { RoomUpdateTaskStore(db).replace(task, cancelled) })
        assertEquals(cancelled, state(task))
        assertTrue(owners(task, ClusterOwnerType.UPDATE_TASK).isEmpty())
        assertEquals(listOf(id), owners(task, ClusterOwnerType.REVIEW_REQUIRED))
        assertEquals(setOf(ClusterOwnerType.REVIEW_REQUIRED.name, ClusterOwnerType.CLONE.name,
            ClusterOwnerType.ROLLBACK.name), io {
            db.clusterReferences().forCluster(id).map { it.ownerType }.toSet()
        })
    }

    @Test fun legacyTaskWithNoVerifiedClusterRetainsOriginalJournalBehavior() {
        val task = job()
        val applying = applying(task)
        val review = applying.move(TaskState.REVIEW_REQUIRED, 13L, TaskIssue.INTERRUPTED)
        assertTrue(io { RoomUpdateTaskStore(db).replace(applying, review) })
        assertEquals(review, state(task))
        assertTrue(io { db.clusterReferences().list().isEmpty() })
    }

    @Test fun sqliteReviewInsertionFailureRollsBackBothJournalAndOwner() {
        val task = job()
        val id = owner(task)
        val applying = applying(task)
        val review = applying.move(TaskState.REVIEW_REQUIRED, 13L, TaskIssue.INTERRUPTED)
        io {
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER e42_fail_review BEFORE INSERT ON cluster_references " +
                    "WHEN NEW.ownerType = 'REVIEW_REQUIRED' BEGIN SELECT RAISE(ABORT, 'fail-closed'); END"
            )
        }
        try {
            assertThrows(Exception::class.java) {
                io { RoomUpdateTaskStore(db).replace(applying, review) }
            }
        } finally {
            io { db.openHelper.writableDatabase.execSQL("DROP TRIGGER e42_fail_review") }
        }
        assertEquals(applying, state(task))
        assertEquals(listOf(id), owners(task, ClusterOwnerType.UPDATE_TASK))
        assertTrue(owners(task, ClusterOwnerType.REVIEW_REQUIRED).isEmpty())
        assertTrue(io { RoomUpdateTaskStore(db).replace(applying, review) })
        assertEquals(review, state(task))
        assertEquals(listOf(id), owners(task, ClusterOwnerType.REVIEW_REQUIRED))
    }

    @Test fun sqliteTerminalReleaseFailureRollsBackBothJournalAndOwner() {
        val task = job()
        val id = owner(task)
        val cancelled = task.move(TaskState.CANCELLED, 21L)
        io {
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER e42_fail_release BEFORE DELETE ON cluster_references " +
                    "WHEN OLD.ownerType = 'UPDATE_TASK' BEGIN SELECT RAISE(ABORT, 'fail-closed'); END"
            )
        }
        try {
            assertThrows(Exception::class.java) {
                io { RoomUpdateTaskStore(db).replace(task, cancelled) }
            }
        } finally {
            io { db.openHelper.writableDatabase.execSQL("DROP TRIGGER e42_fail_release") }
        }
        assertEquals(task, state(task))
        assertEquals(listOf(id), owners(task, ClusterOwnerType.UPDATE_TASK))
        assertTrue(io { RoomUpdateTaskStore(db).replace(task, cancelled) })
        assertEquals(cancelled, state(task))
        assertTrue(owners(task, ClusterOwnerType.UPDATE_TASK).isEmpty())
    }

    @Test fun conflictOrAmbiguousGenerationRejectsJournalChangeWithoutDeletingOwners() {
        val task = job()
        val id = owner(task)
        val other = owner(task, ClusterOwnerType.REVIEW_REQUIRED)
        val cancelled = task.move(TaskState.CANCELLED, 21L)
        assertThrows(IllegalStateException::class.java) {
            io { RoomUpdateTaskStore(db).replace(task, cancelled) }
        }
        assertEquals(task, state(task))
        assertEquals(listOf(id), owners(task, ClusterOwnerType.UPDATE_TASK))
        assertEquals(listOf(other), owners(task, ClusterOwnerType.REVIEW_REQUIRED))
        val another = owner(task)
        assertThrows(IllegalStateException::class.java) {
            io { RoomUpdateTaskStore(db).replace(task, cancelled) }
        }
        assertEquals(task, state(task))
        assertEquals(setOf(id, another), owners(task, ClusterOwnerType.UPDATE_TASK).toSet())
    }

    @Test fun staleCasAndPauseGuardCannotDropPrivateSourceReference() {
        val task = job()
        val id = owner(task)
        val preparing = task.move(TaskState.PREPARING, 11L)
        assertTrue(io { RoomUpdateTaskStore(db).replace(task, preparing) })
        val staleCancelled = task.move(TaskState.CANCELLED, 12L)
        assertFalse(io { RoomUpdateTaskStore(db).replace(task, staleCancelled) })
        assertEquals(preparing, state(task))
        assertEquals(listOf(id), owners(task, ClusterOwnerType.UPDATE_TASK))

        val deferred = preparing.move(TaskState.DEFERRED, 12L, TaskIssue.PAUSED)
        io { RoomUpdateTaskStore(db).setPaused(true) }
        assertFalse(io { RoomUpdateTaskStore(db).replace(preparing, deferred, requireUnpaused = true) })
        assertEquals(preparing, state(task))
        assertEquals(listOf(id), owners(task, ClusterOwnerType.UPDATE_TASK))
    }

    @Test fun reviewedSuccessfulRecoveryNeverReleasesReviewHold() {
        val task = job()
        val id = owner(task)
        val applying = applying(task)
        val review = applying.move(TaskState.REVIEW_REQUIRED, 13L, TaskIssue.INTERRUPTED)
        assertTrue(io { RoomUpdateTaskStore(db).replace(applying, review) })
        val recovered = review.move(TaskState.SUCCEEDED, 14L,
            completion = CompletionKind.RECOVERED_VERSION_VERIFIED)
        assertTrue(io { RoomUpdateTaskStore(db).replace(review, recovered) })
        assertEquals(recovered, state(task))
        assertEquals(listOf(id), owners(task, ClusterOwnerType.REVIEW_REQUIRED))
        assertTrue(owners(task, ClusterOwnerType.UPDATE_TASK).isEmpty())
    }

    private fun <T> io(block: () -> T): T = runBlocking {
        withContext(Dispatchers.IO) { block() }
    }
}
