package vn.fonesi.apk.foundation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import vn.fonesi.apk.foundation.UpdatePreflight.Comparison;
import vn.fonesi.apk.foundation.UpdatePreflight.Observation;
import vn.fonesi.apk.foundation.UpdatePreflight.Preview;

/** Independent synthetic tests. No provider, Android runtime or real installed app is involved. */
public final class UpdatePreflightTest {
    private static int checks;
    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
    private static void invalid(Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid input rejected");
    }
    private static Observation obs(String label, long version, long sequence) {
        return new Observation(label, version, sequence);
    }
    private static Preview result(Observation a, Observation b, long sequence, Comparison expected) {
        Preview p = UpdatePreflight.compare(a, b, sequence);
        check(p.comparison() == expected, "comparison " + expected);
        check(!p.executable(), "all observations remain non-executable");
        check(p.blockReason() == UpdatePreflight.BlockReason.PROVIDER_UNAVAILABLE, "unavailable reason");
        return p;
    }
    public static void main(String[] args) {
        Observation v1 = obs("example.app", 1, 7);
        result(null, null, 7, Comparison.MISSING_OBSERVATION);
        result(v1, null, 7, Comparison.MISSING_OBSERVATION);
        result(null, v1, 7, Comparison.MISSING_OBSERVATION);
        result(v1, v1, 7, Comparison.EQUAL_OBSERVED_VERSION);
        result(obs("example.app", 2, 7), v1, 7, Comparison.SOURCE_OBSERVED_NEWER);
        result(obs("example.app", 0, 7), v1, 7, Comparison.SOURCE_OBSERVED_OLDER);
        result(obs("example.app", Long.MAX_VALUE, 7), obs("example.app", 0, 7), 7,
                Comparison.SOURCE_OBSERVED_NEWER);
        result(obs("example.app", 0, 7), obs("example.app", Long.MAX_VALUE, 7), 7,
                Comparison.SOURCE_OBSERVED_OLDER);
        result(v1, v1, 8, Comparison.STALE_OBSERVATION);
        result(obs("example.app", 2, 8), v1, 7, Comparison.STALE_OBSERVATION);
        result(v1, obs("example.app", 2, 8), 7, Comparison.STALE_OBSERVATION);
        result(obs("other.app", 2, 8), v1, 7, Comparison.STALE_OBSERVATION);
        result(obs("other.app", 2, 7), v1, 7, Comparison.PACKAGE_MISMATCH);
        result(obs("EXAMPLE.app", 1, 7), v1, 7, Comparison.PACKAGE_MISMATCH);
        result(null, obs("other.app", 2, 8), 7, Comparison.MISSING_OBSERVATION);
        Preview old = result(v1, v1, 7, Comparison.EQUAL_OBSERVED_VERSION);
        result(obs("example.app", 2, 8), obs("example.app", 1, 8), 8, Comparison.SOURCE_OBSERVED_NEWER);
        check(old.comparison() == Comparison.EQUAL_OBSERVED_VERSION && !old.executable(), "old preview unchanged");
        for (String bad : new String[]{null, "", " x", "x ", "\u00a0x", "x\u00a0", "x\nx", "x\u0000", "\ud800", "\udc00", "x".repeat(256)}) {
            invalid(() -> obs(bad, 1, 0));
        }
        invalid(() -> obs("x", -1, 0));
        invalid(() -> obs("x", 0, -1));
        invalid(() -> UpdatePreflight.compare(v1, v1, -1));
        invalid(() -> UpdatePreflight.compare(null, null, -1));
        String exact = "app.\u00e9\ud83d\ude00";
        Observation unicode = obs(exact, 0, Long.MAX_VALUE);
        check(unicode.packageLabel().equals(exact), "Unicode preserved exactly");
        check(unicode.observedVersion() == 0 && unicode.snapshotSequence() == Long.MAX_VALUE, "long bounds retained");
        check(obs("x".repeat(255), 0, 0).packageLabel().length() == 255, "label bound accepted");
        result(unicode, unicode, Long.MAX_VALUE, Comparison.EQUAL_OBSERVED_VERSION);
        for (Class<?> type : new Class<?>[]{Observation.class, Preview.class}) {
            check(Modifier.isFinal(type.getModifiers()), "immutable type is final");
            for (Field field : type.getDeclaredFields()) {
                check(Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers()), "private final state");
                check(field.getType().isPrimitive() || field.getType() == String.class || field.getType().isEnum(), "only immutable values");
            }
        }
        System.out.println("UpdatePreflightTest PASS: " + checks + " checks; HOST synthetic display-only; runtime unavailable");
    }
}
