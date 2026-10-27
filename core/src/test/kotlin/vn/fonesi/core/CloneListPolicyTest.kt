package vn.fonesi.core

import org.junit.Assert.*
import org.junit.Test

class CloneListPolicyTest {
    private val rows = listOf(
        CloneListItem("a", "test.social", "Tài khoản Đỏ", "Cửa hàng", CloneStatusFilter.RUNNING),
        CloneListItem("b", "test.social", "Khác", null, CloneStatusFilter.UNKNOWN),
        CloneListItem("c", "other.package", "Shop", "Cửa hàng", CloneStatusFilter.STOPPED),
    )
    @Test fun vietnameseAccentsCaseAndCanonicalCombiningFormsMatch() {
        assertEquals(listOf("a"), CloneListPolicy.filter(rows, "TAI KHOAN DO", null, CloneStatusFilter.ALL).map { it.token })
        assertEquals(CloneListPolicy.fold("Đỏ"), CloneListPolicy.fold("Đo\u0309"))
    }
    @Test fun packageAndGroupSearchUseTheSameNormalization() {
        assertEquals(2, CloneListPolicy.filter(rows, "cua hang", null, CloneStatusFilter.ALL).size)
        assertEquals(listOf("c"), CloneListPolicy.filter(rows, "other.package", null, CloneStatusFilter.ALL).map { it.token })
    }
    @Test fun ungroupedAndUnknownRemainDifferentFromAllAndStopped() {
        assertEquals(listOf("b"), CloneListPolicy.filter(rows, "", "", CloneStatusFilter.ALL).map { it.token })
        assertEquals(listOf("c"), CloneListPolicy.filter(rows, "", "Cửa hàng", CloneStatusFilter.STOPPED).map { it.token })
        assertEquals(listOf("b"), CloneListPolicy.filter(rows, "", null, CloneStatusFilter.UNKNOWN).map { it.token })
    }
    @Test fun selectingVisibleDoesNotSelectHiddenOrIneligibleRows() {
        assertEquals(setOf("a"), CloneListPolicy.selectVisible(emptySet(), setOf("a", "old-engine"), setOf("a", "b")))
    }
    @Test fun previouslySelectedHiddenRowsAreRetainedUntilExplicitClearOrRemoval() {
        assertEquals(setOf("a", "b"), CloneListPolicy.selectVisible(setOf("b"), setOf("a"), setOf("a", "b", "c")))
        assertEquals(setOf("b"), CloneListPolicy.retain(setOf("b", "deleted"), setOf("a", "b")))
    }
    @Test fun toggleNeverRevivesStaleTokensOrAnotherEnginesInstance() {
        assertTrue(CloneListPolicy.toggle(emptySet(), "old", setOf("a")).isEmpty())
        assertTrue(CloneListPolicy.toggle(setOf("a"), "a", setOf("a")).isEmpty())
    }
}
