package vn.fonesi.app.storage

import vn.fonesi.apk.ClusterOwnerType
import vn.fonesi.core.update.UpdateTask

/**
 * Refuse original-app source fallback for both E44-marked tasks and pre-E44 legacy tasks with
 * E41 owner references. Read-only; an inconsistent/corrupt Room registry must fail closed.
 */
internal class RoomLegacyUpdateSourcePolicy(private val db: FonesiDatabase) {
    fun allows(task: UpdateTask): Boolean =
        !task.retainedSource &&
            db.clusterReferences().forOwner(
                ClusterOwnerType.UPDATE_TASK.name, task.id, task.engineKey).isEmpty() &&
            db.clusterReferences().forOwner(
                ClusterOwnerType.REVIEW_REQUIRED.name, task.id, task.engineKey).isEmpty()
}
