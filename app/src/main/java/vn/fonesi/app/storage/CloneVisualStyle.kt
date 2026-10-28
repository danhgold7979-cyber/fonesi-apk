package vn.fonesi.app.storage

/** Presentation metadata only; never used as an engine identity, access token or package path. */
data class CloneVisualStyle(val accentKey: String = "teal", val badgeText: String? = null)

object CloneStylePolicy {
    val accentKeys: List<String> = listOf("teal", "blue", "purple", "amber", "red", "slate")

    fun normalize(accentKey: String, badgeText: String?): CloneVisualStyle {
        require(accentKey in accentKeys) { "Unsupported clone accent" }
        val badge = badgeText?.trim()?.takeIf { it.isNotEmpty() }
        if (badge != null) {
            require(badge.toByteArray(Charsets.UTF_8).size <= 16)
            require(badge.codePointCount(0, badge.length) in 1..2)
            val points = badge.codePoints().toArray()
            require(points.all { point ->
                !Character.isISOControl(point) &&
                    Character.getType(point) !in setOf(
                        Character.FORMAT.toInt(), Character.UNASSIGNED.toInt(),
                        Character.PRIVATE_USE.toInt(), Character.SURROGATE.toInt(),
                        Character.LINE_SEPARATOR.toInt(), Character.PARAGRAPH_SEPARATOR.toInt(),
                    )
            }) { "Unsafe badge characters" }
        }
        return CloneVisualStyle(accentKey, badge)
    }

    fun displayBadge(style: CloneVisualStyle): String =
        normalize(style.accentKey, style.badgeText).badgeText ?: "F"
}
