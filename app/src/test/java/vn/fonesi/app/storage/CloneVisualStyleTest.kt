package vn.fonesi.app.storage

import org.junit.Assert.*
import org.junit.Test

class CloneVisualStyleTest {
    @Test fun allSixPresetsAreRecognizedAndDefaultBadgeIsF() {
        assertEquals(6, CloneStylePolicy.accentKeys.distinct().size)
        for (key in CloneStylePolicy.accentKeys) {
            val style = CloneStylePolicy.normalize(key, null)
            assertEquals(key, style.accentKey)
            assertEquals("F", CloneStylePolicy.displayBadge(style))
        }
    }

    @Test fun unicodeBadgeIsTrimmedBoundedAndPreserved() {
        assertEquals("A", CloneStylePolicy.normalize("teal", "  A  ").badgeText)
        assertEquals("OK", CloneStylePolicy.normalize("red", "OK").badgeText)
        assertEquals("🛒", CloneStylePolicy.normalize("blue", "🛒").badgeText)
        assertNull(CloneStylePolicy.normalize("slate", "").badgeText)
        assertNull(CloneStylePolicy.normalize("slate", "  ").badgeText)
    }

    @Test fun unsafeOrExcessiveStyleNeverPersists() {
        val invalid = listOf("A\nB", "A\rB", "\u202E", "ABC", "\u200D", "\u0000", "あいう")
        for (badge in invalid) {
            assertThrows(IllegalArgumentException::class.java) {
                CloneStylePolicy.normalize("teal", badge)
            }
        }
        for (key in listOf("unsafe", "", "TEAL", "teal ", "../../red")) {
            assertThrows(IllegalArgumentException::class.java) {
                CloneStylePolicy.normalize(key, "A")
            }
        }
    }

    @Test fun emojiWithTwoCodePointsIsAllowedWithinByteLimit() {
        assertEquals("💙", CloneStylePolicy.displayBadge(CloneStylePolicy.normalize("purple", "💙")))
    }
}
