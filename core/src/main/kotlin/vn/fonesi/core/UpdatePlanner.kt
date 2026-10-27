package vn.fonesi.core

data class UpdateTarget(val source: InstalledSource, val affectedCloneIds: List<String>)

enum class UpdateBlockReason {
    ENGINE_UNAVAILABLE, UNKNOWN_CLONE, SOURCE_MISSING, SOURCE_INVALID,
    SIGNER_REVIEW_REQUIRED, NEWER_CLONE_VERSION, IN_USE_OR_UNKNOWN,
    SOURCE_UNSUPPORTED,
    INCOMPLETE_GROUP,
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
    private data class Decision(val source: InstalledSource?, val affected: List<CloneRecord>, val reason: UpdateBlockReason?, val current: Boolean, val busy: Boolean)
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
        val groups = if (engine.updateScope == UpdateScope.SHARED_PACKAGE) allClones.groupBy { it.packageName } else emptyMap()
        val decisions = mutableMapOf<String, Decision>()
        fun decide(packageName: String, affected: List<CloneRecord>): Decision {
            val source = sources[packageName]
            val reason = when {
                source == null -> UpdateBlockReason.SOURCE_MISSING
                source.packageName != packageName || source.versionCode < 0 || source.apkPaths.isEmpty() ||
                    source.apkPaths.any { it.isBlank() } || source.signerDigests.isEmpty() -> UpdateBlockReason.SOURCE_INVALID
                affected.any { it.signerDigests != source.signerDigests } -> UpdateBlockReason.SIGNER_REVIEW_REQUIRED
                affected.any { it.versionCode > source.versionCode } -> UpdateBlockReason.NEWER_CLONE_VERSION
                source.apkPaths.size > 1 && !engine.importsSplitApks -> UpdateBlockReason.SOURCE_UNSUPPORTED
                else -> null
            }
            val current = reason == null && affected.all { it.versionCode == source!!.versionCode }
            val busy = reason == null && !current && affected.any { it.running != false }
            return Decision(source, affected, reason, current, busy)
        }
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
            val decision = if (engine.updateScope == UpdateScope.SHARED_PACKAGE) decisions.getOrPut(clone.packageName) {
                decide(clone.packageName, groups.getValue(clone.packageName))
            } else decide(clone.packageName, listOf(clone))
            if (decision.reason != null) {
                blocked += UpdateBlock(id, decision.reason)
                continue
            }
            val source = checkNotNull(decision.source)
            if (decision.current) {
                current += id
                continue
            }
            if (decision.busy) {
                blocked += UpdateBlock(id, UpdateBlockReason.IN_USE_OR_UNKNOWN)
                continue
            }
            if (engine.updateScope == UpdateScope.SHARED_PACKAGE &&
                !scheduledPackages.add(clone.packageName)) continue
            targets += UpdateTarget(source, decision.affected.map { it.id })
        }
        return UpdatePlan(targets, current, blocked)
    }
}
