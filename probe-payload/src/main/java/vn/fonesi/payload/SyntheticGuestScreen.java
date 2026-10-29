package vn.fonesi.payload;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * E24 project-owned, synthetic-only private DEX screen. Android registers the HOST Activity;
 * this class never represents a launched/attached third-party guest Activity.
 *
 * The static instance is scoped to a single freshly loaded private DexClassLoader/process.
 */
public final class SyntheticGuestScreen {
    private static final String PREFIX = "FONESI-E24-PRIVATE-SCREEN:";
    private static final String[] ORDER = {"CREATE", "START", "RESUME", "PAUSE", "STOP", "DESTROY"};
    private static int next = 0;

    private SyntheticGuestScreen() {}

    public static View createView(Activity host, String nonce) throws Exception {
        checkMainThread();
        checkNonce(nonce);
        if (!"vn.fonesi.nativeprobe".equals(host.getPackageName()) || next != 0)
            throw new SecurityException("Only a new synthetic Fonesi host Activity may render this View");
        TextView view = new TextView(host);
        view.setTag("FONESI-E24-SIGNED-PRIVATE-VIEW");
        view.setText(PREFIX + digest(nonce));
        view.setTextSize(18f);
        view.setPadding(20, 30, 20, 30);
        return view;
    }

    public static void lifecycle(String event) {
        checkMainThread();
        if (next >= ORDER.length || !ORDER[next].equals(event))
            throw new IllegalStateException("Synthetic lifecycle replay has missing/out-of-order event");
        next++;
    }

    public static String completedTranscript() {
        checkMainThread();
        if (next != ORDER.length)
            throw new IllegalStateException("Synthetic guest event transcript incomplete");
        return String.join(",", ORDER);
    }

    private static void checkMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper())
            throw new IllegalStateException("Synthetic screen lifecycle must run on Android UI thread");
    }

    private static void checkNonce(String nonce) {
        if (nonce == null || !nonce.matches("[0-9a-f]{32}"))
            throw new IllegalArgumentException("Invalid synthetic signed-screen challenge");
    }

    private static String digest(String nonce) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
            .digest((PREFIX + nonce).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(Character.forDigit((b >>> 4) & 15, 16));
            hex.append(Character.forDigit(b & 15, 16));
        }
        return hex.toString();
    }
}
