package vn.fonesi.apk.foundation;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Exact display/catalog identity, never provider authority or a filesystem path. */
public final class CatalogKey {
    public static final int MAX_COMPONENT_BYTES = 16 * 1024;
    public static final int MAX_COMPONENT_UTF16 = 4096;

    public enum StorageClass { NULL, INTEGER, REAL, TEXT, BLOB }
    public enum Error { NONE, EXPECTED_TEXT, MISSING_BYTES, BUDGET_EXCEEDED, MALFORMED_UTF8 }

    public static final class Result {
        private final CatalogKey key;
        private final Error error;
        private Result(CatalogKey key, Error error) { this.key = key; this.error = error; }
        public boolean isValid() { return error == Error.NONE; }
        public Error error() { return error; }
        public CatalogKey key() {
            if (!isValid()) throw new IllegalStateException("No decoded catalog key");
            return key;
        }
    }

    private final String engineKey;
    private final String cloneId;
    private CatalogKey(String engineKey, String cloneId) {
        this.engineKey = engineKey;
        this.cloneId = cloneId;
    }
    public String engineKey() { return engineKey; }
    public String cloneId() { return cloneId; }

    /**
     * Input must be UTF8-proven transport bytes and stable/owned during this call.
     * Budgets limit reads, not schema validity. No storage is read or repaired here.
     */
    public static Result decode(StorageClass engineType, byte[] engineBytes,
                                StorageClass cloneType, byte[] cloneBytes) {
        if (engineType != StorageClass.TEXT || cloneType != StorageClass.TEXT) {
            return new Result(null, Error.EXPECTED_TEXT);
        }
        if (engineBytes == null || cloneBytes == null) return new Result(null, Error.MISSING_BYTES);
        if (engineBytes.length > MAX_COMPONENT_BYTES || cloneBytes.length > MAX_COMPONENT_BYTES) {
            return new Result(null, Error.BUDGET_EXCEEDED);
        }
        byte[] engine = engineBytes.clone();
        byte[] clone = cloneBytes.clone();
        try {
            String first = decodeExact(engine);
            String second = decodeExact(clone);
            if (first.length() > MAX_COMPONENT_UTF16 || second.length() > MAX_COMPONENT_UTF16) {
                return new Result(null, Error.BUDGET_EXCEEDED);
            }
            return new Result(new CatalogKey(first, second), Error.NONE);
        } catch (CharacterCodingException error) {
            return new Result(null, Error.MALFORMED_UTF8);
        }
    }

    private static String decodeExact(byte[] bytes) throws CharacterCodingException {
        String decoded = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        if (!Arrays.equals(bytes, decoded.getBytes(StandardCharsets.UTF_8))) {
            throw new CharacterCodingException();
        }
        return decoded;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CatalogKey)) return false;
        CatalogKey key = (CatalogKey) other;
        return engineKey.equals(key.engineKey) && cloneId.equals(key.cloneId);
    }
    @Override public int hashCode() { return 31 * engineKey.hashCode() + cloneId.hashCode(); }
}
