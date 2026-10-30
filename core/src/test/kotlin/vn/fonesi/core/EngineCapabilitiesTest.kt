package vn.fonesi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class EngineCapabilitiesTest {
    private val signer = "e".repeat(64)

    private fun identityWithDigest(identity: GuestApkClusterIdentity) =
        identity.copy(clusterDigest = identity.computeCanonicalDigest())

    private fun validClusterIdentity(): GuestApkClusterIdentity {
        val base = GuestApkClusterMemberIdentity(
            splitName = null, sha256 = "a".repeat(64), feature = false,
            byteLength = 100, signerDigests = setOf(signer),
            requiredTypes = setOf("base__abi"),
        )
        val alpha = GuestApkClusterMemberIdentity(
            splitName = "feature.alpha", sha256 = "b".repeat(64), feature = true,
            byteLength = 200, signerDigests = setOf(signer),
        )
        val french = GuestApkClusterMemberIdentity(
            splitName = "feature.alpha.fr", sha256 = "d".repeat(64), feature = false,
            byteLength = 300, signerDigests = setOf(signer),
            configForSplit = "feature.alpha", dependencies = setOf("feature.alpha"),
        )
        val beta = GuestApkClusterMemberIdentity(
            splitName = "feature.beta", sha256 = "c".repeat(64), feature = true,
            byteLength = 400, signerDigests = setOf(signer),
            providedTypes = setOf("base__abi"), dependencies = setOf("feature.alpha"),
        )
        val unsigned = GuestApkClusterIdentity(
            "vn.fonesi.fixture", 1, setOf(signer),
            listOf(base, alpha, french, beta), "",
        )
        return identityWithDigest(unsigned)
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
        val base = identity.members.first()
        val alpha = identity.members.single { it.splitName == "feature.alpha" }
        val beta = identity.members.single { it.splitName == "feature.beta" }
        val french = identity.members.single { it.splitName == "feature.alpha.fr" }
        assertEquals(identity, identity.requireStructurallyValid())
        assertThrows(IllegalArgumentException::class.java) {
            identityWithDigest(identity.copy(members = listOf(alpha, french, beta))).requireStructurallyValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            identityWithDigest(identity.copy(members = listOf(base, alpha, alpha, french, beta)))
                .requireStructurallyValid()
        }
        val cyclic = listOf(
            base,
            alpha.copy(dependencies = setOf("feature.beta")),
            beta.copy(dependencies = setOf("feature.alpha")),
        )
        assertThrows(IllegalArgumentException::class.java) {
            identityWithDigest(identity.copy(members = cyclic)).requireStructurallyValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            identityWithDigest(identity.copy(members = listOf(base, alpha, beta.copy(dependencies = setOf("missing")))))
                .requireStructurallyValid()
        }
    }

    @Test fun splitClusterIdentityRequiresCanonicalNamesAndFeatureConfigRelationships() {
        val identity = validClusterIdentity()
        val base = identity.members.first()
        val alpha = identity.members.single { it.splitName == "feature.alpha" }
        val beta = identity.members.single { it.splitName == "feature.beta" }
        val french = identity.members.single { it.splitName == "feature.alpha.fr" }
        assertThrows(IllegalArgumentException::class.java) {
            identityWithDigest(identity.copy(packageName = "invalid package")).requireStructurallyValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            identityWithDigest(identity.copy(members = listOf(base, beta, alpha, french))).requireStructurallyValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            identityWithDigest(identity.copy(members = listOf(base, alpha.copy(configForSplit = "feature.beta"), french, beta)))
                .requireStructurallyValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            identityWithDigest(identity.copy(members = listOf(base, alpha, french.copy(configForSplit = "feature.missing"), beta)))
                .requireStructurallyValid()
        }
        val numericSegment = alpha.copy(splitName = "feature.1")
        assertThrows(IllegalArgumentException::class.java) {
            identityWithDigest(identity.copy(members = listOf(base, numericSegment, alpha, french, beta)))
                .requireStructurallyValid()
        }
    }

    @Test fun splitIdentityMatchesAuditorConfigDependencyAndSplitTypeRules() {
        val identity = validClusterIdentity()
        val base = identity.members.first()
        val alpha = identity.members.single { it.splitName == "feature.alpha" }
        val french = identity.members.single { it.splitName == "feature.alpha.fr" }
        val beta = identity.members.single { it.splitName == "feature.beta" }

        // ApkSetAuditor permits an empty configForSplit on a non-feature config split.
        val emptyConfig = identityWithDigest(identity.copy(
            members = listOf(base, alpha, french.copy(configForSplit = ""), beta),
        ))
        assertEquals(emptyConfig, emptyConfig.requireStructurallyValid())

        // uses-split dependencies are valid on non-feature splits too.
        assertEquals(setOf("feature.alpha"), french.dependencies)
        assertEquals(identity, identity.requireStructurallyValid())

        assertThrows(IllegalArgumentException::class.java) {
            identityWithDigest(identity.copy(
                members = listOf(base.copy(splitRequired = true, requiredTypes = emptySet())),
            )).requireStructurallyValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            identityWithDigest(identity.copy(
                members = listOf(base, alpha, french, beta.copy(providedTypes = emptySet())),
            )).requireStructurallyValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            identityWithDigest(identity.copy(packageName = "a".repeat(256) + ".x")).requireStructurallyValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            identityWithDigest(identity.copy(
                members = listOf(base.copy(byteLength = 0), alpha, french, beta),
            )).requireStructurallyValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            identityWithDigest(identity.copy(
                members = listOf(base, alpha.copy(signerDigests = setOf("9".repeat(64))), french, beta),
            )).requireStructurallyValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            identityWithDigest(identity.copy(
                members = listOf(base, alpha.copy(configForSplit = ""), french, beta),
            )).requireStructurallyValid()
        }
    }

    @Test fun splitIdentityDigestCommitsEveryMetadataFieldAndMemberLength() {
        val identity = validClusterIdentity()
        val changedMetadata = identity.members.mapIndexed { index, member ->
            if (index == 2) member.copy(isolatedSplits = true) else member
        }
        val changedLength = identity.members.mapIndexed { index, member ->
            if (index == 1) member.copy(byteLength = member.byteLength + 1) else member
        }
        assertThrows(IllegalArgumentException::class.java) {
            identity.copy(members = changedMetadata).requireStructurallyValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            identity.copy(members = changedLength).requireStructurallyValid()
        }
        val recomputed = identityWithDigest(identity.copy(members = changedMetadata))
        assertTrue(recomputed.clusterDigest != identity.clusterDigest)
        assertEquals(recomputed, recomputed.requireStructurallyValid())
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
        assertThrows(IllegalArgumentException::class.java) {
            EngineCapabilities(splitClusterCodeExecutionVerified = true)
        }
        val imported = EngineCapabilities(
            splitClusterPreflightSupported = true,
            splitClusterInstallDispatchSupported = true,
            splitClusterReadbackVerified = true,
            splitClusterImportSupported = true,
        )
        assertTrue(imported.splitClusterImportSupported)
        assertThrows(IllegalArgumentException::class.java) {
            imported.copy(splitClusterAtomicUpdateVerified = true)
        }
        val executable = imported.copy(splitClusterCodeExecutionVerified = true)
        val atomic = executable.copy(splitClusterAtomicUpdateVerified = true)
        assertTrue(executable.splitClusterCodeExecutionVerified)
        assertTrue(atomic.splitClusterAtomicUpdateVerified)
    }

}
