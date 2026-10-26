package vn.fonesi.app.storage

import org.json.JSONArray
import org.json.JSONObject
import vn.fonesi.core.UpdateScope
import vn.fonesi.core.update.*

internal object TaskCodec {
    fun encode(task: UpdateTask): String = JSONObject().apply {
        put("format", 1); put("id", task.id); put("engineKey", task.engineKey)
        put("packageName", task.packageName); put("cloneIds", JSONArray(task.cloneIds))
        put("versionCode", task.versionCode); put("signers", JSONArray(task.signerDigests.sorted()))
        put("scope", task.scope.name); put("dedupKey", task.dedupKey); put("state", task.state.name)
        put("revision", task.revision); put("affectedIds", JSONArray(task.affectedIds))
        put("snapshot", task.snapshotFingerprint ?: JSONObject.NULL)
        put("issue", task.issue?.name ?: JSONObject.NULL)
        put("completion", task.completion?.name ?: JSONObject.NULL)
        put("createdAt", task.createdAt); put("changedAt", task.changedAt)
    }.toString()

    fun decode(row: UpdateTaskRow): UpdateTask {
        val json = JSONObject(row.payload)
        require(json.getInt("format") == 1) { "Unsupported task format; preserving database" }
        fun strings(key: String): List<String> = json.getJSONArray(key).let { array ->
            (0 until array.length()).map { array.getString(it) }
        }
        fun optional(key: String): String? = if (json.isNull(key)) null else json.getString(key)
        val task = UpdateTask(
            id = json.getString("id"), engineKey = json.getString("engineKey"),
            packageName = json.getString("packageName"), cloneIds = strings("cloneIds"),
            versionCode = json.getLong("versionCode"), signerDigests = strings("signers").toSet(),
            scope = UpdateScope.valueOf(json.getString("scope")), dedupKey = json.getString("dedupKey"),
            state = TaskState.valueOf(json.getString("state")), revision = json.getLong("revision"),
            affectedIds = strings("affectedIds"), snapshotFingerprint = optional("snapshot"),
            issue = optional("issue")?.let(TaskIssue::valueOf),
            completion = optional("completion")?.let(CompletionKind::valueOf),
            createdAt = json.getLong("createdAt"), changedAt = json.getLong("changedAt"),
        )
        require(task.id == row.taskId && task.packageName == row.packageName && task.revision == row.revision && task.state.name == row.state)
        require(row.activeKey == if (task.state.terminal) null else task.dedupKey)
        return task
    }

    fun row(task: UpdateTask) = UpdateTaskRow(taskId = task.id, packageName = task.packageName,
        state = task.state.name, revision = task.revision, activeKey = task.dedupKey.takeUnless { task.state.terminal }, payload = encode(task))
}
