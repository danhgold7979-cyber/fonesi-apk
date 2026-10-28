package vn.fonesi.engine

import vn.fonesi.apk.AuditedApk
import java.io.File
import java.util.PriorityQueue

/**
 * Private APK dependency layout only. This is NOT a loaded ClassLoader, Android Resources,
 * Activity/Application registry, split-capable provider or proof of running a guest app.
 */
internal data class GuestSplitLoaderNode(
    val id: String,
    val parentId: String?,
    val apks: List<File>,
    val isFeature: Boolean,
)

/**
 * Root-first deterministic plan. For isolated splits, each feature's parent is exactly one
 * verified feature or the private base. Config APKs stay with their target loader node.
 */
internal data class GuestSplitLoaderGraph(
    val isolated: Boolean,
    val nodes: List<GuestSplitLoaderNode>,
) {
    val allApks: List<File> get() = nodes.flatMap { it.apks }
    fun node(id: String) = nodes.single { it.id == id }
}

/**
 * Converts a cryptographically audited GuestCodeLayout into an isolated feature-parent DAG.
 * Deliberately DOES NOT remove GuestCodeLoader's isolatedSplits block. Android dynamic resource
 * loaders/PackageManager/Binder and arbitrary guest components remain unsupported.
 */
internal object GuestSplitGraphPlanner {
    const val BASE = "__base__"

    fun plan(layout: GuestCodeLayout): GuestSplitLoaderGraph {
        val cluster = layout.cluster.apks
        val all = cluster.splits
        val members = listOf(cluster.base.file) + all.map { it.file }
        require(members.distinct().size == members.size) { "Ambiguous APK member paths" }
        require(layout.base == cluster.base.file && layout.splits.toSet() == all.map { it.file }.toSet()) {
            "Private APK layout does not contain exactly its verified cluster"
        }
        require(layout.splitNames.toSet() == all.map { checkNotNull(it.splitName) }.toSet()) {
            "Split names and source paths diverge"
        }

        // Non-isolated APKs have a single flat code namespace; do not invent parent loaders.
        if (!cluster.base.isolatedSplits) {
            return GuestSplitLoaderGraph(false, listOf(GuestSplitLoaderNode(
                BASE, null, layout.allApks.toList(), false
            )))
        }

        val features = all.filter { it.feature }
        val configs = all.filterNot { it.feature }
        val featureByName = features.associateBy { checkNotNull(it.splitName) }
        require(featureByName.size == features.size) { "Duplicate feature split identity" }

        // Android config splits are scoped to their declared feature or the base package.
        // Unknown targets and multi-parent code dependencies are unsupported, not flattened.
        fun target(config: AuditedApk): String {
            val parent = config.configFor?.takeIf { it.isNotEmpty() } ?: BASE
            require(parent == BASE || featureByName.containsKey(parent)) {
                "Configuration split has no verified feature target"
            }
            return parent
        }
        val configsByTarget = configs.groupBy(::target)
        val dependencies = features.associate { feature ->
            val name = checkNotNull(feature.splitName)
            require(feature.dependencies.size <= 1) {
                "Multiple feature classloader parents are not supported"
            }
            val parent = feature.dependencies.singleOrNull() ?: BASE
            require(parent == BASE || featureByName.containsKey(parent)) {
                "Feature classloader parent is missing"
            }
            require(parent != name) { "Feature cannot depend on its own classloader" }
            name to parent
        }

        // Topological sort, with deterministic lexicographic tiebreaks. Avoid recursion into
        // untrusted dependency depth, although the signed-ApkSetAuditor already rejects cycles.
        val children = mutableMapOf<String, MutableList<String>>()
        val waiting = dependencies.keys.toMutableSet()
        for ((name, parent) in dependencies) children.getOrPut(parent) { mutableListOf() }.add(name)
        val ready = PriorityQueue<String>()
        children[BASE].orEmpty().forEach(ready::add)
        val order = mutableListOf<String>()
        while (ready.isNotEmpty()) {
            val current = ready.remove()
            require(waiting.remove(current)) { "Duplicate/cyclic classloader dependency" }
            order += current
            children[current].orEmpty().forEach(ready::add)
        }
        require(waiting.isEmpty()) { "Split classloader graph contains a cycle or unreachable feature" }

        val nodes = mutableListOf(GuestSplitLoaderNode(
            BASE, null,
            listOf(cluster.base.file) + configsByTarget[BASE].orEmpty()
                .sortedBy { it.splitName }.map { it.file },
            false
        ))
        for (name in order) {
            val feature = featureByName.getValue(name)
            val files = listOf(feature.file) + configsByTarget[name].orEmpty()
                .sortedBy { it.splitName }.map { it.file }
            nodes += GuestSplitLoaderNode(name, dependencies.getValue(name), files, true)
        }

        val graph = GuestSplitLoaderGraph(true, nodes)
        require(graph.allApks.size == members.size && graph.allApks.toSet() == members.toSet()) {
            "Every signed private base/feature/config APK must occur exactly once"
        }
        require(graph.nodes.map { it.id }.distinct().size == graph.nodes.size)
        return graph
    }
}
