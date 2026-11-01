package vn.fonesi.apk.foundation;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Owned in-memory checks only. Does not prove Android Unicode tables, UI or persistence. */
public final class StyleValueTest {
    private static int checks;
    private StyleValueTest() { }
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    private static Map<String, Object> input(Object accent, Object badge) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("accentKey", accent);
        fields.put("badgeText", badge);
        return fields;
    }
    private static StyleValue.Result validate(Object accent, Object badge) {
        return StyleValue.validateFields(input(accent, badge));
    }
    private static void rejected(StyleValue.Result result, StyleValue.Error error) {
        check(!result.valid() && result.error() == error, "precise error " + error);
        boolean threw = false;
        try { result.value(); } catch (IllegalStateException expected) { threw = true; }
        check(threw, "invalid input cannot supply presentation");
    }
    public static void main(String[] args) {
        StyleValue defaults = StyleValue.newDefault();
        check(defaults.accentKey().equals("teal") && defaults.rawBadge() == null
                && defaults.displayBadge().equals("F"), "explicit new default");
        for (String accent : new String[] {"teal", "blue", "purple", "amber", "red", "slate"}) {
            StyleValue.Result value = validate(accent, "A");
            check(value.valid() && value.value().accentKey().equals(accent), "exact accent");
        }
        for (String accent : new String[] {"Teal", " teal", "teal ", "", "yellow", "#126B55", "url(x)"}) {
            rejected(validate(accent, "A"), StyleValue.Error.ACCENT_UNKNOWN);
        }
        rejected(validate(null, "A"), StyleValue.Error.ACCENT_TYPE);
        rejected(validate(1, "A"), StyleValue.Error.ACCENT_TYPE);
        rejected(StyleValue.validateFields(null), StyleValue.Error.FIELDS_REQUIRED);
        rejected(StyleValue.validateFields(Map.of()), StyleValue.Error.UNKNOWN_OR_MISSING_FIELDS);
        rejected(StyleValue.validateFields(Map.of("accentKey", "teal")), StyleValue.Error.UNKNOWN_OR_MISSING_FIELDS);
        for (String extra : new String[] {"path", "url", "resource", "bitmap", "signature", "future"}) {
            Map<String, Object> fields = input("teal", "A");
            fields.put(extra, "opaque");
            Map<String, Object> before = new HashMap<>(fields);
            rejected(StyleValue.validateFields(fields), StyleValue.Error.UNKNOWN_OR_MISSING_FIELDS);
            check(fields.equals(before), "unknown-field rejection does not mutate input");
        }
        for (Object badge : new Object[] {1, true, new byte[] {1}, new Object()}) {
            rejected(validate("teal", badge), StyleValue.Error.BADGE_TYPE);
        }
        for (String raw : new String[] {"", " ", "   ", "\u00a0", "\u2003"}) {
            StyleValue value = validate("teal", raw).value();
            check(value.displayBadge().equals("F") && value.rawBadge().equals(raw), "display-only empty default");
        }
        check(validate("teal", null).value().rawBadge() == null, "null preserved");
        for (String visible : new String[] {"A", "AB", "é", "e\u0301", "\ud83d\ude00", "\ud83d\ude00\ud83d\ude01", "..", "./", "@x", "C:"}) {
            String raw = "\u00a0 " + visible + " \u2003";
            StyleValue value = validate("blue", raw).value();
            check(value.displayBadge().equals(visible) && value.rawBadge().equals(raw), "valid raw retained / display trimmed");
            check(value.displayBadge().getBytes(StandardCharsets.UTF_8).length <= 16, "explicit byte invariant");
        }
        for (String badge : new String[] {"ABC", "\ud83d\ude00AB", "https://x", "<b>x</b>"}) {
            rejected(validate("teal", badge), StyleValue.Error.BADGE_CODEPOINT_COUNT);
        }
        // Each forbidden category plus malformed UTF-16. Edge positions test validation before trim.
        for (String badge : new String[] {"\u0000", "\nA", "A\t", "\u200d", "\u0378", "\ue000", "\ud800", "\udc00", "A\ud800B", "\u2028", "\u2029", "\n"}) {
            Map<String, Object> fields = input("teal", badge);
            Map<String, Object> before = new HashMap<>(fields);
            rejected(StyleValue.validateFields(fields), StyleValue.Error.BADGE_FORBIDDEN_CODEPOINT);
            check(fields.equals(before), "malformed input retained unchanged");
        }
        Map<String, Object> fields = input("slate", " A ");
        StyleValue snapshot = StyleValue.validateFields(fields).value();
        fields.put("accentKey", "red");
        fields.put("badgeText", "B");
        check(snapshot.accentKey().equals("slate") && snapshot.rawBadge().equals(" A ")
                && snapshot.displayBadge().equals("A"), "result independent of later caller map edits");
        System.out.println("PASS StyleValue owned HOST assertions=" + checks
                + "; no Android/rendering/persistence/provider acceptance");
    }
}
