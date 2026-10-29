package vn.fonesi.core.update

import vn.fonesi.core.UpdateScope
import vn.fonesi.core.UpdateTarget
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.UUID

enum class TaskState {
    QUEUED, PREPARING, APPLYING, VERIFYING, DEFERRED, REVIEW_REQUIRED, SUCCEEDED, FAILED, CANCELLED;

    val terminal: Boolean get() = this in setOf(SUCCEEDED, FAILED, CANCELLED)

    fun canMoveTo(next: TaskState): Boolean = when (this) {
        QUEUED -> next in setOf(PREPARING, CANCELLED)
        PREPARING -> next in setOf(QUEUED, APPLYING, DEFERRED, FAILED, SUCCEEDED)
        APPLYING -> next in setOf(VERIFYING, REVIEW_REQUIRED)
        VERIFYING -> next in setOf(SUCCEEDED, REVIEW_REQUIRED)
        DEFERRED -> next in setOf(QUEUED, CANCELLED)
        REVIEW_REQUIRED -> next == SUCCEEDED
        else -> false
    }
}

enum class TaskIssue {
    PAUSED, ENGINE_CHANGED, ENGINE_UNAVAILABLE, SOURCE_MISSING, SOURCE_CHANGED,
    SOURCE_PREPARATION_FAILED, UNSAFE_TARGET, CLONE_BUSY, SCOPE_CHANGED,
    INTERRUPTED, ENGINE_OUTCOME_UNKNOWN, VERSION_NOT_VERIFIED,
    EXECUTION_POLICY, SUPERSEDED_SOURCE,
}

enum class CompletionKind { APPLIED_AND_VERIFIED, ALREADY_CURRENT, RECOVERED_VERSION_VERIFIED }

data class UpdateTask(
    val id: String,
    val engineKey: String,
    val packageName: String,
    val cloneIds: List<String>,
    val versionCode: Long,
    val signerDigests: Set<String>,
    val scope: UpdateScope,
    val dedupKey: String,
    val state: TaskState = TaskState.QUEUED,
    val revision: Long = 0,
    val affectedIds: List<String> = emptyList(),
    val snapshotFingerprint: String? = null,
    val issue: TaskIssue? = null,
    val completion: CompletionKind? = null,
    val createdAt: Long,
    val changedAt: Long = createdAt,
    /** Durable no-fallback source authority marker, not permission to launch guest code. */
    val retainedSource: Boolean = false,
) {
    fun move(
        state: TaskState,
        now: Long,
        issue: TaskIssue? = null,
        completion: CompletionKind? = null,
        affectedIds: List<String> = this.affectedIds,
        fingerprint: String? = snapshotFingerprint,
    ): UpdateTask {
        require(this.state.canMoveTo(state)) { "Invalid update task transition" }
        require((state == TaskState.SUCCEEDED) == (completion != null))
        if (state == TaskState.APPLYING) require(affectedIds.isNotEmpty() && !fingerprint.isNullOrBlank())
        return copy(
        state = state, revision = revision + 1, changedAt = now, issue = issue,
        completion = completion, affectedIds = affectedIds, snapshotFingerprint = fingerprint,
        )
    }

    fun requireSuccessorOf(previous: UpdateTask) {
        require(previous.state.canMoveTo(state) && revision == previous.revision + 1)
        require(id == previous.id && engineKey == previous.engineKey && packageName == previous.packageName &&
            cloneIds == previous.cloneIds && versionCode == previous.versionCode && signerDigests == previous.signerDigests &&
            scope == previous.scope && dedupKey == previous.dedupKey && createdAt == previous.createdAt &&
            retainedSource == previous.retainedSource)
        require((state == TaskState.SUCCEEDED) == (completion != null))
        if (state == TaskState.APPLYING) require(affectedIds.isNotEmpty() && !snapshotFingerprint.isNullOrBlank())
    }

    companion object {
        fun create(engineKey: String, target: UpdateTarget, scope: UpdateScope, now: Long): UpdateTask {
            require(engineKey.isNotBlank())
            require(target.source.packageName.isNotBlank() && target.source.versionCode >= 0)
            require(target.source.signerDigests.isNotEmpty() && target.source.signerDigests.none { it.isBlank() })
            require(target.affectedCloneIds.isNotEmpty() && target.affectedCloneIds.none { it.isBlank() })
            val ids = target.affectedCloneIds.distinct().sorted()
            require(scope == UpdateScope.SHARED_PACKAGE || ids.size == 1)
            val key = fingerprintFields(buildList {
                add(engineKey); add(target.source.packageName); add(scope.name)
                add(target.source.versionCode.toString())
                add(target.source.signerDigests.size.toString()); addAll(target.source.signerDigests.sorted())
                add(ids.size.toString()); addAll(ids)
            })
            return UpdateTask(UUID.randomUUID().toString(), engineKey, target.source.packageName, ids,
                target.source.versionCode, target.source.signerDigests, scope, key, createdAt = now)
        }
    }
}

internal fun fingerprintFields(values: List<String>): String {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use { out ->
        values.forEach { value ->
            val encoded = value.toByteArray(Charsets.UTF_8)
            out.writeInt(encoded.size)
            out.write(encoded)
        }
    }
    return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
