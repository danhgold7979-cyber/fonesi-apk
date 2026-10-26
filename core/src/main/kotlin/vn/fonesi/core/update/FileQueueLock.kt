package vn.fonesi.core.update

import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** An OS lock, shared by all runners using this queue; not just an in-memory mutex. */
class FileQueueLock(private val path: Path) : QueueLock {
    override fun acquire(): AutoCloseable? {
        Files.createDirectories(path.toAbsolutePath().parent)
        val channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        val lock = try {
            channel.tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        } catch (error: Exception) {
            channel.close()
            throw error
        }
        if (lock == null) {
            channel.close()
            return null
        }
        return AutoCloseable { try { lock.release() } finally { channel.close() } }
    }
}
