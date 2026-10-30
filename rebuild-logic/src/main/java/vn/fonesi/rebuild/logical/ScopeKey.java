package vn.fonesi.rebuild.logical;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Pure stable package namespace key; not a lease, ownership proof or authentication. */
public final class ScopeKey {
    private ScopeKey() {}
    public static String forIdentity(OperationProtocol.Identity identity) {
        Objects.requireNonNull(identity);
        String namespace = identity.provider().id();
        String framed = namespace.length() + ":" + namespace + ":" + identity.packageName();
        byte[] hash;
        try { hash = MessageDigest.getInstance("SHA-256").digest(framed.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        char[] result = new char[hash.length * 2];
        String digits = "0123456789abcdef";
        for (int n = 0; n < hash.length; n++) {
            int value = hash[n] & 255;
            result[n * 2] = digits.charAt(value >>> 4);
            result[n * 2 + 1] = digits.charAt(value & 15);
        }
        return new String(result);
    }
}
