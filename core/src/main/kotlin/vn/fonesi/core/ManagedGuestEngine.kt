package vn.fonesi.core

import vn.fonesi.core.update.SourceSnapshotter
import java.util.UUID

enum class InstanceState { RESERVED, READY, DELETING, DELETED, CONFLICT }
data class GuestInstance(val id: String, val userId: Int, val packageName: String, val state: InstanceState, val launchPending: Boolean)
data class GuestPackage(val packageName: String, val version: Long, val signers: Set<String>, val running: Boolean)

/** Provider claims are conservative and independently verified; readiness does not imply capability. */
data class GuestBackendCapabilities(
    val singleApkPreflightSupported: Boolean = false,
    val singleApkInstallSupported: Boolean = false,
    val packageIdentityReadbackSupported: Boolean = false,
    val splitClusterPreflightSupported: Boolean = false,
    val splitClusterInstallDispatchSupported: Boolean = false,
    val splitClusterReadbackVerified: Boolean = false,
    val splitClusterCodeExecutionVerified: Boolean = false,
    val splitClusterAtomicUpdateVerified: Boolean = false,
) {
    val splitClusterImportSupported: Boolean
        get() = splitClusterPreflightSupported && splitClusterInstallDispatchSupported && splitClusterReadbackVerified
    init {
        require(!splitClusterCodeExecutionVerified || splitClusterImportSupported) {
            "Split code execution requires complete cluster preflight, install and readback"
        }
        require(!splitClusterAtomicUpdateVerified || (splitClusterImportSupported && splitClusterCodeExecutionVerified)) {
            "Atomic split update requires import/readback and split code execution"
        }
    }
}

data class GuestApkClusterMemberIdentity(
    val splitName: String?,
    val sha256: String,
    val feature: Boolean,
    val configForSplit: String? = null,
    val dependencies: Set<String> = emptySet(),
)

data class GuestApkClusterIdentity(
    val packageName: String,
    val versionCode: Long,
    val signerDigests: Set<String>,
    val members: List<GuestApkClusterMemberIdentity>,
    val clusterDigest: String,
) {
    /** Validates identity completeness only; signature verification remains the provider's duty. */
    fun requireStructurallyValid(): GuestApkClusterIdentity {
        val digest = Regex("^[0-9a-f]{64}$")
        val splitPattern = Regex("^[A-Za-z_][A-Za-z0-9_.]*$")
        require(packageName.isNotBlank() && versionCode >= 0 && signerDigests.isNotEmpty())
        require(signerDigests.all(digest::matches) && digest.matches(clusterDigest))
        require(members.isNotEmpty() && members.count { it.splitName == null } == 1)
        require(members.all { digest.matches(it.sha256) })
        val splits = members.mapNotNull { it.splitName }
        require(splits.distinct().size == splits.size && splits.all(splitPattern::matches))
        val featureNames = members.filter { it.feature }.mapNotNull { it.splitName }.toSet()
        require(members.filter { it.splitName == null }.all { it.feature.not() && it.configForSplit == null && it.dependencies.isEmpty() })
        require(members.all { member ->
            (member.configForSplit == null || member.configForSplit in featureNames) &&
                member.dependencies.all { it in featureNames } &&
                (member.feature || member.dependencies.isEmpty())
        })
        val edges = members.filter { it.feature }.associate { checkNotNull(it.splitName) to it.dependencies }
        val remaining = edges.mapValues { it.value.size }.toMutableMap()
        val dependants = mutableMapOf<String, MutableList<String>>()
        edges.forEach { (name, parents) -> parents.forEach { parent -> dependants.getOrPut(parent) { mutableListOf() }.add(name) } }
        val ready = java.util.ArrayDeque(remaining.filterValues { it == 0 }.keys)
        var visited = 0
        while (ready.isNotEmpty()) {
            val name = ready.removeFirst(); visited++
            dependants[name].orEmpty().forEach { child ->
                val count = remaining.getValue(child) - 1; remaining[child] = count
                if (count == 0) ready.addLast(child)
            }
        }
        require(visited == edges.size) { "Split dependency graph contains a cycle" }
        return this
    }
}

data class GuestApkClusterReadback(
    val identity: GuestApkClusterIdentity,
    val sdkOwnedPrivateStorageVerified: Boolean,
    val completeMemberSetVerified: Boolean,
)

interface SplitClusterGuestBackend {
    /** Must verify every member's signature, package, version, split graph and SHA before mutation. */
    fun preflightCluster(source: InstalledSource): GuestApkClusterIdentity
    /** Must import every member atomically or preserve an explicit unknown/reconciliation state. */
    fun installCluster(userId: Int, source: InstalledSource, expected: GuestApkClusterIdentity): GuestApkClusterReadback
    /** Must read the full member set from SDK-owned storage, never from the original app. */
    fun inspectCluster(userId: Int, packageName: String): GuestApkClusterReadback?
}

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
    val capabilities: GuestBackendCapabilities get() = GuestBackendCapabilities()
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
        singleApkPreflightSupported = backend.capabilities.singleApkPreflightSupported,
        singleApkInstallSupported = backend.capabilities.singleApkInstallSupported,
        packageIdentityReadbackSupported = backend.capabilities.packageIdentityReadbackSupported,
        splitClusterPreflightSupported = backend.capabilities.splitClusterPreflightSupported,
        splitClusterInstallDispatchSupported = backend.capabilities.splitClusterInstallDispatchSupported,
        splitClusterReadbackVerified = backend.capabilities.splitClusterReadbackVerified,
        splitClusterImportSupported = backend.capabilities.splitClusterImportSupported,
        splitClusterCodeExecutionVerified = backend.capabilities.splitClusterCodeExecutionVerified,
        splitClusterAtomicUpdateVerified = backend.capabilities.splitClusterAtomicUpdateVerified,
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
            val peerSnapshot = active().filter { it.packageName == source.packageName }
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
            // Creating a native user is the first irreversible SDK mutation.
            // Before importing shared package code, validate the frozen source,
            // our reservation, and every other native guest again. Keep the
            // reservation/snapshot for reconciliation if any check is uncertain.
            check(snapshots.verify(snapshot)) { "Prepared APK changed after native user creation" }
            check(instance(row.id) == row) { "Reserved native guest identity changed before install" }
            check(backend.inspect(row.userId, row.packageName) == null) {
                "Reserved user already has a guest package; refusing to overwrite"
            }
            val peersBeforeInstall = active().filter { it.packageName == source.packageName && it.id != row.id }
            check(peersBeforeInstall.size == peerSnapshot.size && peersBeforeInstall.toSet() == peerSnapshot.toSet()) {
                "Shared package guest scope changed after native user creation"
            }
            check(peersBeforeInstall.all { peer ->
                peer.state == InstanceState.READY &&
                    record(peer)?.let { it.running == false && it.versionCode == source.versionCode &&
                        it.signerDigests == source.signerDigests } == true
            }) { "Existing native guest is busy, missing or changed before shared APK import" }
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
        // A RESERVED import may have native bytes but has not passed create/readback
        // confirmation. Reconciliation can inspect it; normal launch must not.
        check(row.state == InstanceState.READY) { "Guest clone is not confirmed READY" }
        val observed = checkNotNull(record(row)) { "Guest package readback unavailable" }
        // A previous asynchronous launch with no positive running evidence is
        // unknown. Re-dispatching would bypass the durable launchPending guard.
        check(observed.running != null) { "Guest launch outcome is unknown; stop and reconcile before retry" }
        mutationAllowed()
        registry.pending(row.id, true) // Durable before asynchronous launch, including exceptions/death.
        backend.launch(row.userId, row.packageName)
    }

    override fun stop(cloneId: String) {
        ready()
        val row = instance(cloneId)
        mutationAllowed()
        // The SDK may return a different package generation with the same package name.
        // Capture a valid native identity before dispatch, not original-app metadata.
        val beforeStop = checkNotNull(backend.inspect(row.userId, row.packageName)) {
            "Guest stop has no pre-dispatch native package identity; reconciliation required"
        }
        check(beforeStop.packageName == row.packageName &&
            beforeStop.version >= 0 && beforeStop.signers.isNotEmpty()) {
            "Guest stop pre-dispatch package identity is invalid"
        }
        mutationAllowed()
        backend.stop(row.userId, row.packageName)
        // A missing/changed guest is an unknown stop outcome, not proof of success.
        // Never clear launchPending until the exact same native guest image is stopped.
        val observed = checkNotNull(backend.inspect(row.userId, row.packageName)) {
            "Guest stop has no native package readback; reconciliation required"
        }
        check(observed.packageName == beforeStop.packageName &&
            observed.version == beforeStop.version &&
            observed.signers == beforeStop.signers &&
            !observed.running) {
            "Guest stop identity continuity or stopped state not verified"
        }
        registry.pending(row.id, false)
    }

    override fun delete(cloneId: String) {
        ready()
        val row = instance(cloneId)
        val live = record(row)
        check(live == null || live.running == false) { "Instance running or launch outcome unknown" }
        mutationAllowed()
        // The initial native readback may be stale after foreground authorization.
        // Verify the same stopped guest image immediately before marking DELETING
        // and asking the SDK to remove it. An absent image must stay absent.
        val beforeDelete = backend.inspect(row.userId, row.packageName)
        if (live == null) {
            check(beforeDelete == null) { "A native guest appeared during deletion; reconcile before retry" }
        } else {
            check(beforeDelete != null &&
                beforeDelete.packageName == live.packageName &&
                beforeDelete.version == live.versionCode &&
                beforeDelete.signers == live.signerDigests &&
                !beforeDelete.running) {
                "Native guest identity changed or resumed before deletion; reconcile before retry"
            }
        }
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
        // A shared install changes code for every native user; capture stable
        // user/clone ownership as well as the SDK package identity snapshot.
        val originalPeers = active().filter { it.packageName == target.source.packageName }
        check(originalPeers.size == current.size && originalPeers.map { it.id }.toSet() == current.map { it.id }.toSet()) {
            "Shared update guest ownership scope changed"
        }
        val first = instance(current.first().id)
        mutationAllowed()
        // Foreground authorization and external native activity may have
        // changed SDK/registry state since planning. Recheck before dispatch.
        val dispatchPeers = active().filter { it.packageName == target.source.packageName }
        check(dispatchPeers.size == originalPeers.size && dispatchPeers.toSet() == originalPeers.toSet()) {
            "Shared update guest users or lifecycle changed before SDK install"
        }
        val dispatchRecords = sharedRecords(target.source.packageName)
        check(dispatchRecords.size == current.size && dispatchRecords.toSet() == current.toSet() &&
            instance(first.id) == first) {
            "Shared update native identity or running state changed before SDK install"
        }
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
        // Native APK readback is not proof that an interrupted RESERVED import
        // completed. Never mutate shared package code until every peer is READY.
        check(row.state == InstanceState.READY) { "A shared peer is not confirmed READY" }
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
