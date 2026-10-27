package vn.fonesi.core

import org.junit.Assert.*
import org.junit.Test
import vn.fonesi.core.update.*

/** Protocol/fault tests only; native SDK and account isolation are not executed. */
class ManagedGuestEngineTest {
    private val source = InstalledSource("test.social", 1, setOf("signer"), listOf("/original/base.apk"))
    private val registry = RegistryDouble()
    private val backend = BackendDouble()
    private val snapshots = SnapshotDouble()
    private fun engine() = ManagedGuestEngine(backend, registry, snapshots)

    @Test fun createUsesFrozenSourceAndReadsBackBeforePublishingReady() {
        val a = engine().create(source, "A")
        assertEquals("/frozen/base.apk", backend.installs.single().second.apkPaths.single())
        assertEquals(InstanceState.READY, registry.list().single().state)
        assertEquals(a, engine().listClones().single())
        assertEquals(1, snapshots.discards)
    }
    @Test fun splitSourceIsRejectedBeforeAnySdkOrRegistryMutation() {
        assertThrows(UnsupportedSourceException::class.java) { engine().create(source.copy(apkPaths = listOf("base", "split")), "A") }
        assertTrue(registry.list().isEmpty()); assertTrue(backend.installs.isEmpty()); assertEquals(0, snapshots.prepares)
    }
    @Test fun failedSnapshotVerificationDoesNotEnterSdk() {
        snapshots.valid = false
        assertThrows(IllegalStateException::class.java) { engine().create(source, "A") }
        assertTrue(registry.list().isEmpty()); assertTrue(backend.users().isEmpty()); assertEquals(1, snapshots.discards)
    }
    @Test fun newInstanceCannotSilentlyUpgradeOrInterruptExistingSharedPackage() {
        val a = engine().create(source, "A")
        assertThrows(IllegalStateException::class.java) { engine().create(source.copy(versionCode = 2), "B") }
        engine().launch(a.id)
        assertThrows(IllegalStateException::class.java) { engine().create(source, "B") }
        assertEquals(1, backend.installs.size)
    }
    @Test fun independentlySelectedInstancesDispatchDifferentNativeUsers() {
        val a = engine().create(source, "A"); val b = engine().create(source, "B")
        engine().launch(b.id)
        assertNotEquals(a.id, b.id)
        assertEquals(listOf(registry.list().single { it.id == b.id }.userId to source.packageName), backend.launches)
    }
    @Test fun asynchronousLaunchRemainsUnknownAcrossEngineRecreationUntilConfirmedStop() {
        val a = engine().create(source, "A")
        engine().launch(a.id)
        assertNull(engine().listClones().single().running)
        assertTrue(registry.list().single().launchPending)
        assertThrows(IllegalStateException::class.java) { engine().delete(a.id) }
        engine().stop(a.id)
        assertEquals(false, engine().listClones().single().running)
    }
    @Test fun launchExceptionPreservesPendingMarkerAndIsNeverRetriedAutomatically() {
        val a = engine().create(source, "A"); backend.failLaunch = true
        assertThrows(IllegalStateException::class.java) { engine().launch(a.id) }
        assertTrue(registry.list().single().launchPending)
        assertNull(engine().listClones().single().running)
        assertEquals(1, backend.launchAttempts)
    }
    @Test fun observedRunningConfirmsDispatchAndFailedStopDoesNotClearItsState() {
        val a = engine().create(source, "A"); engine().launch(a.id)
        val row = registry.list().single()
        backend.packages[row.userId to source.packageName] = GuestPackage(source.packageName, 1, source.signerDigests, true)
        assertEquals(true, engine().listClones().single().running)
        assertFalse(registry.list().single().launchPending)
        backend.failStop = true
        assertThrows(IllegalStateException::class.java) { engine().stop(a.id) }
        assertEquals(true, engine().listClones().single().running)
    }
    @Test fun preexistingNativeUserIsReservedAsConflictAndNeverOverwritten() {
        backend.userIds += 10001
        engine().create(source, "A")
        assertEquals(listOf(InstanceState.CONFLICT, InstanceState.READY), registry.list().map { it.state })
        assertEquals(10002, backend.installs.single().first)
    }
    @Test fun interruptedCreateRetainsReservationAndSnapshotWithoutDestructiveRollback() {
        backend.failAfterInstall = true
        assertThrows(IllegalStateException::class.java) { engine().create(source, "A") }
        assertEquals(InstanceState.RESERVED, registry.list().single().state)
        assertEquals(0, snapshots.discards); assertTrue(backend.uninstalls.isEmpty())
        assertEquals(1, engine().listClones().size) // Real readback can be reconciled into metadata.
    }
    @Test fun deleteOnlyAddressesSelectedUserAndDoesNotReuseItsReservation() {
        val a = engine().create(source, "A"); val b = engine().create(source, "B")
        engine().delete(a.id)
        assertEquals(listOf(b.id), engine().listClones().map { it.id })
        assertEquals(1, backend.uninstalls.size)
        val c = engine().create(source, "C")
        assertNotEquals(a.id, c.id)
        assertEquals(listOf(10001, 10002, 10003), registry.list().map { it.userId })
    }
    @Test fun sharedUpdateImportsOnceAndVerifiesAllAffectedInstances() {
        val a = engine().create(source, "A"); val b = engine().create(source, "B")
        val updated = engine().update(UpdateTarget(source.copy(versionCode = 2), listOf(a.id, b.id)))
        assertTrue(updated.all { it.versionCode == 2L }); assertEquals(3, backend.installs.size)
    }
    @Test fun sharedUpdateRejectsIncompleteScopeBusyInstancesAndDowngrade() {
        val a = engine().create(source, "A"); val b = engine().create(source, "B")
        assertThrows(IllegalStateException::class.java) { engine().update(UpdateTarget(source.copy(versionCode = 2), listOf(a.id))) }
        engine().launch(a.id)
        assertThrows(IllegalStateException::class.java) { engine().update(UpdateTarget(source.copy(versionCode = 2), listOf(a.id, b.id))) }
        engine().stop(a.id)
        assertThrows(IllegalStateException::class.java) { engine().update(UpdateTarget(source.copy(versionCode = 0), listOf(a.id, b.id))) }
        assertEquals(2, backend.installs.size)
    }
    @Test fun partialUpdateReadbackFailsWithoutReinstallOrDeletingAccountData() {
        val a = engine().create(source, "A"); val b = engine().create(source, "B"); backend.shareCode = false
        assertThrows(IllegalStateException::class.java) { engine().update(UpdateTarget(source.copy(versionCode = 2), listOf(a.id, b.id))) }
        assertEquals(3, backend.installs.size); assertTrue(backend.uninstalls.isEmpty())
    }
    @Test fun unavailableBackendNeverCreatesOrListsInventedInstances() {
        backend.ready = false
        assertTrue(engine().availability is EngineAvailability.Unavailable)
        assertThrows(IllegalStateException::class.java) { engine().create(source, "A") }
        assertThrows(IllegalStateException::class.java) { engine().listClones() }
        assertTrue(registry.list().isEmpty())
    }
    @Test fun missingRegistryPeerBlocksSharedCreateAndUpdateWithoutShrinkingScope() {
        val a = engine().create(source, "A")
        registry.reserve(source.packageName) // Interrupted import, no metadata row and no native readback.
        assertThrows(IllegalStateException::class.java) { engine().create(source, "B") }
        assertThrows(IllegalStateException::class.java) { engine().update(UpdateTarget(source.copy(versionCode = 2), listOf(a.id))) }
        assertEquals(1, backend.installs.size)
        assertEquals(0, backend.uninstalls.size)
        assertEquals(2, registry.list().size)
    }
    @Test fun deletingRegistryPeerBlocksSharedImportUntilReconciled() {
        val a = engine().create(source, "A")
        registry.state(a.id, InstanceState.DELETING)
        assertThrows(IllegalStateException::class.java) { engine().create(source, "B") }
        assertThrows(IllegalStateException::class.java) { engine().update(UpdateTarget(source.copy(versionCode = 2), listOf(a.id))) }
        assertEquals(1, backend.installs.size)
    }
    @Test fun foregroundLostDuringPreparationNeverReservesOrEntersSdk() {
        var authorized = true
        snapshots.afterPrepare = { authorized = false }
        val guarded = ManagedGuestEngine(backend, registry, snapshots) { authorized }
        assertThrows(IllegalStateException::class.java) { guarded.create(source, "A") }
        assertTrue(registry.list().isEmpty()); assertTrue(backend.users().isEmpty())
        assertTrue(backend.installs.isEmpty()); assertEquals(1, snapshots.discards)
    }
    @Test fun rejectedSourcePreflightNeverReservesUserOrLeavesUncertainNativeState() {
        backend.preflight = { error("Invalid signature or unsupported ABI") }
        assertThrows(IllegalStateException::class.java) { engine().create(source, "A") }
        assertTrue(registry.list().isEmpty()); assertTrue(backend.users().isEmpty())
        assertTrue(backend.installs.isEmpty()); assertEquals(1, snapshots.discards)
    }
    @Test fun foregroundLostDuringReadOnlyPreflightNeverEntersSdk() {
        var authorized = true
        backend.preflight = { frozen -> assertEquals("/frozen/base.apk", frozen.apkPaths.single()); authorized = false }
        val guarded = ManagedGuestEngine(backend, registry, snapshots) { authorized }
        assertThrows(IllegalStateException::class.java) { guarded.create(source, "A") }
        assertTrue(registry.list().isEmpty()); assertTrue(backend.users().isEmpty()); assertEquals(1, snapshots.discards)
    }
    @Test fun peerBecomingBusyDuringPreparationBlocksSharedCreate() {
        engine().create(source, "A")
        val peer = registry.list().single()
        snapshots.afterPrepare = { backend.packages[peer.userId to source.packageName] = GuestPackage(source.packageName, 1, source.signerDigests, true) }
        assertThrows(IllegalStateException::class.java) { engine().create(source, "B") }
        assertEquals(1, registry.list().size); assertEquals(1, backend.installs.size)
    }
    @Test fun authorizationRequiredBeforeLaunchStopDeleteOrUpdateSdkEntry() {
        val a = engine().create(source, "A")
        val guarded = ManagedGuestEngine(backend, registry, snapshots) { false }
        assertThrows(IllegalStateException::class.java) { guarded.launch(a.id) }
        assertThrows(IllegalStateException::class.java) { guarded.stop(a.id) }
        assertThrows(IllegalStateException::class.java) { guarded.delete(a.id) }
        assertThrows(IllegalStateException::class.java) { guarded.update(UpdateTarget(source.copy(versionCode = 2), listOf(a.id))) }
        assertEquals(0, backend.launchAttempts); assertEquals(0, backend.stopAttempts)
        assertEquals(1, backend.installs.size); assertTrue(backend.uninstalls.isEmpty())
        assertFalse(registry.list().single().launchPending); assertEquals(InstanceState.READY, registry.list().single().state)
    }
    @Test fun authorizationLostAfterFirstSdkMutationStillCompletesCreateReadback() {
        var authorized = true
        backend.afterCreateUser = { authorized = false }
        val created = ManagedGuestEngine(backend, registry, snapshots) { authorized }.create(source, "A")
        assertEquals(created, engine().listClones().single())
        assertEquals(InstanceState.READY, registry.list().single().state)
        assertEquals(1, snapshots.discards); assertTrue(backend.uninstalls.isEmpty())
    }
    @Test fun unusedReservationIsRetiredWithoutReusingIdWhenAuthorizationExpiresBeforeSdkEntry() {
        var checks = 0
        val guarded = ManagedGuestEngine(backend, registry, snapshots) { ++checks < 3 }
        assertThrows(IllegalStateException::class.java) { guarded.create(source, "A") }
        assertEquals(InstanceState.DELETED, registry.list().single().state)
        assertTrue(backend.users().isEmpty()); assertTrue(backend.installs.isEmpty())
        val created = engine().create(source, "B")
        assertNotEquals(registry.list().first().id, created.id)
        assertEquals(10002, backend.installs.single().first)
    }
}

private class RegistryDouble : InstanceRegistry {
    val rows = mutableListOf<GuestInstance>()
    override fun reserve(packageName: String) = GuestInstance("id-${rows.size + 1}", 10001 + rows.size, packageName, InstanceState.RESERVED, false).also { rows += it }
    override fun list() = rows.toList()
    override fun state(id: String, state: InstanceState) { val i = rows.indexOfFirst { it.id == id }; rows[i] = rows[i].copy(state = state) }
    override fun pending(id: String, pending: Boolean) { val i = rows.indexOfFirst { it.id == id }; rows[i] = rows[i].copy(launchPending = pending) }
}
private class SnapshotDouble : SourceSnapshotter {
    var valid = true; var prepares = 0; var discards = 0
    var afterPrepare: () -> Unit = {}
    override fun prepare(taskId: String, expected: InstalledSource): PreparedSource {
        prepares++; afterPrepare(); return PreparedSource(expected.copy(apkPaths = listOf("/frozen/base.apk")), "f", emptyList())
    }
    override fun verify(snapshot: PreparedSource) = valid
    override fun discard(taskId: String) { discards++ }
}
private class BackendDouble : GuestBackend {
    override var ready = true
    val userIds = mutableSetOf<Int>()
    val packages = mutableMapOf<Pair<Int, String>, GuestPackage>()
    val installs = mutableListOf<Pair<Int, InstalledSource>>()
    val launches = mutableListOf<Pair<Int, String>>()
    val uninstalls = mutableListOf<Pair<Int, String>>()
    var failLaunch = false; var failAfterInstall = false; var failStop = false; var shareCode = true; var launchAttempts = 0
    var stopAttempts = 0
    var afterCreateUser: () -> Unit = {}
    var preflight: (InstalledSource) -> Unit = {}
    override fun validateSource(source: InstalledSource) { preflight(source) }
    override fun users() = userIds.toSet()
    override fun createUser(userId: Int) { userIds += userId; afterCreateUser() }
    override fun inspect(userId: Int, packageName: String) = packages[userId to packageName]
    override fun install(userId: Int, source: InstalledSource) {
        installs += userId to source
        if (shareCode) packages.keys.filter { it.second == source.packageName }.forEach { packages[it] = GuestPackage(source.packageName, source.versionCode, source.signerDigests, false) }
        packages[userId to source.packageName] = GuestPackage(source.packageName, source.versionCode, source.signerDigests, false)
        check(!failAfterInstall)
    }
    override fun launch(userId: Int, packageName: String) { launchAttempts++; check(!failLaunch); launches += userId to packageName }
    override fun stop(userId: Int, packageName: String) { stopAttempts++; if (!failStop) packages.computeIfPresent(userId to packageName) { _, old -> old.copy(running = false) } }
    override fun uninstall(userId: Int, packageName: String) { uninstalls += userId to packageName; packages.remove(userId to packageName) }
}
