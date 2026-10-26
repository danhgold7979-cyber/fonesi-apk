package vn.fonesi.core

data class UpdateTarget(val source: InstalledSource, val affectedCloneIds: List<String>)

enum class UpdateBlockReason {
    ENGINE_UNAVAILABLE, UNKNOWN_CLONE, SOURCE_MISSING, SOURCE_INVALID,
    SIGNER_REVIEW_REQUIRED, NEWER_CLONE_VERSION, IN_USE_OR_UNKNOWN,
}

data class UpdateBlock(val requestedCloneId: String, val reason: UpdateBlockReason)

data class UpdatePlan(
    val targets: List<UpdateTarget>,
    val alreadyCurrent: List<String>,
    val blocked: List<UpdateBlock>,
)

/**
 * Pure planning only. It neither installs packages nor reports an update as completed.
 * A future executor must revalidate the source and running state immediately before updating.
 */
object UpdatePlanner {
    fun plan(
        requestedIds: List<String>,
        allClones: List<CloneRecord>,
        sources: Map<String, InstalledSource>,
        engine: EngineAvailability,
    ): UpdatePlan {
        require(allClones.map { it.id }.distinct().size == allClones.size) { "Duplicate clone IDs" }
        val requests = requestedIds.distinct()
        if (engine !is EngineAvailability.Ready) {
            return UpdatePlan(emptyList(), emptyList(), requests.map {
                UpdateBlock(it, UpdateBlockReason.ENGINE_UNAVAILABLE)
            })
        }
        val byId = allClones.associateBy { it.id }
        val targets = mutableListOf<UpdateTarget>()
        val current = mutableListOf<String>()
        val blocked = mutableListOf<UpdateBlock>()
        val scheduledPackages = mutableSetOf<String>()
        for (id in requests) {
            val clone = byId[id]
            if (clone == null) {
                blocked += UpdateBlock(id, UpdateBlockReason.UNKNOWN_CLONE)
                continue
            }
            val source = sources[clone.packageName]
            val affected = if (engine.updateScope == UpdateScope.SHARED_PACKAGE) {
                allClones.filter { it.packageName == clone.packageName }
            } else listOf(clone)
            val reason = when {
                source == null -> UpdateBlockReason.SOURCE_MISSING
                source.packageName != clone.packageName || source.versionCode < 0 ||
                    source.apkPaths.isEmpty() || source.apkPaths.any { it.isBlank() } ||
                    source.signerDigests.isEmpty() -> UpdateBlockReason.SOURCE_INVALID
                affected.any { it.signerDigests != source.signerDigests } ->
                    UpdateBlockReason.SIGNER_REVIEW_REQUIRED
                affected.any { it.versionCode > source.versionCode } ->
                    UpdateBlockReason.NEWER_CLONE_VERSION
                else -> null
            }
            if (reason != null) {
                blocked += UpdateBlock(id, reason)
                continue
            }
            checkNotNull(source)
            if (affected.all { it.versionCode == source.versionCode }) {
                current += id
                continue
            }
            if (affected.any { it.running != false }) {
                blocked += UpdateBlock(id, UpdateBlockReason.IN_USE_OR_UNKNOWN)
                continue
            }
            if (engine.updateScope == UpdateScope.SHARED_PACKAGE &&
                !scheduledPackages.add(clone.packageName)) continue
            targets += UpdateTarget(source, affected.map { it.id })
        }
        return UpdatePlan(targets, current, blocked)
    }
}
