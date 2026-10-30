package vn.fonesi.payload;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-owned PRIVATE Activity constructor marker. This is not an Android guest lifecycle or
 * an account/UID security boundary. A second construction in the same process fails closed.
 */
public final class SyntheticActivityConstructionProbe {
    private static final AtomicInteger CONSTRUCTED = new AtomicInteger();

    private SyntheticActivityConstructionProbe() {}

    /** Pure fail-closed transition used by JVM tests without creating an Android Activity. */
    public static int nextCount(int current) {
        if (current != 0) {
            throw new IllegalStateException("Synthetic PRIVATE Activity constructor repeated or marker invalid");
        }
        return 1;
    }

    /** Invoked only by the constructor of the independently signed synthetic PayloadActivity. */
    public static void recordConstructor() {
        CONSTRUCTED.updateAndGet(SyntheticActivityConstructionProbe::nextCount);
    }

    public static int constructedCount() {
        return CONSTRUCTED.get();
    }
}
