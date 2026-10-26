package vn.fonesi.core.update

import vn.fonesi.core.*
import java.nio.file.Files
import java.nio.file.Path

/** Test-only doubles; none are linked into an application. */
internal class MemoryTasks : UpdateTaskStore {
    private val tasks = linkedMapOf<String, UpdateTask>()
    private var paused = false
    var beforeReplace: ((UpdateTask) -> Unit)? = null
    @Synchronized override fun list() = tasks.values.toList()
    @Synchronized override fun insertOrGetActive(task: UpdateTask): UpdateTask {
        return tasks.values.firstOrNull { it.dedupKey == task.dedupKey && !it.state.terminal }
            ?: task.also { tasks[it.id] = it }
    }
    @Synchronized override fun replace(expected: UpdateTask, replacement: UpdateTask, requireUnpaused: Boolean): Boolean {
        if (tasks[expected.id] != expected || (requireUnpaused && paused)) return false
        require(replacement.id == expected.id && replacement.revision == expected.revision + 1)
        beforeReplace?.invoke(replacement)
        tasks[expected.id] = replacement
        return true
    }
    @Synchronized override fun isPaused() = paused
    @Synchronized override fun setPaused(paused: Boolean) { this.paused = paused }
}

internal class TestEngine(scope: UpdateScope = UpdateScope.PER_CLONE) : CloneEngine {
    override var availability: EngineAvailability = EngineAvailability.Ready(scope)
    val clones = mutableListOf(CloneRecord("a", "test.social", 1, setOf("signer"), false))
    var calls = 0
    var applyVersion = true
    var beforeApply: (() -> Unit)? = null
    var afterApply: (() -> Unit)? = null
    override fun listClones() = clones.toList()
    override fun create(source: InstalledSource, label: String): CloneRecord = error("Not part of queue test")
    override fun launch(cloneId: String) = error("Not part of queue test")
    override fun delete(cloneId: String) = error("Not part of queue test")
    override fun update(target: UpdateTarget): List<CloneRecord> {
        calls++
        beforeApply?.invoke()
        val receipt = clones.map {
            if (it.id in target.affectedCloneIds) it.copy(versionCode = target.source.versionCode, signerDigests = target.source.signerDigests) else it
        }
        if (applyVersion) { clones.clear(); clones.addAll(receipt) }
        afterApply?.invoke()
        return receipt
    }
}

internal class QueueFixture(val root: Path) {
    val store = MemoryTasks()
    val engine = TestEngine()
    val queue = UpdateQueue(store)
    val originals = Files.createDirectory(root.resolve("originals"))
    val apk = originals.resolve("base.apk").also { Files.writeString(it, "synthetic APK bytes for filesystem tests") }
    var source: InstalledSource? = InstalledSource("test.social", 2, setOf("signer"), listOf(apk.toString()))
    val catalog = SourceCatalog { name -> source?.takeIf { it.packageName == name } }
    val files = FileSourceSnapshotter(root.resolve("snapshots"), catalog)
    var afterPrepare: (() -> Unit)? = null
    val snapshotter = object : SourceSnapshotter {
        override fun prepare(taskId: String, expected: InstalledSource) = files.prepare(taskId, expected).also { afterPrepare?.invoke() }
        override fun verify(snapshot: PreparedSource) = files.verify(snapshot)
        override fun discard(taskId: String) = files.discard(taskId)
    }
    fun enqueue(ids: List<String> = listOf("a"), scope: UpdateScope = UpdateScope.PER_CLONE): UpdateTask =
        queue.enqueue("test-engine", listOf(UpdateTarget(checkNotNull(source), ids)), scope).single()
    fun runner() = UpdateRunner("test-engine", engine, store, catalog, snapshotter, FileQueueLock(root.resolve("queue.lock")))
}

internal class SimulatedProcessDeath : Error("Test-only process interruption")
