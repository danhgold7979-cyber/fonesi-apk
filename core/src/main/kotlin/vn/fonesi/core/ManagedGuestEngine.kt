package vn.fonesi.core

import vn.fonesi.core.update.SourceSnapshotter
import java.util.UUID

enum class InstanceState { RESERVED, READY, DELETING, DELETED, CONFLICT }
data class GuestInstance(val id: String, val userId: Int, val packageName: String, val state: InstanceState, val launchPending: Boolean)
data class GuestPackage(val packageName: String, val version: Long, val signers: Set<String>, val running: Boolean)

interface InstanceRegistry {
    /** Durable reservation, never reuses a user ID, including after failed operations or deletion. */
    fun reserve(packageName: String): GuestInstance
    fun list(): List<GuestInstance>
    fun state(id: String, state: InstanceState)
    fun pending(id: String, pending: Boolean)
}

/** Actual platform adapter must read from the guest package manager, not original app metadata. */
interface GuestBackend {
    val ready: Boolean
    /** Read-only preflight on a frozen source; reject before creating any native user. */
    fun validateSource(source: InstalledSource) {}
    fun users(): Set<Int>
    fun createUser(userId: Int)
    fun inspect(userId: Int, packageName: String): GuestPackage?
    /** Must import/copy synchronously into SDK-owned storage, never retain a temporary path. */
    fun install(userId: Int, source: InstalledSource)
    fun launch(userId: Int, packageName: String)
    fun stop(userId: Int, packageName: String)
    fun uninstall(userId: Int, packageName: String)
}

interface StoppableCloneEngine { fun stop(cloneId: String) }
class UnsupportedSourceException : IllegalArgumentException("The candidate requires one complete APK; split APK import is unavailable")

/**
 * Real orchestration over an injected native backend; no guest is emulated here. The current
 * candidate is deliberately single-APK/shared-code until a split-capable engine is available.
 * Call under the same operation lock used by metadata, launches and the update runner.
 */
class ManagedGuestEngine(
    private val backend: GuestBackend,
    private val registry: InstanceRegistry,
    private val snapshots: SourceSnapshotter,
    private val mayMutate: () -> Boolean = { true },
) : CloneEngine, StoppableCloneEngine {
    override val availability: EngineAvailability get() = if (backend.ready) EngineAvailability.Ready(UpdateScope.SHARED_PACKAGE)
        else EngineAvailability.Unavailable("Bộ máy thử nghiệm chưa được bật hoặc không khởi tạo được.")

    /**
     * The adapter can preflight/install one APK and read back package identity when the SDK is
     * initialized. This is not acceptance of guest component lifecycle, framework mediation,
     * native page-size support or account isolation.
     */
    override val capabilities: EngineCapabilities get() = if (backend.ready) EngineCapabilities(
        singleApkPreflightSupported = true,
        singleApkInstallSupported = true,
        packageIdentityReadbackSupported = true,
        splitClusterImportSupported = false,
        guestActivityLifecycleVerified = false,
        guestServiceLifecycleVerified = false,
        guestReceiverLifecycleVerified = false,
        guestProviderLifecycleVerified = false,
        frameworkMediationVerified = false,
        nativePageSizesBytesVerified = emptySet(),
        updateReadbackVerified = false,
        isolationLevel = EngineIsolationLevel.UNKNOWN,
        evidenceRefs = setOf(
            "core/src/main/kotlin/vn/fonesi/core/ManagedGuestEngine.kt",
            "app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt",
        ),
    ) else EngineCapabilities()

    private fun ready() = check(backend.ready) { "Engine unavailable" }
    private fun mutationAllowed() = check(mayMutate()) { "Foreground authorization is required to start an SDK mutation" }
    private fun active() = registry.list().filter { it.state in setOf(InstanceState.RESERVED, InstanceState.READY, InstanceState.DELETING) }
    private fun instance(id: String) = active().single { it.id == id }
    private fun record(row: GuestInstance): CloneRecord? {
        val live = backend.inspect(row.userId, row.packageName) ?: return null
        check(live.packageName == row.packageName && live.version >= 0 && live.signers.isNotEmpty()) { "Invalid guest package readback" }
        if (live.running && row.launchPending) registry.pending(row.id, false)
        return CloneRecord(row.id, live.packageName, live.version, live.signers,
            if (row.launchPending && !live.running) null else live.running)
    }

    override fun listClones(): List<CloneRecord> {
        ready()
        return active().mapNotNull(::record)
    }

    override fun create(source: InstalledSource, label: String): CloneRecord {
        ready()
        mutationAllowed()
        require(label.isNotBlank())
        checkSource(source)
        checkCreatePeers(source)
        val taskId = UUID.randomUUID().toString()
        val snapshot = snapshots.prepare(taskId, source)
        var enteredSdk = false
        var unusedReservation: GuestInstance? = null
        try {
            check(snapshots.verify(snapshot) && snapshot.source.packageName == source.packageName &&
                snapshot.source.versionCode == source.versionCode && snapshot.source.signerDigests == source.signerDigests)
            backend.validateSource(snapshot.source)
            mutationAllowed()
            checkCreatePeers(source)
            var row = registry.reserve(source.packageName)
            unusedReservation = row
            while (row.userId in backend.users()) {
                registry.state(row.id, InstanceState.CONFLICT)
                unusedReservation = null
                row = registry.reserve(source.packageName)
                unusedReservation = row
            }
            mutationAllowed()
            enteredSdk = true
            backend.createUser(row.userId)
            check(row.userId in backend.users()) { "User creation not confirmed" }
            backend.install(row.userId, snapshot.source)
            val observed = checkNotNull(record(row))
            check(observed.versionCode == source.versionCode && observed.signerDigests == source.signerDigests)
            registry.state(row.id, InstanceState.READY)
            snapshots.discard(taskId)
            return observed
        } catch (error: Exception) {
            // Keep uncertain imports/snapshots for diagnosis. Never uninstall to simulate rollback.
            if (!enteredSdk) {
                unusedReservation?.let { registry.state(it.id, InstanceState.DELETED) }
                snapshots.discard(taskId)
            }
            throw error
        }
    }

    override fun launch(cloneId: String) {
        ready()
        val row = instance(cloneId)
        check(row.state != InstanceState.DELETING && record(row) != null)
        mutationAllowed()
        registry.pending(row.id, true) // Durable before asynchronous launch, including exceptions/death.
        backend.launch(row.userId, row.packageName)
    }

    override fun stop(cloneId: String) {
        ready()
        val row = instance(cloneId)
        mutationAllowed()
        backend.stop(row.userId, row.packageName)
        // A missing guest is an unknown stop outcome, never evidence that the existing
        // clone stopped. Keep launchPending durable until an exact native readback.
        val observed = checkNotNull(backend.inspect(row.userId, row.packageName)) {
            "Guest stop has no native package readback; reconciliation required"
        }
        check(observed.packageName == row.packageName &&
            observed.version >= 0 && observed.signers.isNotEmpty() &&
            !observed.running) {
            "Guest stop identity or stopped state not verified"
        }
        registry.pending(row.id, false)
    }

    override fun delete(cloneId: String) {
        ready()
        val row = instance(cloneId)
        val live = record(row)
        check(live == null || live.running == false) { "Instance running or launch outcome unknown" }
        mutationAllowed()
        registry.state(row.id, InstanceState.DELETING)
        if (live != null) backend.uninstall(row.userId, row.packageName)
        check(backend.inspect(row.userId, row.packageName) == null) { "Uninstall not confirmed" }
        registry.state(row.id, InstanceState.DELETED)
        // Do not delete/reuse the SDK user: its durable reservation prevents stale identity reuse.
    }

    override fun update(target: UpdateTarget): List<CloneRecord> {
        ready()
        checkSource(target.source)
        val current = sharedRecords(target.source.packageName)
        check(current.isNotEmpty() && current.map { it.id }.toSet() == target.affectedCloneIds.toSet()) { "Shared update scope changed" }
        check(current.all { it.running == false && it.versionCode <= target.source.versionCode && it.signerDigests == target.source.signerDigests })
        val first = instance(current.first().id)
        mutationAllowed()
        backend.install(first.userId, target.source)
        val observed = sharedRecords(target.source.packageName)
        check(observed.map { it.id }.toSet() == target.affectedCloneIds.toSet() && observed.all {
            it.versionCode == target.source.versionCode && it.signerDigests == target.source.signerDigests
        }) { "Shared update not verified for every instance" }
        return observed
    }

    /** Missing native readback is an uncertain peer, never permission to shrink shared scope. */
    private fun sharedRecords(packageName: String): List<CloneRecord> = active().filter { it.packageName == packageName }.map { row ->
        check(row.state != InstanceState.DELETING) { "A shared peer still needs deletion reconciliation" }
        checkNotNull(record(row)) { "A shared peer cannot be read back" }
    }

    private fun checkCreatePeers(source: InstalledSource) {
        check(sharedRecords(source.packageName).all { it.running == false && it.versionCode == source.versionCode && it.signerDigests == source.signerDigests }) {
            "Stop and update existing instances before importing shared package code"
        }
    }

    private fun checkSource(source: InstalledSource) {
        if (source.apkPaths.size != 1) throw UnsupportedSourceException()
        require(source.packageName.isNotBlank() && source.versionCode >= 0 && source.signerDigests.isNotEmpty())
    }
}
