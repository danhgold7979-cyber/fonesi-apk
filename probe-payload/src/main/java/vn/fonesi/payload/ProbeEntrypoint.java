package vn.fonesi.payload;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Harmless synthetic bytecode entrypoint. No Android Context, account, storage or network.
 * Used solely to prove that the private guest DexClassLoader actually executes bytecode.
 */
public final class ProbeEntrypoint {
    private static final String DOMAIN = "FONESI-E12-SYNTHETIC-V1:";
    private ProbeEntrypoint() {}

    public static String execute(String challenge) throws NoSuchAlgorithmException {
        if (challenge == null || !challenge.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Invalid synthetic challenge");
        }
        byte[] bytes = MessageDigest.getInstance("SHA-256")
            .digest((DOMAIN + challenge).getBytes(StandardCharsets.UTF_8));
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder answer = new StringBuilder(64);
        for (byte b : bytes) {
            answer.append(hex[(b >>> 4) & 15]).append(hex[b & 15]);
        }
        return answer.toString();
    }
}
