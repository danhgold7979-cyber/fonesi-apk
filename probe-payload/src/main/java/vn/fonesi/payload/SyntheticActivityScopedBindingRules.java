package vn.fonesi.payload;

import java.util.UUID;

/** E81 test-only immutable scope binding, independent of Android lifecycle and UID isolation. */
public final class SyntheticActivityScopedBindingRules {
    private SyntheticActivityScopedBindingRules() {}

    public static String nextBoundScope(String previousScope, String requestedScope) {
        if (previousScope != null)
            throw new IllegalStateException("Synthetic PRIVATE Activity scope cannot be rebound");
        if (requestedScope == null)
            throw new IllegalArgumentException("Missing synthetic scope UUID");
        try {
            if (!UUID.fromString(requestedScope).toString().equals(requestedScope))
                throw new IllegalArgumentException("Noncanonical synthetic scope UUID");
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid synthetic scope UUID", error);
        }
        return requestedScope;
    }
}
