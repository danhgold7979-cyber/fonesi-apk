package vn.fonesi.engine

import com.android.apksig.ApkVerifier
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.fonesi.apk.PrivateApkStore
import vn.fonesi.core.InstalledSource
import java.io.File
import java.security.MessageDigest

/**
 * Real signed binary APK cluster policy tests. These do not instantiate isolated ART loaders,
 * Android ResourcesManager or guest Activities, and do not certify split runtime capability.
 */
class GuestSplitGraphTest {
    @get:Rule val temporary = TemporaryFolder()
    private val fixtureRoot = File(checkNotNull(System.getProperty("fonesi.nativeFixtures")))
    private val store by lazy { PrivateApkStore(temporary.root.toPath().resolve("code")) }
    private fun apk(name: String) = File(fixtureRoot, "$name.apk")
    private fun publish(vararg names: String): String {
        val signers = ApkVerifier.Builder(apk("main-disguised"))
            .setMinCheckedPlatformVersion(26).build().verify().signerCertificates
            .map { MessageDigest.getInstance("SHA-256").digest(it.encoded)
                .joinToString("") { b -> "%02x".format(b.toInt() and 255) } }.toSet()
        return store.publish(InstalledSource("vn.fonesi.fixture", 1, signers,
            names.map { apk(it).path }), 34).id
    }
    private fun signed(id: String) =
        GuestCodeLayout.readForGraphOnly(store, id, 34, temporary.root, "vn.fonesi.host")

    @Test fun isolatedGraphKeepsBaseConfigAndParentChainedFeaturesDistinct() {
        val id = publish("chat-config", "dependent-feature", "base-config",
            "isolated-base", "base")
        val layout = signed(id)
        assertTrue(layout.cluster.apks.base.isolatedSplits)
        val graph = GuestSplitGraphPlanner.plan(layout)
        assertTrue(graph.isolated)
        assertEquals(listOf("__base__", "feature.chat", "feature.pay"), graph.nodes.map { it.id })
        val byName = layout.cluster.apks.splits.associateBy { it.splitName }
        assertEquals(listOf(layout.base, byName.getValue("config.en").file),
            graph.node(GuestSplitGraphPlanner.BASE).apks)
        assertEquals("feature.chat", graph.node("feature.pay").parentId)
        assertEquals(GuestSplitGraphPlanner.BASE, graph.node("feature.chat").parentId)
        assertEquals(listOf(byName.getValue("feature.chat").file,
            byName.getValue("feature.chat.en").file), graph.node("feature.chat").apks)
        assertEquals(listOf(byName.getValue("feature.pay").file), graph.node("feature.pay").apks)
        assertEquals(layout.allApks.toSet(), graph.allApks.toSet())
        assertEquals(graph.allApks.size, graph.allApks.distinct().size)
    }

    @Test fun isolatedSignedInputsAreDeterministicRegardlessOfInputFilenameOrder() {
        val first = signed(publish("isolated-base", "dependent-feature", "base",
            "chat-config", "base-config"))
        val second = signed(publish("base-config", "chat-config", "base",
            "dependent-feature", "isolated-base"))
        fun snapshot(layout: GuestCodeLayout) = GuestSplitGraphPlanner.plan(layout).nodes.map {
            Triple(it.id, it.parentId, it.apks.map(File::getName))
        }
        assertEquals(snapshot(first), snapshot(second))
    }

    @Test fun normalNonIsolatedClusterUsesOnlyOneFlatNode() {
        val id = publish("main-disguised", "base-config", "base", "chat-config")
        val layout = GuestCodeLayout.read(store, id, 34, temporary.root, "vn.fonesi.host")
        val graph = GuestSplitGraphPlanner.plan(layout)
        assertFalse(graph.isolated)
        assertEquals(1, graph.nodes.size)
        assertEquals(layout.allApks, graph.node(GuestSplitGraphPlanner.BASE).apks)
    }

    @Test fun productionLoaderStillRejectsIsolatedClusterEvenAfterGraphPlanning() {
        val id = publish("isolated-base", "base-config", "base")
        assertTrue(GuestSplitGraphPlanner.plan(signed(id)).isolated)
        assertThrows(IllegalArgumentException::class.java) {
            GuestCodeLayout.read(store, id, 34, temporary.root, "vn.fonesi.host")
        }
    }

    @Test fun graphPolicyRejectsMultiParentAndCycleEvenIfCallerForgedVerifiedModel() {
        val layout = signed(publish("isolated-base", "base", "dependent-feature"))
        val apks = layout.cluster.apks
        val altered = apks.splits.map { member ->
            if (member.splitName == "feature.pay")
                member.copy(dependencies = setOf("feature.chat", "feature.pay"))
            else member
        }
        val unsafe = layout.copy(cluster = layout.cluster.copy(apks = apks.copy(splits = altered)))
        assertThrows(IllegalArgumentException::class.java) {
            GuestSplitGraphPlanner.plan(unsafe)
        }
        val cyclic = altered.map { member ->
            if (member.splitName == "feature.chat")
                member.copy(dependencies = setOf("feature.pay"))
            else if (member.splitName == "feature.pay")
                member.copy(dependencies = setOf("feature.chat"))
            else member
        }
        val cycleGraph = layout.copy(cluster = layout.cluster.copy(apks = apks.copy(splits = cyclic)))
        assertThrows(IllegalArgumentException::class.java) {
            GuestSplitGraphPlanner.plan(cycleGraph)
        }
    }

    @Test fun privateSplitTamperingIsRejectedBeforeGraphCanBeBuilt() {
        val id = publish("isolated-base", "base-config")
        val file = store.open(id, 34).apks.splits.single().file
        assertTrue(file.setWritable(true))
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        assertThrows(Exception::class.java) { signed(id) }
    }

    @Test fun missingFeatureDependencyIsRejectedBySignedApkAuditor() {
        assertThrows(Exception::class.java) {
            publish("isolated-base", "dependent-feature")
        }
    }
}
