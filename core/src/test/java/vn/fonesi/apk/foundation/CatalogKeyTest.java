package vn.fonesi.apk.foundation;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class CatalogKeyTest {
    private static int checks;
    private CatalogKeyTest() { }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static byte[] utf8(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private static CatalogKey.Result decode(byte[] first, byte[] second) {
        return CatalogKey.decode(CatalogKey.StorageClass.TEXT, first, CatalogKey.StorageClass.TEXT, second);
    }
    private static CatalogKey key(String first, String second) {
        CatalogKey.Result result = decode(utf8(first), utf8(second));
        check(result.isValid(), "valid exact key");
        return result.key();
    }
    private static void rejects(CatalogKey.Result result, CatalogKey.Error error) {
        check(!result.isValid() && result.error() == error, "precise failure");
        boolean threw = false;
        try { result.key(); } catch (IllegalStateException expected) { threw = true; }
        check(threw, "no key from failed result");
    }
    public static void main(String[] args) {
        for (String text : new String[]{"", " ", "\u00a0", "A\u0000B", "\n\t", "../", "a|b",
                "Case", "case", "0001", "é", "e\u0301", "\ud83d\ude00", "\ufffd", "\uffff"}) {
            byte[] raw = utf8(text), before = raw.clone();
            CatalogKey value = decode(raw, raw).key();
            check(value.engineKey().equals(text) && value.cloneId().equals(text), "no grammar/normalization");
            check(Arrays.equals(raw, before), "no input mutation");
            Arrays.fill(raw, (byte) 'X');
            check(value.engineKey().equals(text) && value.cloneId().equals(text), "immutable snapshot");
        }
        for (CatalogKey.StorageClass type : CatalogKey.StorageClass.values()) {
            if (type == CatalogKey.StorageClass.TEXT) continue;
            rejects(CatalogKey.decode(type, utf8("a"), CatalogKey.StorageClass.TEXT, utf8("b")), CatalogKey.Error.EXPECTED_TEXT);
            rejects(CatalogKey.decode(CatalogKey.StorageClass.TEXT, utf8("a"), type, utf8("b")), CatalogKey.Error.EXPECTED_TEXT);
        }
        rejects(CatalogKey.decode(null, utf8("a"), CatalogKey.StorageClass.TEXT, utf8("b")), CatalogKey.Error.EXPECTED_TEXT);
        rejects(decode(null, utf8("b")), CatalogKey.Error.MISSING_BYTES);
        rejects(decode(utf8("a"), null), CatalogKey.Error.MISSING_BYTES);
        for (byte[] invalid : new byte[][]{{(byte)0x80}, {(byte)0xc0,(byte)0xaf}, {(byte)0xc2},
                {(byte)0xe0,(byte)0x80,(byte)0x80}, {(byte)0xed,(byte)0xa0,(byte)0x80},
                {(byte)0xf0,(byte)0x80,(byte)0x80,(byte)0x80},
                {(byte)0xf4,(byte)0x90,(byte)0x80,(byte)0x80}, {(byte)0xff},
                {(byte)0xe2,(byte)0x82}, {(byte)0xc2,0x20}}) {
            byte[] before = invalid.clone();
            rejects(decode(invalid, utf8("ok")), CatalogKey.Error.MALFORMED_UTF8);
            rejects(decode(utf8("ok"), invalid), CatalogKey.Error.MALFORMED_UTF8);
            check(Arrays.equals(invalid,before), "malformed input retained");
        }
        String exact = "\u0800".repeat(4096);
        check(key(exact, "id").engineKey().equals(exact), "4096 UTF16 fits with 12288 UTF8 bytes");
        rejects(decode(utf8(exact + "x"), utf8("id")), CatalogKey.Error.BUDGET_EXCEEDED);
        check(key("\ud83d\ude00".repeat(2048), "id").engineKey().length() == 4096, "supplementary UTF16 boundary");
        rejects(decode(utf8("x"), utf8("\ud83d\ude00".repeat(2049))), CatalogKey.Error.BUDGET_EXCEEDED);
        byte[] atRawLimit = new byte[16384]; atRawLimit[0] = (byte)0x80;
        rejects(decode(atRawLimit, utf8("id")), CatalogKey.Error.MALFORMED_UTF8);
        byte[] overRawLimit = Arrays.copyOf(atRawLimit, 16385);
        rejects(decode(overRawLimit, utf8("id")), CatalogKey.Error.BUDGET_EXCEEDED);
        rejects(decode(utf8("id"), overRawLimit), CatalogKey.Error.BUDGET_EXCEEDED);
        CatalogKey first = key("FB", "id"), collision = key("Ea", "id");
        check(first.hashCode() == collision.hashCode() && !first.equals(collision), "hash collision not identity");
        Set<CatalogKey> keys = new HashSet<>(); keys.add(first); keys.add(collision);
        check(keys.size() == 2, "both colliding identities retained");
        check(first.equals(key("FB","id")) && first.hashCode() == key("FB","id").hashCode(), "equal contract");
        check(!key("a|b","c").equals(key("a","b|c")), "no delimiter concatenation");
        check(!key("engine1","id").equals(key("engine2","id")), "namespace exact");
        check(!key("engine","Case").equals(key("engine","case")), "case exact");
        check(!key("é","id").equals(key("e\u0301","id")), "no Unicode normalization");
        check(!first.equals(null) && !first.equals("FB"), "type boundary");
        System.out.println("PASS CatalogKey owned HOST assertions=" + checks
                + "; no SQLite adapter/Android/auth/provider acceptance");
    }
}
