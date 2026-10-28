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
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import android.content.res.Resources;
import vn.fonesi.apk.PrivateApkCluster;
import vn.fonesi.apk.PrivateApkStore;
import vn.fonesi.core.GuestImageActivationLedger;
import vn.fonesi.core.GuestImageActivationState;
import vn.fonesi.core.GuestImageRef;
import vn.fonesi.engine.GuestCodeLoader;
import vn.fonesi.engine.LoadedGuestCode;
import vn.fonesi.engine.NativeRuntime;
import vn.fonesi.engine.PrivateGuestImageEvidence;

/**
 * Disposable, same-UID Binder test channel in one explicit dedicated Android process.
 * Not an Android service/Binder virtualization layer and never executes an account-bearing app.
 */
public final class SyntheticGuestChannelService extends Service {
    static final String PROCESS_SUFFIX = ":fonesi_guest_channel";
    static final String DOMAIN = "FONESI-E15-INTERNAL-BINDER-V1:";
    private final AtomicBoolean guestAttempted = new AtomicBoolean(false);

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

        @Override public String exchangeGuest(String nonce) {
            requireProcessIdentity();
            if (Binder.getCallingUid() != Process.myUid())
                throw new SecurityException("Only host UID may dispatch a synthetic guest method");
            if (nonce == null || !nonce.matches("[0-9a-f]{32}"))
                throw new IllegalArgumentException("Invalid private guest challenge");
            if (!guestAttempted.compareAndSet(false, true))
                throw new IllegalStateException("Exactly one private guest code attempt per Binder process");
            try {
                // Binder/AIDL invokes on a worker thread. Never initialize guest code on Android UI.
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
                    throw new IllegalStateException("Guest code must not load on the main Looper");
                File owned = getApplicationContext().getDataDir().getCanonicalFile();
                File filesRoot = getFilesDir().getCanonicalFile();
                if (!filesRoot.getPath().startsWith(owned.getPath() + File.separator))
                    throw new SecurityException("Synthetic Binder code store escaped private data");
                File privateRoot = new File(filesRoot, "e16-private-code").getCanonicalFile();
                if (!privateRoot.getPath().startsWith(filesRoot.getPath() + File.separator))
                    throw new SecurityException("Private guest code root escaped files directory");
                PrivateApkStore store = new PrivateApkStore(privateRoot.toPath());
                PrivateApkCluster cluster = store.publish(
                    SyntheticSignedPayloadSource.fromInstalled(SyntheticGuestChannelService.this),
                    Build.VERSION.SDK_INT);
                PrivateGuestImageEvidence evidence = new PrivateGuestImageEvidence(store, Build.VERSION.SDK_INT);
                GuestImageRef image = evidence.resolve(cluster.getId());
                if (!evidence.matches(image))
                    throw new IllegalStateException("Signed private APK evidence not confirmed");
                GuestImageActivationLedger ledger = new GuestImageActivationLedger(
                    new File(filesRoot, "e16-image-activation").toPath(),
                    candidate -> evidence.matches(candidate)
                );
                String cloneId = UUID.randomUUID().toString();
                GuestImageActivationState pending = ledger.stage(cloneId, 0L, image);
                if (pending.getActive() != null || !image.equals(pending.getPending()))
                    throw new IllegalStateException("Synthetic guest image must remain pending");

                // Only a project-owned inert source path is hooked; no user/guest account paths.
                File source = new File(filesRoot, "e16-native-source").getCanonicalFile();
                File target = new File(filesRoot, "e16-native-target").getCanonicalFile();
                if (!(source.isDirectory() || source.mkdirs()) ||
                    !(target.isDirectory() || target.mkdirs()))
                    throw new IllegalStateException("Cannot prepare synthetic hook paths");
                Map<String, File> bindings = new LinkedHashMap<>();
                bindings.put(source.getPath(), target);
                NativeRuntime.configure(getApplicationContext(), bindings);
                if (NativeRuntime.state() != NativeRuntime.CONFIGURED)
                    throw new IllegalStateException("Synthetic routing not configured");
                NativeRuntime.enable(getApplicationContext());
                if (NativeRuntime.state() != NativeRuntime.ENABLED)
                    throw new IllegalStateException("Synthetic native runtime not enabled");

                LoadedGuestCode code = new GuestCodeLoader(getApplicationContext(), store).load(cluster.getId());
                if (!SyntheticSignedPayloadSource.PACKAGE.equals(code.getPackageName()))
                    throw new IllegalStateException("Private code package mismatch");
                Class<?> guest = code.getClassLoader().loadClass(
                    SyntheticSignedPayloadSource.PACKAGE + ".ProbeEntrypoint");
                if (guest.getClassLoader() != code.getClassLoader())
                    throw new IllegalStateException("Private DEX class loader not used");
                boolean absentFromHost = false;
                try { getClass().getClassLoader().loadClass(guest.getName()); }
                catch (ClassNotFoundException expected) { absentFromHost = true; }
                if (!absentFromHost)
                    throw new SecurityException("Guest bytecode was accidentally bundled into the host");
                Resources resources = code.getResources();
                int marker = resources.getIdentifier("fonesi_guest_code_probe", "string",
                    SyntheticSignedPayloadSource.PACKAGE);
                if (marker == 0 || !"Fonesi signed private payload".equals(resources.getString(marker)))
                    throw new IllegalStateException("Signed private guest resource mismatch");
                Object reply = guest.getMethod("execute", String.class).invoke(null, nonce);
                String expected = hex(MessageDigest.getInstance("SHA-256").digest(
                    ("FONESI-E12-SYNTHETIC-V1:" + nonce).getBytes(StandardCharsets.UTF_8)));
                if (!(reply instanceof String) || !expected.equals(reply) ||
                    !evidence.matches(image) ||
                    ledger.readVerified(cloneId).getActive() != null ||
                    NativeRuntime.state() != NativeRuntime.ENABLED)
                    throw new IllegalStateException("Private guest bytecode or code-image readback failed");
                // Code image is not marked active: no real Android guest Application/package was launched.
                return "E16|" + reply + "|" + Process.myPid() + "|" + Process.myUid();
            } catch (Exception failure) {
                // Never fall back to original installed code, host classloader, or dummy response.
                throw new IllegalStateException("Synthetic signed private guest Binder dispatch failed", failure);
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
