package vn.fonesi.nativeprobe;

import android.app.ActivityManager;
import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Disposable, same-UID Binder test channel in one explicit dedicated Android process.
 * Not an Android service/Binder virtualization layer and never executes an account-bearing app.
 */
public final class SyntheticGuestChannelService extends Service {
    static final String PROCESS_SUFFIX = ":fonesi_guest_channel";
    static final String DOMAIN = "FONESI-E15-INTERNAL-BINDER-V1:";

    private final ISyntheticGuestChannel.Stub binder = new ISyntheticGuestChannel.Stub() {
        @Override public String exchange(String nonce) {
            requireProcessIdentity();
            if (Binder.getCallingUid() != Process.myUid())
                throw new SecurityException("Only the host application UID may call the synthetic channel");
            if (nonce == null || !nonce.matches("[0-9a-f]{32}"))
                throw new IllegalArgumentException("Invalid synthetic Binder challenge");
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((DOMAIN + nonce).getBytes(StandardCharsets.UTF_8));
                return "E15|" + hex(digest) + "|" + Process.myPid() + "|" + Process.myUid();
            } catch (java.security.NoSuchAlgorithmException failure) {
                throw new IllegalStateException("SHA-256 unavailable for internal IPC proof", failure);
            }
        }
    };

    @Override public IBinder onBind(Intent intent) {
        requireProcessIdentity();
        if (intent == null || intent.getComponent() == null ||
            !getPackageName().equals(intent.getComponent().getPackageName()) ||
            !getClass().getName().equals(intent.getComponent().getClassName())) {
            return null;
        }
        return binder;
    }

    private void requireProcessIdentity() {
        final String observed;
        if (Build.VERSION.SDK_INT >= 28) observed = Application.getProcessName();
        else {
            ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            String processName = null;
            if (manager != null && manager.getRunningAppProcesses() != null)
                for (ActivityManager.RunningAppProcessInfo process : manager.getRunningAppProcesses())
                    if (process.pid == Process.myPid()) { processName = process.processName; break; }
            observed = processName;
        }
        if (!(getPackageName() + PROCESS_SUFFIX).equals(observed) ||
            Process.myUid() != getApplicationInfo().uid)
            throw new SecurityException("Synthetic Binder proof is not in its declared guest-named process");
    }

    static String hex(byte[] input) {
        final char[] digits = "0123456789abcdef".toCharArray();
        StringBuilder result = new StringBuilder(input.length * 2);
        for (byte b : input) result.append(digits[(b >>> 4) & 15]).append(digits[b & 15]);
        return result.toString();
    }
}
