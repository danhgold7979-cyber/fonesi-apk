package vn.fonesi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class EngineCapabilitiesTest {
    private fun validClusterIdentity(): GuestApkClusterIdentity {
        val base = GuestApkClusterMemberIdentity(null, "a".repeat(64), false)
        val alpha = GuestApkClusterMemberIdentity("feature.alpha", "b".repeat(64), true)
        val beta = GuestApkClusterMemberIdentity("feature.beta", "c".repeat(64), true,
            dependencies = setOf("feature.alpha"))
        val french = GuestApkClusterMemberIdentity("feature.alpha.fr", "d".repeat(64), false,
            configForSplit = "feature.alpha")
        return GuestApkClusterIdentity(
            "vn.fonesi.fixture", 1, setOf("e".repeat(64)),
            listOf(base, alpha, beta, french), "f".repeat(64),
        )
    }

    @Test fun unavailableEngineExposesNoOperationalOrAcceptedCapabilities() {
        val engine = UnavailableCloneEngine()
        assertFalse(engine.capabilities.singleApkPreflightSupported)
        assertFalse(engine.capabilities.singleApkInstallSupported)
        assertFalse(engine.capabilities.packageIdentityReadbackSupported)
        assertFalse(engine.capabilities.splitClusterImportSupported)
        assertFalse(engine.capabilities.guestActivityLifecycleVerified)
        assertFalse(engine.capabilities.frameworkMediationVerified)
        assertFalse(engine.capabilities.updateReadbackVerified)
        assertTrue(engine.capabilities.nativePageSizesBytesVerified.isEmpty())
        assertFalse(engine.capabilities.satisfiesIsolation(EngineIsolationLevel.HOST_UID_NAMESPACE))
        assertFalse(engine.capabilities.satisfiesIsolation(EngineIsolationLevel.SEPARATE_OS_UID))
    }

    @Test fun legacyReadyEngineDoesNotImplyGuestLifecycleOrAccountIsolation() {
        val engine = object : CloneEngine {
            override val availability = EngineAvailability.Ready(UpdateScope.SHARED_PACKAGE)
            override fun listClones(): List<CloneRecord> = emptyList()
            override fun create(source: InstalledSource, label: String): CloneRecord =
                error("not part of capability test")
            override fun launch(cloneId: String) = Unit
            override fun delete(cloneId: String) = Unit
            override fun update(target: UpdateTarget): List<CloneRecord> = emptyList()
        }
        assertTrue(engine.availability is EngineAvailability.Ready)
        assertFalse(engine.capabilities.splitClusterImportSupported)
        assertFalse(engine.capabilities.guestActivityLifecycleVerified)
        assertFalse(engine.capabilities.guestServiceLifecycleVerified)
        assertFalse(engine.capabilities.guestReceiverLifecycleVerified)
        assertFalse(engine.capabilities.guestProviderLifecycleVerified)
        assertFalse(engine.capabilities.frameworkMediationVerified)
        assertFalse(engine.capabilities.updateReadbackVerified)
        assertTrue(engine.capabilities.isolationLevel == EngineIsolationLevel.UNKNOWN)
        assertFalse(engine.capabilities.satisfiesIsolation(EngineIsolationLevel.HOST_UID_NAMESPACE))
        assertFalse(engine.capabilities.satisfiesIsolation(EngineIsolationLevel.SEPARATE_OS_UID))
    }

    @Test fun strongerIsolationRequiresAnExplicitAcceptedBoundary() {
        val hostNamespace = EngineCapabilities(isolationLevel = EngineIsolationLevel.HOST_UID_NAMESPACE)
        val separateUid = EngineCapabilities(isolationLevel = EngineIsolationLevel.SEPARATE_OS_UID)
        val guestOs = EngineCapabilities(isolationLevel = EngineIsolationLevel.SEPARATE_GUEST_OS)
        val unknown = EngineCapabilities()
        assertTrue(hostNamespace.satisfiesIsolation(EngineIsolationLevel.HOST_UID_NAMESPACE))
        assertFalse(hostNamespace.satisfiesIsolation(EngineIsolationLevel.SEPARATE_OS_UID))
        assertTrue(separateUid.satisfiesIsolation(EngineIsolationLevel.HOST_UID_NAMESPACE))
        assertTrue(separateUid.satisfiesIsolation(EngineIsolationLevel.SEPARATE_OS_UID))
        assertFalse(separateUid.satisfiesIsolation(EngineIsolationLevel.SEPARATE_GUEST_OS))
        assertTrue(guestOs.satisfiesIsolation(EngineIsolationLevel.SEPARATE_GUEST_OS))
        assertFalse(unknown.satisfiesIsolation(EngineIsolationLevel.HOST_UID_NAMESPACE))
    }
    @Test fun splitProviderCapabilitiesRequirePreflightDispatchAndCompleteReadback() {
        val none = GuestBackendCapabilities()
        assertFalse(none.splitClusterImportSupported)
        val preflightOnly = GuestBackendCapabilities(splitClusterPreflightSupported = true)
        assertFalse(preflightOnly.splitClusterImportSupported)
        val dispatchWithoutReadback = GuestBackendCapabilities(
            splitClusterPreflightSupported = true,
            splitClusterInstallDispatchSupported = true,
        )
        assertFalse(dispatchWithoutReadback.splitClusterImportSupported)
        val completeImport = GuestBackendCapabilities(
            splitClusterPreflightSupported = true,
            splitClusterInstallDispatchSupported = true,
            splitClusterReadbackVerified = true,
        )
        assertTrue(completeImport.splitClusterImportSupported)
        assertFalse(completeImport.splitClusterCodeExecutionVerified)
        assertFalse(completeImport.splitClusterAtomicUpdateVerified)
        assertThrows(IllegalArgumentException::class.java) {
            GuestBackendCapabilities(splitClusterCodeExecutionVerified = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GuestBackendCapabilities(splitClusterAtomicUpdateVerified = true)
        }
    }

    @Test fun splitClusterIdentityRequiresOneBaseUniqueMembersAndAcyclicDependencies() {
        val identity = validClusterIdentity()
        val base = identity.members[0]
        val alpha = identity.members[1]
        val beta = identity.members[2]
        val french = identity.members[3]
        assertEquals(identity, identity.requireStructurallyValid())
        assertThrows(IllegalArgumentException::class.java) {
            identity.copy(members = listOf(alpha, beta, french)).requireStructurallyValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            identity.copy(members = listOf(base, alpha, alpha, beta, french)).requireStructurallyValid()
        }
        val cyclic = listOf(
            base,
            alpha.copy(dependencies = setOf("feature.beta")),
            beta.copy(dependencies = setOf("feature.alpha")),
        )
        assertThrows(IllegalArgumentException::class.java) {
            identity.copy(members = cyclic).requireStructurallyValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            identity.copy(members = listOf(base, alpha, beta.copy(dependencies = setOf("missing"))))
                .requireStructurallyValid()
        }
    }

    @Test fun splitClusterIdentityRequiresCanonicalNamesAndFeatureConfigRelationships() {
        val identity = validClusterIdentity()
        val base = identity.members[0]
        val alpha = identity.members[1]
        val beta = identity.members[2]
        val french = identity.members[3]
        assertThrows(IllegalArgumentException::class.java) {
            identity.copy(packageName = "invalid package").requireStructurallyValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            identity.copy(members = listOf(base, beta, alpha, french)).requireStructurallyValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            identity.copy(members = listOf(base, alpha.copy(configForSplit = "feature.beta"), beta, french))
                .requireStructurallyValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            identity.copy(members = listOf(base, alpha, beta, french.copy(configForSplit = "feature.missing")))
                .requireStructurallyValid()
        }
    }

    @Test fun splitClusterReadbackRequiresExactIdentityAndCompleteSdkOwnedMemberSet() {
        val identity = validClusterIdentity()
        val valid = GuestApkClusterReadback(identity, true, true)
        assertEquals(valid, valid.requireMatches(identity))

        val changedMember = identity.members.mapIndexed { index, member ->
            if (index == 1) member.copy(sha256 = "9".repeat(64)) else member
        }
        val mismatches = listOf(
            identity.copy(packageName = "vn.fonesi.other"),
            identity.copy(versionCode = 2),
            identity.copy(signerDigests = setOf("9".repeat(64))),
            identity.copy(members = changedMember),
            identity.copy(members = identity.members.dropLast(1)),
        )
        mismatches.forEach { mismatch ->
            assertThrows(IllegalArgumentException::class.java) {
                GuestApkClusterReadback(mismatch, true, true).requireMatches(identity)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(sdkOwnedPrivateStorageVerified = false).requireMatches(identity)
        }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(completeMemberSetVerified = false).requireMatches(identity)
        }
    }

    @Test fun engineCannotClaimSplitImportWithoutCompleteProviderEvidence() {
        assertThrows(IllegalArgumentException::class.java) {
            EngineCapabilities(splitClusterImportSupported = true)
        }
        val complete = EngineCapabilities(
            splitClusterPreflightSupported = true,
            splitClusterInstallDispatchSupported = true,
            splitClusterReadbackVerified = true,
            splitClusterImportSupported = true,
        )
        assertTrue(complete.splitClusterImportSupported)
        assertFalse(complete.splitClusterCodeExecutionVerified)
    }

}
