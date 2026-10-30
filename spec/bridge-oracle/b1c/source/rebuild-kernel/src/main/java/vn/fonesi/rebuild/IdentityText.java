package vn.fonesi.rebuild;

/** B1b identity contract: exact scalar text, no lossy UTF-8 namespace aliases. */
final class IdentityText {
    private IdentityText() {}
    static void require(String text) {
        if (text == null || text.isEmpty() || text.length() > 256) invalid();
        int bytes = 0;
        for (int offset = 0; offset < text.length();) {
            char first = text.charAt(offset);
            if (Character.isLowSurrogate(first)) invalid();
            if (Character.isHighSurrogate(first) && (offset + 1 == text.length()
                    || !Character.isLowSurrogate(text.charAt(offset + 1)))) invalid();
            int scalar = text.codePointAt(offset);
            int width = Character.charCount(scalar);
            if (scalar <= 0x1f || (scalar >= 0x7f && scalar <= 0x9f)) invalid();
            if ((offset == 0 || offset + width == text.length()) && whitespace(scalar)) invalid();
            bytes += scalar <= 0x7f ? 1 : scalar <= 0x7ff ? 2 : scalar <= 0xffff ? 3 : 4;
            offset += width;
        }
        if (bytes > 768) invalid();
    }
    private static boolean whitespace(int scalar) {
        return (scalar >= 0x9 && scalar <= 0xd) || scalar == 0x20 || scalar == 0x85
                || scalar == 0xa0 || scalar == 0x1680 || (scalar >= 0x2000 && scalar <= 0x200a)
                || scalar == 0x2028 || scalar == 0x2029 || scalar == 0x202f
                || scalar == 0x205f || scalar == 0x3000;
    }
    private static void invalid() { throw new IllegalArgumentException("Invalid identity field (B1b)"); }
}
