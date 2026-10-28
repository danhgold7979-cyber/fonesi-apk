package vn.fonesi.app.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateJournalRecoveryWorkerTest {
    @Test fun idleJournalRecoveryCompletesWithoutStartingCloneEngine() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<UpdateJournalRecoveryWorker>(context).build()
        val result = worker.startWork().get(30, TimeUnit.SECONDS)
        assertTrue("Journal-only worker should succeed when recovery has no retryable work",
            result is ListenableWorker.Result.Success)
    }

    @Test fun workerSourceHasNoRuntimeActivationDependency() {
        // This test is intentionally architectural: background recovery must remain independent
        // from the closed-source SDK and all installed/guest package APIs.
        val source = java.io.File(
            "src/main/java/vn/fonesi/app/update/UpdateJournalRecoveryWorker.kt"
        ).readText().replace(
            Regex("""/\*[\s\S]*?\*/|//[^\r\n]*"""), ""
        )
        assertTrue(source.contains("UpdateJournalRecovery("))
        assertTrue(source.contains("RoomUpdateTaskStore"))
        assertTrue(!source.contains("RuntimeBindings"))
        assertTrue(!source.contains("LocalServices"))
        assertTrue(!source.contains("FCore"))
        assertTrue(!source.contains("PackageManager"))
        assertTrue(!source.contains("AndroidSourceCatalog"))
    }
}
