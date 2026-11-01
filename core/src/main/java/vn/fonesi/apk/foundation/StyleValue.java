package vn.fonesi.apk.foundation;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Inert original-scope display validation. No persistence, rendering or execution authority. */
public final class StyleValue {
    private final String accentKey;
    private final String rawBadge;
    private final String displayBadge;

    private StyleValue(String accentKey, String rawBadge, String displayBadge) {
        this.accentKey = accentKey;
        this.rawBadge = rawBadge;
        this.displayBadge = displayBadge;
    }

    public String accentKey() { return accentKey; }
    public String rawBadge() { return rawBadge; }
    public String displayBadge() { return displayBadge; }

    /** Explicit creation of a new default; never called as a fallback for invalid stored input. */
    public static StyleValue newDefault() { return new StyleValue("teal", null, "F"); }

    public enum Error {
        NONE, FIELDS_REQUIRED, UNKNOWN_OR_MISSING_FIELDS, ACCENT_TYPE, ACCENT_UNKNOWN,
        BADGE_TYPE, BADGE_FORBIDDEN_CODEPOINT, BADGE_CODEPOINT_COUNT, BADGE_UTF8_BYTES
    }

    public static final class Result {
        private final Error error;
        private final StyleValue value;
        private Result(Error error, StyleValue value) { this.error = error; this.value = value; }
        public boolean valid() { return error == Error.NONE; }
        public Error error() { return error; }
        public StyleValue value() {
            if (!valid()) throw new IllegalStateException("Invalid style has no presentation");
            return value;
        }
    }

    private static Result reject(Error error) { return new Result(error, null); }

    /** Internal typed fields only; these names do not activate an external serialization format. */
    public static Result validateFields(Map<String, ?> fields) {
        if (fields == null) return reject(Error.FIELDS_REQUIRED);
        if (fields.size() != 2 || !fields.containsKey("accentKey") || !fields.containsKey("badgeText")) {
            return reject(Error.UNKNOWN_OR_MISSING_FIELDS);
        }
        Object accentInput = fields.get("accentKey");
        if (!(accentInput instanceof String)) return reject(Error.ACCENT_TYPE);
        String accent = (String) accentInput;
        if (!validAccent(accent)) return reject(Error.ACCENT_UNKNOWN);
        Object badgeInput = fields.get("badgeText");
        if (badgeInput != null && !(badgeInput instanceof String)) return reject(Error.BADGE_TYPE);
        String raw = (String) badgeInput;
        if (raw == null) return new Result(Error.NONE, new StyleValue(accent, null, "F"));

        // Check raw input first: presentation trimming must not conceal corrupt control characters.
        for (int offset = 0; offset < raw.length();) {
            int cp = raw.codePointAt(offset);
            int type = Character.getType(cp);
            if (type == Character.CONTROL || type == Character.FORMAT
                    || type == Character.UNASSIGNED || type == Character.PRIVATE_USE
                    || type == Character.SURROGATE || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                return reject(Error.BADGE_FORBIDDEN_CODEPOINT);
            }
            offset += Character.charCount(cp);
        }
        int start = 0;
        int end = raw.length();
        while (start < end && presentationSpace(raw.codePointAt(start))) {
            start += Character.charCount(raw.codePointAt(start));
        }
        while (end > start && presentationSpace(raw.codePointBefore(end))) {
            end -= Character.charCount(raw.codePointBefore(end));
        }
        if (start == end) return new Result(Error.NONE, new StyleValue(accent, raw, "F"));
        int count = raw.codePointCount(start, end);
        if (count < 1 || count > 2) return reject(Error.BADGE_CODEPOINT_COUNT);
        String display = raw.substring(start, end);
        if (display.getBytes(StandardCharsets.UTF_8).length > 16) {
            return reject(Error.BADGE_UTF8_BYTES);
        }
        return new Result(Error.NONE, new StyleValue(accent, raw, display));
    }

    private static boolean validAccent(String value) {
        switch (value) {
            case "teal": case "blue": case "purple": case "amber": case "red": case "slate":
                return true;
            default:
                return false;
        }
    }

    private static boolean presentationSpace(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp);
    }
}
