package vn.fonesi.core

import java.text.Normalizer
import java.util.Locale

enum class CloneStatusFilter { ALL, RUNNING, STOPPED, UNKNOWN }
data class CloneListItem(val token: String, val packageName: String, val label: String, val group: String?, val status: CloneStatusFilter)

object CloneListPolicy {
    private val marks = setOf(Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt())
    fun fold(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .filterNot { Character.getType(it) in marks }
        .lowercase(Locale.ROOT).replace('đ', 'd')

    /** null = all groups; empty = ungrouped. Stored group names are nonempty after normalization. */
    fun filter(items: List<CloneListItem>, query: String, group: String?, status: CloneStatusFilter): List<CloneListItem> {
        val needle = fold(query.trim())
        return items.filter { row ->
            (group == null || if (group.isEmpty()) row.group == null else row.group == group) &&
                (status == CloneStatusFilter.ALL || row.status == status) &&
                (needle.isEmpty() || listOf(row.label, row.packageName, row.group.orEmpty()).any { fold(it).contains(needle) })
        }
    }
    fun retain(selected: Set<String>, eligible: Set<String>): Set<String> = selected intersect eligible
    fun selectVisible(selected: Set<String>, visible: Set<String>, eligible: Set<String>): Set<String> = (selected + visible) intersect eligible
    fun toggle(selected: Set<String>, token: String, eligible: Set<String>): Set<String> =
        (if (token in selected) selected - token else selected + token) intersect eligible
}
