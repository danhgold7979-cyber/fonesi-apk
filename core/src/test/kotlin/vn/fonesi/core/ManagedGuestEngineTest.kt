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
    @Test fun peerStartingAfterNativeUserCreationBlocksNewSharedGuestInstall() {
        val a = engine().create(source, "A")
        val peer = registry.list().single { it.id == a.id }
        val key = peer.userId to peer.packageName
        val nativeBefore = checkNotNull(backend.packages[key])
        backend.afterCreateUser = { backend.packages[key] = nativeBefore.copy(running = true) }
        assertThrows(IllegalStateException::class.java) { engine().create(source, "B") }
        assertEquals(1, backend.installs.size)
        assertEquals(listOf(InstanceState.READY, InstanceState.RESERVED), registry.list().map { it.state })
        assertEquals(1, snapshots.discards) // Native entry is uncertain; retain second snapshot.
        assertTrue(checkNotNull(backend.packages[key]).running)
        assertTrue(backend.uninstalls.isEmpty())
    }
    @Test fun peerSignatureChangingAfterNativeUserCreationBlocksNewSharedInstall() {
        engine().create(source, "A")
        val peer = registry.list().single()
        val key = peer.userId to peer.packageName
        val nativeBefore = checkNotNull(backend.packages[key])
        backend.afterCreateUser = { backend.packages[key] = nativeBefore.copy(signers = setOf("changed")) }
        assertThrows(IllegalStateException::class.java) { engine().create(source, "B") }
        assertEquals(1, backend.installs.size)
        assertEquals(setOf("changed"), backend.packages[key]?.signers)
        assertEquals(InstanceState.RESERVED, registry.list().last().state)
        assertTrue(backend.uninstalls.isEmpty())
    }
    @Test fun removingExistingPeerAfterNativeUserCreationCannotShrinkSharedScope() {
        val a = engine().create(source, "A")
        backend.afterCreateUser = { registry.state(a.id, InstanceState.DELETED) }
        assertThrows(IllegalStateException::class.java) { engine().create(source, "B") }
        assertEquals(1, backend.installs.size)
        assertEquals(listOf(InstanceState.DELETED, InstanceState.RESERVED), registry.list().map { it.state })
        assertTrue(backend.uninstalls.isEmpty())
    }
    @Test fun newReservedPeerAfterNativeUserCreationBlocksSharedImport() {
        engine().create(source, "A")
        backend.afterCreateUser = { registry.reserve(source.packageName) }
        assertThrows(IllegalStateException::class.java) { engine().create(source, "B") }
        assertEquals(1, backend.installs.size)
        assertEquals(listOf(InstanceState.READY, InstanceState.RESERVED, InstanceState.RESERVED), registry.list().map { it.state })
        assertTrue(backend.uninstalls.isEmpty())
    }
    @Test fun frozenSourceInvalidatedAfterNativeUserCreationRetainsReservation() {
        engine().create(source, "A")
        backend.afterCreateUser = { snapshots.valid = false }
        assertThrows(IllegalStateException::class.java) { engine().create(source, "B") }
        assertEquals(1, backend.installs.size)
        assertEquals(1, snapshots.discards)
        assertEquals(InstanceState.RESERVED, registry.list().last().state)
        assertTrue(backend.uninstalls.isEmpty())
    }
    @Test fun unexpectedNativePackageAtReservedUserMustNotBeOverwritten() {
        engine().create(source, "A")
        backend.afterCreateUser = {
            val newestUser = checkNotNull(backend.userIds.maxOrNull())
            backend.packages[newestUser to source.packageName] = GuestPackage(source.packageName, 42, setOf("alien-signer"), false)
        }
        assertThrows(IllegalStateException::class.java) { engine().create(source, "B") }
        assertEquals(1, backend.installs.size)
        assertEquals(InstanceState.RESERVED, registry.list().last().state)
        assertEquals(42L, backend.packages[registry.list().last().userId to source.packageName]?.version)
        assertTrue(backend.uninstalls.isEmpty())
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
    @Test fun pendingUnknownLaunchCannotDispatchTwiceBeforeVerifiedStop() {
        val clone = engine().create(source, "A")
        engine().launch(clone.id)
        assertTrue(registry.list().single().launchPending)
        assertNull(engine().listClones().single().running)
        assertThrows(IllegalStateException::class.java) { engine().launch(clone.id) }
        assertTrue(registry.list().single().launchPending)
        assertEquals(1, backend.launchAttempts)
        assertEquals(1, backend.launches.size)
        engine().stop(clone.id) // Only exact positive stopped readback clears pending.
        assertFalse(registry.list().single().launchPending)
        engine().launch(clone.id)
        assertEquals(2, backend.launchAttempts)
        assertTrue(registry.list().single().launchPending)
    }
    @Test fun failedLaunchCannotBeBlindlyRetriedAcrossEngineRecreation() {
        val clone = engine().create(source, "A")
        backend.failLaunch = true
        assertThrows(IllegalStateException::class.java) { engine().launch(clone.id) }
        backend.failLaunch = false
        assertThrows(IllegalStateException::class.java) { engine().launch(clone.id) }
        assertEquals(1, backend.launchAttempts)
        assertTrue(registry.list().single().launchPending)
        assertTrue(backend.launches.isEmpty())
        engine().stop(clone.id)
        engine().launch(clone.id)
        assertEquals(2, backend.launchAttempts)
        assertEquals(listOf(registry.list().single().userId to source.packageName), backend.launches)
    }
    @Test fun positiveRunningReadbackResolvesUnknownAndAllowsReopenOfSameNativeGuest() {
        val clone = engine().create(source, "A")
        engine().launch(clone.id)
        val row = registry.list().single()
        backend.packages[row.userId to row.packageName] =
            GuestPackage(row.packageName, source.versionCode, source.signerDigests, true)
        engine().launch(clone.id)
        assertEquals(2, backend.launchAttempts)
        assertEquals(listOf(row.userId to row.packageName, row.userId to row.packageName), backend.launches)
        assertTrue(registry.list().single().launchPending)
        assertEquals(true, engine().listClones().single().running)
        assertFalse(registry.list().single().launchPending)
    }

    @Test fun reservedNativePackageReadbackCannotAuthorizeLaunch() {
        backend.failAfterInstall = true
        assertThrows(IllegalStateException::class.java) { engine().create(source, "Unconfirmed") }
        val reserved = registry.list().single()
        assertEquals(InstanceState.RESERVED, reserved.state)
        assertNotNull(backend.inspect(reserved.userId, reserved.packageName))
        assertThrows(IllegalStateException::class.java) { engine().launch(reserved.id) }
        assertFalse(registry.list().single().launchPending)
        assertEquals(0, backend.launchAttempts)
        assertTrue(backend.launches.isEmpty())
    }

    @Test fun nonReadyLifecycleStatesCannotDispatchOrMarkPending() {
        val confirmed = engine().create(source, "A")
        val row = registry.list().single()
        for (state in listOf(InstanceState.DELETING, InstanceState.CONFLICT, InstanceState.DELETED)) {
            registry.state(confirmed.id, state)
            assertThrows(Exception::class.java) { engine().launch(confirmed.id) }
            assertFalse(registry.list().single().launchPending)
            assertEquals(0, backend.launchAttempts)
            assertTrue(backend.launches.isEmpty())
        }
        registry.state(confirmed.id, InstanceState.READY)
        engine().launch(confirmed.id)
        assertTrue(registry.list().single().launchPending)
        assertEquals(1, backend.launchAttempts)
        assertEquals(listOf(row.userId to row.packageName), backend.launches)
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
    @Test fun missingNativeReadbackAfterStopPreservesLaunchPendingForReview() {
        val clone = engine().create(source, "A")
        engine().launch(clone.id)
        val row = registry.list().single()
        backend.packages.remove(row.userId to row.packageName)
        assertThrows(IllegalStateException::class.java) { engine().stop(clone.id) }
        assertEquals(0, backend.stopAttempts) // Missing pre-stop identity cannot authorize SDK mutation.
        assertTrue(registry.list().single().launchPending)
        assertEquals(InstanceState.READY, registry.list().single().state)
        assertTrue(backend.uninstalls.isEmpty())
    }

    @Test fun corruptedNativeIdentityBeforeStopCannotInvokeSdkOrClearPending() {
        val clone = engine().create(source, "A")
        engine().launch(clone.id)
        val row = registry.list().single()
        backend.packages[row.userId to row.packageName] =
            GuestPackage(row.packageName, -1L, source.signerDigests, false)
        assertThrows(IllegalStateException::class.java) { engine().stop(clone.id) }
        assertEquals(0, backend.stopAttempts)
        assertTrue(registry.list().single().launchPending)
    }

    @Test fun changedNativeVersionAfterStopCannotClearPending() {
        val clone = engine().create(source, "A")
        engine().launch(clone.id)
        backend.afterStop = { userId, packageName ->
            val key = userId to packageName
            backend.packages[key] = checkNotNull(backend.packages[key]).copy(version = source.versionCode + 1)
        }
        assertThrows(IllegalStateException::class.java) { engine().stop(clone.id) }
        assertEquals(1, backend.stopAttempts)
        assertTrue(registry.list().single().launchPending)
        assertEquals(InstanceState.READY, registry.list().single().state)
    }

    @Test fun changedNativeSignersAfterStopCannotClearPending() {
        val clone = engine().create(source, "A")
        engine().launch(clone.id)
        backend.afterStop = { userId, packageName ->
            val key = userId to packageName
            backend.packages[key] = checkNotNull(backend.packages[key]).copy(signers = setOf("different-signer"))
        }
        assertThrows(IllegalStateException::class.java) { engine().stop(clone.id) }
        assertEquals(1, backend.stopAttempts)
        assertTrue(registry.list().single().launchPending)
        assertEquals(InstanceState.READY, registry.list().single().state)
    }

    @Test fun wrongPackageGuestReadbackCannotConfirmStopOrClearPending() {
        val clone = engine().create(source, "A")
        engine().launch(clone.id)
        val row = registry.list().single()
        backend.afterStop = { userId, packageName ->
            backend.packages[userId to packageName] =
                GuestPackage("another.package", source.versionCode, source.signerDigests, false)
        }
        assertThrows(IllegalStateException::class.java) { engine().stop(clone.id) }
        assertEquals(1, backend.stopAttempts)
        assertEquals(listOf(clone.id), registry.list().map { it.id })
        assertTrue(registry.list().single().launchPending)
    }

    @Test fun invalidVersionGuestReadbackCannotConfirmStop() {
        val clone = engine().create(source, "A")
        engine().launch(clone.id)
        val row = registry.list().single()
        backend.afterStop = { userId, packageName ->
            backend.packages[userId to packageName] =
                GuestPackage(packageName, -1L, source.signerDigests, false)
        }
        assertThrows(IllegalStateException::class.java) { engine().stop(clone.id) }
        assertTrue(registry.list().single().launchPending)
        assertEquals(1, backend.stopAttempts)
    }

    @Test fun emptySignerGuestReadbackCannotConfirmStop() {
        val clone = engine().create(source, "A")
        engine().launch(clone.id)
        val row = registry.list().single()
        backend.afterStop = { userId, packageName ->
            backend.packages[userId to packageName] =
                GuestPackage(packageName, source.versionCode, emptySet(), false)
        }
        assertThrows(IllegalStateException::class.java) { engine().stop(clone.id) }
        assertTrue(registry.list().single().launchPending)
        assertEquals(1, backend.stopAttempts)
    }

    @Test fun stillRunningNativeReadbackCannotConfirmStopEvenIfBackendReturns() {
        val clone = engine().create(source, "A")
        engine().launch(clone.id)
        val row = registry.list().single()
        backend.packages[row.userId to row.packageName] =
            GuestPackage(row.packageName, source.versionCode, source.signerDigests, true)
        backend.failStop = true
        assertThrows(IllegalStateException::class.java) { engine().stop(clone.id) }
        assertTrue(registry.list().single().launchPending)
        assertEquals(1, backend.stopAttempts)
    }

    @Test fun positiveExactNativeStoppedReadbackAloneClearsDurablePending() {
        val clone = engine().create(source, "A")
        engine().launch(clone.id)
        assertTrue(registry.list().single().launchPending)
        engine().stop(clone.id)
        assertFalse(registry.list().single().launchPending)
        assertEquals(InstanceState.READY, registry.list().single().state)
        assertEquals(listOf(clone.id), engine().listClones().map { it.id })
        assertEquals(1, backend.stopAttempts)
        assertEquals(0, backend.uninstalls.size)
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
    @Test fun changedNativeVersionBeforeDeleteDispatchDoesNotRemoveGuest() {
        val clone = engine().create(source, "A")
        val row = registry.list().single()
        val original = checkNotNull(backend.inspect(row.userId, row.packageName))
        val guard = ManagedGuestEngine(backend, registry, snapshots) {
            backend.packages[row.userId to row.packageName] = original.copy(version = original.version + 1)
            true
        }
        assertThrows(IllegalStateException::class.java) { guard.delete(clone.id) }
        assertEquals(InstanceState.READY, registry.list().single().state)
        assertEquals(original.version + 1, backend.inspect(row.userId, row.packageName)?.version)
        assertTrue(backend.uninstalls.isEmpty())
        assertTrue(row.userId in backend.users())
    }
    @Test fun changedNativeSigningIdentityBeforeDeleteDispatchDoesNotRemoveGuest() {
        val clone = engine().create(source, "A")
        val row = registry.list().single()
        val original = checkNotNull(backend.inspect(row.userId, row.packageName))
        val guard = ManagedGuestEngine(backend, registry, snapshots) {
            backend.packages[row.userId to row.packageName] = original.copy(signers = setOf("new-signer"))
            true
        }
        assertThrows(IllegalStateException::class.java) { guard.delete(clone.id) }
        assertEquals(InstanceState.READY, registry.list().single().state)
        assertEquals(setOf("new-signer"), backend.inspect(row.userId, row.packageName)?.signers)
        assertTrue(backend.uninstalls.isEmpty())
    }
    @Test fun guestBecomingRunningBeforeDeleteDispatchCannotBeUninstalled() {
        val clone = engine().create(source, "A")
        val row = registry.list().single()
        val original = checkNotNull(backend.inspect(row.userId, row.packageName))
        val guard = ManagedGuestEngine(backend, registry, snapshots) {
            backend.packages[row.userId to row.packageName] = original.copy(running = true)
            true
        }
        assertThrows(IllegalStateException::class.java) { guard.delete(clone.id) }
        assertEquals(InstanceState.READY, registry.list().single().state)
        assertTrue(checkNotNull(backend.inspect(row.userId, row.packageName)).running)
        assertTrue(backend.uninstalls.isEmpty())
    }
    @Test fun nativeGuestAppearingAfterInitiallyMissingReadbackRequiresReconciliation() {
        val clone = engine().create(source, "A")
        val row = registry.list().single()
        backend.packages.remove(row.userId to row.packageName)
        val guard = ManagedGuestEngine(backend, registry, snapshots) {
            backend.packages[row.userId to row.packageName] = GuestPackage(row.packageName, 2, setOf("other-signer"), false)
            true
        }
        assertThrows(IllegalStateException::class.java) { guard.delete(clone.id) }
        assertEquals(InstanceState.READY, registry.list().single().state)
        assertEquals(2L, backend.inspect(row.userId, row.packageName)?.version)
        assertTrue(backend.uninstalls.isEmpty())
    }
    @Test fun consistentlyAbsentNativeGuestCanRetireWithoutSdkUninstall() {
        val clone = engine().create(source, "A")
        val row = registry.list().single()
        backend.packages.remove(row.userId to row.packageName)
        engine().delete(clone.id)
        assertEquals(InstanceState.DELETED, registry.list().single().state)
        assertTrue(backend.uninstalls.isEmpty())
        assertTrue(row.userId in backend.users())
        val next = engine().create(source, "B")
        assertNotEquals(clone.id, next.id)
        assertTrue(registry.list().last().userId > row.userId)
    }
    @Test fun sharedUpdateRejectsNativeVersionDriftAfterAuthorization() {
        val clone = engine().create(source, "A")
        val row = registry.list().single()
        val key = row.userId to row.packageName
        val prior = checkNotNull(backend.packages[key])
        val guarded = ManagedGuestEngine(backend, registry, snapshots) {
            backend.packages[key] = prior.copy(version = prior.version + 1)
            true
        }
        assertThrows(IllegalStateException::class.java) {
            guarded.update(UpdateTarget(source.copy(versionCode = 2), listOf(clone.id)))
        }
        assertEquals(1, backend.installs.size)
        assertEquals(2L, backend.packages[key]?.version)
        assertTrue(backend.uninstalls.isEmpty())
    }
    @Test fun sharedUpdateRejectsNativeSigningDriftAfterAuthorization() {
        val clone = engine().create(source, "A")
        val row = registry.list().single()
        val key = row.userId to row.packageName
        val prior = checkNotNull(backend.packages[key])
        val guarded = ManagedGuestEngine(backend, registry, snapshots) {
            backend.packages[key] = prior.copy(signers = setOf("unexpected-signer"))
            true
        }
        assertThrows(IllegalStateException::class.java) {
            guarded.update(UpdateTarget(source.copy(versionCode = 2), listOf(clone.id)))
        }
        assertEquals(1, backend.installs.size)
        assertEquals(setOf("unexpected-signer"), backend.packages[key]?.signers)
        assertTrue(backend.uninstalls.isEmpty())
    }
    @Test fun sharedUpdateRejectsPeerRunningAfterAuthorization() {
        val a = engine().create(source, "A")
        val b = engine().create(source, "B")
        val second = registry.list().single { it.id == b.id }
        val key = second.userId to second.packageName
        val prior = checkNotNull(backend.packages[key])
        val guarded = ManagedGuestEngine(backend, registry, snapshots) {
            backend.packages[key] = prior.copy(running = true)
            true
        }
        assertThrows(IllegalStateException::class.java) {
            guarded.update(UpdateTarget(source.copy(versionCode = 2), listOf(a.id, b.id)))
        }
        assertEquals(2, backend.installs.size)
        assertTrue(checkNotNull(backend.packages[key]).running)
        assertTrue(backend.uninstalls.isEmpty())
    }
    @Test fun sharedUpdateRejectsNewReservedPeerAfterAuthorization() {
        val clone = engine().create(source, "A")
        val guarded = ManagedGuestEngine(backend, registry, snapshots) {
            registry.reserve(source.packageName)
            true
        }
        assertThrows(IllegalStateException::class.java) {
            guarded.update(UpdateTarget(source.copy(versionCode = 2), listOf(clone.id)))
        }
        assertEquals(1, backend.installs.size)
        assertEquals(listOf(InstanceState.READY, InstanceState.RESERVED), registry.list().map { it.state })
        assertTrue(backend.uninstalls.isEmpty())
    }
    @Test fun sharedUpdateRejectsNativeUserIdReassignmentAfterAuthorization() {
        val clone = engine().create(source, "A")
        val original = registry.list().single()
        val nativeBefore = checkNotNull(backend.packages[original.userId to original.packageName])
        val guarded = ManagedGuestEngine(backend, registry, snapshots) {
            registry.rows[0] = original.copy(userId = original.userId + 100)
            backend.packages[original.userId + 100 to original.packageName] = nativeBefore
            true
        }
        assertThrows(IllegalStateException::class.java) {
            guarded.update(UpdateTarget(source.copy(versionCode = 2), listOf(clone.id)))
        }
        assertEquals(1, backend.installs.size)
        assertEquals(nativeBefore, backend.packages[original.userId to original.packageName])
        assertEquals(InstanceState.READY, registry.list().single().state)
        assertTrue(backend.uninstalls.isEmpty())
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
    @Test fun reservedNativePeerBlocksAnotherCreateBeforeSnapshotOrSdkMutation() {
        backend.failAfterInstall = true
        assertThrows(IllegalStateException::class.java) { engine().create(source, "Interrupted") }
        val reserved = registry.list().single()
        assertEquals(InstanceState.RESERVED, reserved.state)
        val actualPackage = checkNotNull(backend.inspect(reserved.userId, reserved.packageName))
        backend.failAfterInstall = false
        val preparedBefore = snapshots.prepares
        assertThrows(IllegalStateException::class.java) { engine().create(source, "Another") }
        assertEquals(preparedBefore, snapshots.prepares)
        assertEquals(listOf(reserved), registry.list())
        assertEquals(setOf(reserved.userId), backend.users())
        assertEquals(actualPackage, backend.inspect(reserved.userId, reserved.packageName))
        assertEquals(1, backend.installs.size)
        assertTrue(backend.uninstalls.isEmpty())
    }
    @Test fun reservedNativePeerBlocksSharedUpdateBeforeSdkMutation() {
        backend.failAfterInstall = true
        assertThrows(IllegalStateException::class.java) { engine().create(source, "Interrupted") }
        val reserved = registry.list().single()
        val nativeBefore = checkNotNull(backend.inspect(reserved.userId, source.packageName))
        backend.failAfterInstall = false
        assertThrows(IllegalStateException::class.java) {
            engine().update(UpdateTarget(source.copy(versionCode = 2), listOf(reserved.id)))
        }
        assertEquals(listOf(reserved), registry.list())
        assertEquals(nativeBefore, backend.inspect(reserved.userId, source.packageName))
        assertEquals(1, backend.installs.size)
        assertTrue(backend.uninstalls.isEmpty())
    }
    @Test fun mixedReadyAndReservedNativePeersCannotNarrowOrAdvanceSharedUpdate() {
        val confirmed = engine().create(source, "Ready")
        backend.failAfterInstall = true
        assertThrows(IllegalStateException::class.java) { engine().create(source, "Interrupted") }
        val unconfirmed = registry.list().single { it.state == InstanceState.RESERVED }
        val nativeBefore = backend.packages.toMap()
        backend.failAfterInstall = false
        assertThrows(IllegalStateException::class.java) {
            engine().update(UpdateTarget(source.copy(versionCode = 2), listOf(confirmed.id)))
        }
        assertThrows(IllegalStateException::class.java) {
            engine().update(UpdateTarget(source.copy(versionCode = 2), listOf(confirmed.id, unconfirmed.id)))
        }
        assertEquals(2, backend.installs.size)
        assertEquals(nativeBefore, backend.packages)
        assertEquals(InstanceState.RESERVED, registry.list().single { it.id == unconfirmed.id }.state)
        assertTrue(backend.uninstalls.isEmpty())
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
    var afterStop: (Int, String) -> Unit = { _, _ -> }
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
    override fun stop(userId: Int, packageName: String) {
        stopAttempts++
        if (!failStop) packages.computeIfPresent(userId to packageName) { _, old -> old.copy(running = false) }
        afterStop(userId, packageName)
    }
    override fun uninstall(userId: Int, packageName: String) { uninstalls += userId to packageName; packages.remove(userId to packageName) }
}
