package vn.fonesi.payload;

import org.junit.Test;
import static org.junit.Assert.*;

public class SyntheticApplicationProcessMarkerTest {
    private static final String FIRST = "11111111-1111-4111-8111-111111111111";
    private static final String SECOND = "22222222-2222-4222-8222-222222222222";
    @Test public void freshScopeStartsAtOne() {
        SyntheticApplicationProcessMarker.State state = SyntheticApplicationProcessMarker.advance(0, -1, null, 10101, FIRST);
        assertEquals(1, state.getStarts()); assertEquals(10101, state.getPid()); assertEquals(FIRST, state.getProcessToken());
    }
    @Test public void duplicateOnCreateInSameProcessIsRejected() {
        assertThrows(IllegalStateException.class, () -> SyntheticApplicationProcessMarker.advance(1, 10101, FIRST, 10101, FIRST));
    }
    @Test public void newProcessAdvancesPersistedCountOnce() {
        SyntheticApplicationProcessMarker.State state = SyntheticApplicationProcessMarker.advance(1, 10101, FIRST, 10102, SECOND);
        assertEquals(2, state.getStarts()); assertEquals(10102, state.getPid()); assertEquals(SECOND, state.getProcessToken());
    }
    @Test public void pidReuseWithNewProcessTokenIsNotMistakenForDuplicate() {
        SyntheticApplicationProcessMarker.State state = SyntheticApplicationProcessMarker.advance(1, 10101, FIRST, 10101, SECOND);
        assertEquals(2, state.getStarts()); assertEquals(SECOND, state.getProcessToken());
    }
    @Test public void partialPersistedStateIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SyntheticApplicationProcessMarker.advance(0, 10101, FIRST, 10102, SECOND));
        assertThrows(IllegalArgumentException.class, () -> SyntheticApplicationProcessMarker.advance(1, -1, null, 10102, SECOND));
    }
    @Test public void invalidCurrentIdentityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SyntheticApplicationProcessMarker.advance(0, -1, null, 0, FIRST));
        assertThrows(IllegalArgumentException.class, () -> SyntheticApplicationProcessMarker.advance(0, -1, null, 10102, "not-a-uuid"));
    }
    @Test public void negativeAndOverflowCountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SyntheticApplicationProcessMarker.advance(-1, -1, null, 10102, SECOND));
        assertThrows(IllegalArgumentException.class, () -> SyntheticApplicationProcessMarker.advance(Integer.MAX_VALUE, 10101, FIRST, 10102, SECOND));
    }
}
