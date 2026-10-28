package vn.fonesi.apk

import com.android.apksig.ApkVerifier
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.fonesi.core.InstalledSource
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PrivateApkStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val fixtures = File(checkNotNull(System.getProperty("fonesi.apkFixtures")))
    private val root by lazy { temporary.root.toPath().resolve("code") }
    private fun apk(name: String) = File(fixtures, "$name.apk")
    private fun source(vararg names: String, version: Long = 1): InstalledSource {
        val signers = ApkVerifier.Builder(apk("main-disguised")).setMinCheckedPlatformVersion(26).build().verify()
            .signerCertificates.map { hex(MessageDigest.getInstance("SHA-256").digest(it.encoded)) }.toSet()
        return InstalledSource("vn.fonesi.fixture", version, signers, names.map { apk(it).path })
    }
    private fun cluster() = PrivateApkStore(root).publish(source("chat-config", "dependent-feature", "base", "main-disguised", "base-config"), 34)
    private fun rejectOpen(id: String) { assertThrows(Exception::class.java) { PrivateApkStore(root).open(id, 34) } }
    private fun children(path: Path = root) = Files.list(path).use { it.iterator().asSequence().map { f -> f.fileName.toString() }.toSet() }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun change(file: File, bytes: ByteArray) { check(file.setWritable(true)); file.writeBytes(bytes) }

    @Test fun clusterDigestIsStableAcrossReadbackAndInputOrder() {
        val store = PrivateApkStore(root)
        val first = store.publish(
            source("chat-config", "dependent-feature", "base", "main-disguised", "base-config"), 34
        )
        val second = store.publish(
            source("base-config", "main-disguised", "base", "dependent-feature", "chat-config"), 34
        )
        assertNotEquals(first.id, second.id)
        assertEquals(64, first.clusterDigest.length)
        assertEquals(first.clusterDigest, store.open(first.id, 34).clusterDigest)
        assertEquals(first.clusterDigest, second.clusterDigest)
    }

    @Test fun clusterDigestChangesWhenVerifiedClusterMembersChange() {
        val store = PrivateApkStore(root)
        val baseOnly = store.publish(source("main-disguised"), 34)
        val withSplit = store.publish(source("main-disguised", "base-config"), 34)
        val newVersion = store.publish(source("new-version", "new-version-config", version = 2), 34)
        assertNotEquals(baseOnly.clusterDigest, withSplit.clusterDigest)
        assertNotEquals(withSplit.clusterDigest, newVersion.clusterDigest)
        assertEquals(newVersion.clusterDigest, store.open(newVersion.id, 34).clusterDigest)
    }

    @Test fun clusterReferencesAreIdempotentDurableAndOwnerScoped() {
        val published = cluster()
        val ref = ClusterReference(published.id, ClusterOwnerType.CLONE, "clone-123", "engine-a", 100L)
        val ledger = PrivateApkClusterReferenceLedger(root)
        assertTrue(ledger.acquire(ref))
        assertFalse(ledger.acquire(ref))
        assertFalse("Timestamp changes must not duplicate the same logical owner reference",
            ledger.acquire(ref.copy(createdAtEpochMs = 999L)))
        assertEquals(setOf(ref), PrivateApkClusterReferenceLedger(root).references(published.id))
        assertEquals(setOf(ref), ledger.referencesForOwner(ClusterOwnerType.CLONE, "clone-123", "engine-a"))
        assertTrue(ledger.unreferencedClusterIds().isEmpty())
    }

    @Test fun releasingLastReferenceOnlyMarksOrphanCandidateAndNeverDeletesCluster() {
        val published = cluster()
        val ref = ClusterReference(published.id, ClusterOwnerType.REVIEW_REQUIRED, "task-456", "engine-a", 101L)
        val ledger = PrivateApkClusterReferenceLedger(root)
        assertTrue(ledger.acquire(ref))
        assertTrue(ledger.release(ref.copy(createdAtEpochMs = 999L)))
        assertFalse(ledger.release(ref))
        assertTrue(ledger.references(published.id).isEmpty())
        assertEquals(setOf(published.id), ledger.unreferencedClusterIds())
        assertEquals(published, PrivateApkStore(root).open(published.id, 34))
    }

    @Test fun referenceLedgerRejectsMissingClusterAndInvalidOwnerIdentity() {
        val ledger = PrivateApkClusterReferenceLedger(root)
        val missing = java.util.UUID.randomUUID().toString()
        assertThrows(Exception::class.java) {
            ledger.acquire(ClusterReference(missing, ClusterOwnerType.CLONE, "clone-1", "engine-a", 1L))
        }
        val published = cluster()
        assertThrows(Exception::class.java) {
            ledger.acquire(ClusterReference(published.id, ClusterOwnerType.CLONE, " ", "engine-a", 1L))
        }
        assertTrue(ledger.references(published.id).isEmpty())
    }

    @Test fun concurrentReferenceAcquiresAreSerializedAcrossLedgerInstances() {
        val published = cluster()
        val ref = ClusterReference(published.id, ClusterOwnerType.UPDATE_TASK, "task-789", "engine-a", 102L)
        val first = PrivateApkClusterReferenceLedger(root)
        val second = PrivateApkClusterReferenceLedger(root)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = pool.invokeAll(listOf(Callable { first.acquire(ref) }, Callable { second.acquire(ref) }))
                .map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it })
            assertEquals(setOf(ref), PrivateApkClusterReferenceLedger(root).references(published.id))
        } finally {
            pool.shutdownNow()
        }
    }

    @Test fun corruptReferenceIndexFailsClosedWithoutDeletingPublishedCluster() {
        val published = cluster()
        val ledger = PrivateApkClusterReferenceLedger(root)
        val ref = ClusterReference(published.id, ClusterOwnerType.ROLLBACK, "rollback-1", "engine-a", 103L)
        assertTrue(ledger.acquire(ref))
        val index = root.resolve("cluster.references").toFile()
        change(index, byteArrayOf(0, 1, 2, 3))
        assertThrows(Exception::class.java) { PrivateApkClusterReferenceLedger(root).references(published.id) }
        assertTrue("Reference corruption must never delete package bytes", published.apks.base.file.isFile)
    }

    @Test fun publishedCopiesRetainEverySplitAndExactSignedBytesWithPrivatePaths() {
        val input = source("chat-config", "dependent-feature", "base", "main-disguised", "base-config")
        val expected = ApkSetAuditor.verify(input, 34)
        val copy = PrivateApkStore(root).publish(input, 34)
        assertEquals(4, copy.apks.splits.size)
        val original = (listOf(expected.base) + expected.splits).associateBy { it.splitName }
        for (member in listOf(copy.apks.base) + copy.apks.splits) {
            assertArrayEquals(original.getValue(member.splitName).file.readBytes(), member.file.readBytes())
            assertEquals(original.getValue(member.splitName).sha256, member.sha256)
            assertTrue(member.file.toPath().startsWith(root.resolve(copy.id)))
            assertEquals(copy.apks.base.signers, member.signers)
        }
        assertEquals(setOf(copy.id), children())
    }

    @Test fun restartReadbackRetainsFeatureConfigurationAndDependencyMetadata() {
        val published = cluster()
        val read = PrivateApkStore(root).open(published.id, 34)
        assertEquals(published, read)
        assertEquals(setOf("feature.chat"), read.apks.splits.single { it.splitName == "feature.pay" }.dependencies)
        assertEquals("feature.chat", read.apks.splits.single { it.splitName == "feature.chat.en" }.configFor)
    }

    @Test fun privateClusterCanOpenAfterOriginalSourceIsRemoved() {
        val privateInput = temporary.newFile("installed.apk")
        apk("main-disguised").copyTo(privateInput, overwrite = true)
        val published = PrivateApkStore(root).publish(source("main-disguised").copy(apkPaths = listOf(privateInput.path)), 34)
        assertTrue(privateInput.delete())
        assertEquals(published, PrivateApkStore(root).open(published.id, 34))
    }

    @Test fun oldAndNewCodeVersionsCoexistAndDoNotModifyEachOtherOrSource() {
        val store = PrivateApkStore(root)
        val old = store.publish(source("main-disguised", "base-config"), 34)
        val before = old.apks.base.file.readBytes()
        val next = store.publish(source("new-version", "new-version-config", version = 2), 34)
        assertNotEquals(old.id, next.id)
        assertEquals(1L, store.open(old.id, 34).apks.base.version)
        assertEquals(2L, store.open(next.id, 34).apks.base.version)
        assertArrayEquals(before, old.apks.base.file.readBytes())
        assertArrayEquals(before, apk("main-disguised").readBytes())
    }

    @Test fun badNewSourceCannotAlterAlreadyPublishedVersion() {
        val valid = cluster(); val store = PrivateApkStore(root)
        for (invalid in listOf(source("tampered"), source("main-disguised", "wrong-signer"), source("requires-any-split"),
            source("main-disguised", "dependent-feature"), source("main-disguised", "base-config").copy(versionCode = 8))) {
            assertThrows(Exception::class.java) { store.publish(invalid, 34) }
            assertEquals(setOf(valid.id), children())
            assertEquals(valid, store.open(valid.id, 34))
        }
    }

    @Test fun modifiedPrivateApkNeverFallsBackToInstalledOriginal() {
        val valid = cluster(); val file = valid.apks.splits.first().file
        val modified = file.readBytes(); modified[modified.lastIndex] = (modified.last().toInt() xor 1).toByte()
        change(file, modified)
        rejectOpen(valid.id)
        assertTrue(apk("main-disguised").isFile)
    }

    @Test fun missingPrivateSplitFailsInsteadOfQuietlyLoadingBaseAlone() {
        val valid = cluster(); assertTrue(valid.apks.splits.first().file.delete()); rejectOpen(valid.id)
    }

    @Test fun extraPrivateSplitAndUnexpectedFilesAreRejected() {
        val valid = cluster()
        Files.writeString(root.resolve(valid.id).resolve("unexpected.apk"), "extra")
        rejectOpen(valid.id)
    }

    @Test fun replacingPrivateApkBySymlinkIsRejectedEvenIfBytesMatch() {
        val valid = cluster(); val target = valid.apks.base.file.toPath()
        Files.delete(target); Files.createSymbolicLink(target, apk("main-disguised").toPath())
        rejectOpen(valid.id)
    }

    @Test fun symlinkClusterDirectoryCannotReadAnotherCluster() {
        val valid = cluster(); val id = java.util.UUID.randomUUID().toString()
        Files.createSymbolicLink(root.resolve(id), root.resolve(valid.id))
        rejectOpen(id)
    }

    @Test fun invalidClusterIdsNeverResolveOutsideStore() {
        cluster()
        for (id in listOf("../outside", "", "../../code", "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF")) rejectOpen(id)
    }

    @Test fun truncatedOrTrailingIndexIsRejectedWithoutDeletingCluster() {
        val valid = cluster(); val index = root.resolve(valid.id).resolve("cluster.index").toFile()
        val bytes = index.readBytes()
        for (invalid in listOf(bytes.copyOf(4), bytes + byteArrayOf(0))) {
            change(index, invalid); rejectOpen(valid.id)
            assertTrue(valid.apks.base.file.isFile)
        }
    }

    @Test fun symlinkIndexCannotReadEvenAnOtherwiseValidIndex() {
        val valid = cluster(); val index = root.resolve(valid.id).resolve("cluster.index")
        val saved = temporary.root.toPath().resolve("saved.index"); Files.move(index, saved)
        Files.createSymbolicLink(index, saved); rejectOpen(valid.id)
    }

    @Test fun indexFromAnotherClusterCannotBeReplayedUnderDifferentId() {
        val first = cluster(); val second = cluster()
        change(root.resolve(first.id).resolve("cluster.index").toFile(), root.resolve(second.id).resolve("cluster.index").toFile().readBytes())
        rejectOpen(first.id)
        assertEquals(second, PrivateApkStore(root).open(second.id, 34))
    }

    @Test fun symlinkRootOrParentCannotBeUsedForNewStore() {
        Files.createDirectory(root)
        val link = temporary.root.toPath().resolve("link"); Files.createSymbolicLink(link, root)
        assertThrows(Exception::class.java) { PrivateApkStore(link) }
        assertThrows(Exception::class.java) { PrivateApkStore(link.resolve("child")) }
        assertFalse(Files.exists(root.resolve("child")))
    }

    @Test fun replacedStoreRootIsRejectedByExistingInstanceBeforePublish() {
        val store = PrivateApkStore(root)
        val old = root.resolveSibling("old"); Files.move(root, old); Files.createSymbolicLink(root, old)
        assertThrows(Exception::class.java) { store.publish(source("main-disguised"), 34) }
        assertTrue(children(old).isEmpty())
    }

    @Test fun simultaneousPublishUsesIndependentSnapshotsAndLeavesNoStaging() {
        val store = PrivateApkStore(root); val pool = Executors.newFixedThreadPool(2)
        try {
            val tasks = pool.invokeAll(listOf(Callable { store.publish(source("main-disguised", "base-config"), 34) },
                Callable { PrivateApkStore(root).publish(source("new-version", "new-version-config", version = 2), 34) }))
            val clusters = tasks.map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(2, clusters.map { it.id }.distinct().size)
            assertEquals(clusters.map { it.id }.toSet(), children())
            clusters.forEach { assertEquals(it, PrivateApkStore(root).open(it.id, 34)) }
        } finally { pool.shutdownNow() }
    }

    @Test fun oversizedIndexIsRejectedBeforeParsingAnyMetadata() {
        val valid = cluster(); val index = root.resolve(valid.id).resolve("cluster.index").toFile()
        change(index, ByteArray(256 * 1024 + 1))
        rejectOpen(valid.id)
    }

    @Test fun emptyAndOversizedInputListsNeverCreateStagingOrChangeExistingVersion() {
        val valid = cluster(); val store = PrivateApkStore(root)
        for (bad in listOf(emptyList(), List(513) { apk("main-disguised").path })) {
            assertThrows(Exception::class.java) { store.publish(source("main-disguised").copy(apkPaths = bad), 34) }
            assertEquals(setOf(valid.id), children())
        }
    }
}
