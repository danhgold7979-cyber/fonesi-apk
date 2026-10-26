package vn.fonesi.core

import org.junit.Assert.*
import org.junit.Test

class UpdatePlannerTest {
    private val packageName = "com.facebook.katana"
    private val source = InstalledSource(packageName, 20, setOf("certificate-a"), listOf("base.apk", "split.apk"))
    private val perClone = EngineAvailability.Ready(UpdateScope.PER_CLONE)
    private val shared = EngineAvailability.Ready(UpdateScope.SHARED_PACKAGE)
    private fun clone(id: String, version: Long = 10, running: Boolean? = false) =
        CloneRecord(id, packageName, version, setOf("certificate-a"), running)
    private fun plan(
        ids: List<String>, clones: List<CloneRecord>,
        engine: EngineAvailability = perClone,
        sources: Map<String, InstalledSource> = mapOf(packageName to source),
    ) = UpdatePlanner.plan(ids, clones, sources, engine)

    @Test fun `single update cannot affect other accounts with a per-clone engine`() {
        val result = plan(listOf("a"), listOf(clone("a"), clone("b")))
        assertEquals(listOf("a"), result.targets.single().affectedCloneIds)
        assertEquals(source.apkPaths, result.targets.single().source.apkPaths)
    }

    @Test fun `shared engine expands impact to every account and coalesces duplicate work`() {
        val result = plan(listOf("a", "b", "a"), listOf(clone("a"), clone("b")), shared)
        assertEquals(listOf("a", "b"), result.targets.single().affectedCloneIds)
        assertTrue(result.blocked.isEmpty())
    }

    @Test fun `shared update waits when an unselected account is open`() {
        val result = plan(listOf("a"), listOf(clone("a"), clone("b", running = true)), shared)
        assertTrue(result.targets.isEmpty())
        assertEquals(UpdateBlockReason.IN_USE_OR_UNKNOWN, result.blocked.single().reason)
    }

    @Test fun `unknown running status must not be treated as stopped`() {
        val result = plan(listOf("a"), listOf(clone("a", running = null)))
        assertEquals(UpdateBlockReason.IN_USE_OR_UNKNOWN, result.blocked.single().reason)
    }

    @Test fun `original app removed does not schedule deletion or reinstall`() {
        val result = plan(listOf("a"), listOf(clone("a")), sources = emptyMap())
        assertTrue(result.targets.isEmpty())
        assertEquals(UpdateBlockReason.SOURCE_MISSING, result.blocked.single().reason)
    }

    @Test fun `signing changes require review rather than overwriting account data`() {
        val result = plan(listOf("a"), listOf(clone("a")), sources = mapOf(
            packageName to source.copy(signerDigests = setOf("different-certificate")),
        ))
        assertEquals(UpdateBlockReason.SIGNER_REVIEW_REQUIRED, result.blocked.single().reason)
        assertTrue(result.targets.isEmpty())
    }

    @Test fun `a stale original app must not downgrade a newer clone`() {
        val result = plan(listOf("a"), listOf(clone("a", version = 30)))
        assertEquals(UpdateBlockReason.NEWER_CLONE_VERSION, result.blocked.single().reason)
    }

    @Test fun `current version is a no-op even while running`() {
        val result = plan(listOf("a"), listOf(clone("a", version = 20, running = true)))
        assertEquals(listOf("a"), result.alreadyCurrent)
        assertTrue(result.targets.isEmpty())
    }

    @Test fun `shared group updates outdated siblings even when selected clone is current`() {
        val result = plan(listOf("a"), listOf(clone("a", 20), clone("b", 10)), shared)
        assertEquals(listOf("a", "b"), result.targets.single().affectedCloneIds)
        assertTrue(result.alreadyCurrent.isEmpty())
    }

    @Test fun `missing engine cannot produce a successful clone or update`() {
        val engine = UnavailableCloneEngine()
        val result = plan(listOf("a"), listOf(clone("a")), engine.availability)
        assertEquals(UpdateBlockReason.ENGINE_UNAVAILABLE, result.blocked.single().reason)
        assertTrue(result.targets.isEmpty())
        assertTrue(engine.listClones().isEmpty())
        assertThrows(EngineUnavailableException::class.java) { engine.create(source, "Facebook 1") }
        assertThrows(EngineUnavailableException::class.java) { engine.launch("a") }
        assertThrows(EngineUnavailableException::class.java) { engine.delete("a") }
        assertThrows(EngineUnavailableException::class.java) { engine.update(UpdateTarget(source, listOf("a"))) }
    }

    @Test fun `request for deleted clone does not select a different account`() {
        val result = plan(listOf("deleted"), listOf(clone("a")))
        assertEquals(UpdateBlockReason.UNKNOWN_CLONE, result.blocked.single().reason)
    }

    @Test fun `invalid or misidentified sources cannot enter update queue`() {
        for (invalid in listOf(
            source.copy(apkPaths = emptyList()),
            source.copy(signerDigests = emptySet()),
            source.copy(packageName = "com.zing.zalo"),
        )) {
            val result = plan(listOf("a"), listOf(clone("a")), sources = mapOf(packageName to invalid))
            assertEquals(UpdateBlockReason.SOURCE_INVALID, result.blocked.single().reason)
        }
    }

    @Test fun `a blocked app does not prevent planning another app`() {
        val zalo = InstalledSource("com.zing.zalo", 20, setOf("certificate-z"), listOf("zalo.apk"))
        val b = CloneRecord("zalo", zalo.packageName, 10, zalo.signerDigests, false)
        val result = plan(listOf("a", "zalo"), listOf(clone("a", running = true), b),
            sources = mapOf(packageName to source, zalo.packageName to zalo))
        assertEquals(listOf("zalo"), result.targets.single().affectedCloneIds)
        assertEquals("a", result.blocked.single().requestedCloneId)
    }

    @Test fun `ambiguous account identifiers fail before planning`() {
        assertThrows(IllegalArgumentException::class.java) {
            plan(listOf("a"), listOf(clone("a"), clone("a")))
        }
    }

    @Test fun `social category is exact and not a compatibility claim`() {
        assertTrue(AppCatalog.isPriority("com.zing.zalo"))
        assertTrue(AppCatalog.isPriority("com.facebook.katana"))
        assertFalse(AppCatalog.isPriority("com.facebook.katana.fake"))
        assertFalse(AppCatalog.isPriority("com.android.settings"))
    }
}
