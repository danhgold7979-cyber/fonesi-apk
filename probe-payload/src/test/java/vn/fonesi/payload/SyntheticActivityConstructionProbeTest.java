package vn.fonesi.payload;

import static org.junit.Assert.*;
import org.junit.Test;

/** E80: pure synthetic constructor counter and duplicate denial without guest Activity. */
public final class SyntheticActivityConstructionProbeTest {
    @Test public void cleanPrivateConstructorTransitionsExactlyOnce() {
        assertEquals(1, SyntheticActivityConstructionProbe.nextCount(0));
    }

    @Test public void duplicateConstructorIsRejected() {
        assertThrows(IllegalStateException.class,
            () -> SyntheticActivityConstructionProbe.nextCount(1));
    }

    @Test public void negativeOrOverflowMarkerIsRejected() {
        assertThrows(IllegalStateException.class,
            () -> SyntheticActivityConstructionProbe.nextCount(-1));
        assertThrows(IllegalStateException.class,
            () -> SyntheticActivityConstructionProbe.nextCount(Integer.MAX_VALUE));
    }
}
