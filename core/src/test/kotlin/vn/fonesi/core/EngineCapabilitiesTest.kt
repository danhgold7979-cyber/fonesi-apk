package vn.fonesi.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.fonesi.core.update.UpdateTarget

class EngineCapabilitiesTest {
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
}
