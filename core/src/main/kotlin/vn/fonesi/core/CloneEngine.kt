package vn.fonesi.core

/** This contract does not implement virtualization. No production provider is integrated yet. */
interface CloneEngine {
    val availability: EngineAvailability
    fun listClones(): List<CloneRecord>
    fun create(source: InstalledSource, label: String): CloneRecord
    /**
     * Dispatch only this instance. IDs must remain stable for its lifetime and must not be recycled
     * for another instance while metadata exists. On return, a scheduled start must already report
     * running=true or unknown, so a serialized updater cannot mistake it for an idle instance.
     * A return is not proof that the guest UI or login succeeded.
     */
    fun launch(cloneId: String)
    fun delete(cloneId: String)
    /** Must keep account data and report the actual version after applying the source. */
    fun update(target: UpdateTarget): List<CloneRecord>
}

sealed interface EngineAvailability {
    data class Ready(val updateScope: UpdateScope, val importsSplitApks: Boolean = false) : EngineAvailability
    data class Unavailable(val reason: String) : EngineAvailability
}

enum class UpdateScope { PER_CLONE, SHARED_PACKAGE }

data class InstalledSource(
    val packageName: String,
    val versionCode: Long,
    val signerDigests: Set<String>,
    val apkPaths: List<String>,
)

data class CloneRecord(
    val id: String,
    val packageName: String,
    val versionCode: Long,
    val signerDigests: Set<String>,
    /** Null means unknown: never assume it is safe to update. */
    val running: Boolean?,
)

class EngineUnavailableException(message: String) : IllegalStateException(message)

class UnavailableCloneEngine : CloneEngine {
    override val availability = EngineAvailability.Unavailable(
        "Chưa tích hợp bộ máy clone đã được kiểm chứng.",
    )
    override fun listClones(): List<CloneRecord> = emptyList()
    private fun unavailable(): Nothing = throw EngineUnavailableException(availability.reason)
    override fun create(source: InstalledSource, label: String): CloneRecord = unavailable()
    override fun launch(cloneId: String): Unit = unavailable()
    override fun delete(cloneId: String): Unit = unavailable()
    override fun update(target: UpdateTarget): List<CloneRecord> = unavailable()
}
