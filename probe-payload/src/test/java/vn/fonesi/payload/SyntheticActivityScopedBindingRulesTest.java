package vn.fonesi.payload;

import static org.junit.Assert.*;
import org.junit.Test;

/** E81: fail-closed immutable UUID binding without an Android Activity fixture. */
public final class SyntheticActivityScopedBindingRulesTest {
    private static final String A = "11111111-1111-4111-8111-111111111111";
    private static final String B = "22222222-2222-4222-8222-222222222222";

    @Test public void freshCanonicalScopeAcceptedOnce() {
        assertEquals(A, SyntheticActivityScopedBindingRules.nextBoundScope(null, A));
    }
    @Test public void sameScopeRebindingFailsClosed() {
        assertThrows(IllegalStateException.class, () ->
            SyntheticActivityScopedBindingRules.nextBoundScope(A, A));
    }
    @Test public void differentScopeRebindingFailsClosed() {
        assertThrows(IllegalStateException.class, () ->
            SyntheticActivityScopedBindingRules.nextBoundScope(A, B));
    }
    @Test public void invalidOrNoncanonicalScopeFailsClosed() {
        for (String input : new String[] {"", "not-a-uuid", A.toUpperCase(), A + "-suffix"}) {
            assertThrows(IllegalArgumentException.class, () ->
                SyntheticActivityScopedBindingRules.nextBoundScope(null, input));
        }
        assertThrows(IllegalArgumentException.class, () ->
            SyntheticActivityScopedBindingRules.nextBoundScope(null, null));
    }
}
