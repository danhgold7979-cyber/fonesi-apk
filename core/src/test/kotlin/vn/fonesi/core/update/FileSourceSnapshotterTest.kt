package vn.fonesi.core.update

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.fonesi.core.InstalledSource
import java.nio.file.Files
import java.util.UUID

class FileSourceSnapshotterTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `base and split are copied byte for byte without touching originals`() {
        val root = temporary.newFolder().toPath()
        val base = root.resolve("base.apk"); val split = root.resolve("split_config.apk")
        Files.writeString(base, "base bytes"); Files.writeString(split, "split bytes")
        val source = InstalledSource("test.app", 7, setOf("signer"), listOf(base.toString(), split.toString()))
        val snapshotter = FileSourceSnapshotter(root.resolve("spool"), SourceCatalog { source })
        val id = UUID.randomUUID().toString()
        val snapshot = snapshotter.prepare(id, source)
        assertTrue(snapshotter.verify(snapshot))
        assertEquals(listOf("base bytes", "split bytes"), snapshot.source.apkPaths.map { Files.readString(java.nio.file.Paths.get(it)) })
        Files.writeString(base, "new original bytes")
        assertTrue(snapshotter.verify(snapshot))
        assertEquals("new original bytes", Files.readString(base))
        snapshotter.discard(id)
        assertTrue(Files.exists(base)); assertTrue(Files.exists(split))
    }

    @Test fun `source replacement while copying is rejected and temporary copies removed`() {
        val root = temporary.newFolder().toPath(); val base = root.resolve("base.apk")
        Files.writeString(base, "synthetic bytes")
        val source = InstalledSource("test.app", 7, setOf("signer"), listOf(base.toString()))
        var calls = 0
        val spool = root.resolve("spool")
        val snapshotter = FileSourceSnapshotter(spool, SourceCatalog { if (++calls == 1) source else source.copy(versionCode = 8) })
        assertThrows(SourceChangedException::class.java) { snapshotter.prepare(UUID.randomUUID().toString(), source) }
        Files.list(spool).use { assertEquals(0L, it.count()) }
        assertEquals("synthetic bytes", Files.readString(base))
    }

    @Test fun `changed prepared bytes fail integrity verification`() {
        val root = temporary.newFolder().toPath(); val file = root.resolve("base.apk")
        Files.writeString(file, "original")
        val source = InstalledSource("test.app", 1, setOf("signer"), listOf(file.toString()))
        val snapshotter = FileSourceSnapshotter(root.resolve("spool"), SourceCatalog { source })
        val snapshot = snapshotter.prepare(UUID.randomUUID().toString(), source)
        val copied = java.nio.file.Paths.get(snapshot.source.apkPaths.single())
        assertTrue(copied.toFile().setWritable(true))
        Files.writeString(copied, "modified")
        assertFalse(snapshotter.verify(snapshot))
    }

    @Test fun `symlink inputs and path traversal task IDs cannot reach outside the spool`() {
        val root = temporary.newFolder().toPath(); val real = root.resolve("real.apk")
        Files.writeString(real, "owned data")
        val symlink = root.resolve("base.apk"); Files.createSymbolicLink(symlink, real)
        val source = InstalledSource("test.app", 1, setOf("signer"), listOf(symlink.toString()))
        val snapshotter = FileSourceSnapshotter(root.resolve("spool"), SourceCatalog { source })
        assertThrows(IllegalArgumentException::class.java) { snapshotter.prepare(UUID.randomUUID().toString(), source) }
        assertThrows(IllegalArgumentException::class.java) { snapshotter.discard("../real.apk") }
        assertEquals("owned data", Files.readString(real))
    }

    @Test fun `an empty package cannot be prepared for import`() {
        val root = temporary.newFolder().toPath(); val base = root.resolve("base.apk")
        Files.createFile(base)
        val source = InstalledSource("test.app", 1, setOf("signer"), listOf(base.toString()))
        val snapshotter = FileSourceSnapshotter(root.resolve("spool"), SourceCatalog { source })
        assertThrows(IllegalStateException::class.java) { snapshotter.prepare(UUID.randomUUID().toString(), source) }
        assertEquals(0L, Files.size(base))
    }
}
