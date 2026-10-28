package vn.fonesi.nativeprobe;

import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.Resources;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import vn.fonesi.apk.PrivateApkCluster;
import vn.fonesi.apk.PrivateApkStore;
import vn.fonesi.core.GuestImageActivationLedger;
import vn.fonesi.core.GuestImageActivationState;
import vn.fonesi.core.GuestImageRef;
import vn.fonesi.core.InstalledSource;
import vn.fonesi.engine.GuestCodeLoader;
import vn.fonesi.engine.LoadedGuestCode;
import vn.fonesi.engine.NativeRuntime;
import vn.fonesi.engine.PrivateGuestImageEvidence;
import vn.fonesi.engine.SyntheticGuestScopedContext;

/**
 * Explicit synthetic Android proof only. It loads a signed private test payload's DEX/resources;
 * it never creates a guest Context, starts a guest Activity, or touches social accounts.
 */
public final class GuestCodeProbeService extends Service {
    static final String EXTRA_RUN_ID = "run_id";
    static final String PAYLOAD = "vn.fonesi.payload";
    private boolean started;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (started || intent == null) return START_NOT_STICKY;
        final String runId;
        try { runId = UUID.fromString(intent.getStringExtra(EXTRA_RUN_ID)).toString(); }
        catch (Exception invalid) { stopSelf(startId); return START_NOT_STICKY; }
        started = true;
        new Thread(() -> run(runId), "Fonesi-guest-code-probe").start();
        return START_NOT_STICKY;
    }

    private void run(String runId) {
        File reports = null;
        JSONObject result = new JSONObject();
        String stage = "verify dedicated Android process";
        try {
            // An Android process name isolates the exercise from the host lifecycle, but
            // both processes still share an application UID. Never claim kernel-level isolation.
            final String processName;
            if (Build.VERSION.SDK_INT >= 28) {
                processName = Application.getProcessName();
            } else {
                throw new IllegalStateException("E12 synthetic process proof requires Android API 28+");
            }
            ProbeData.check((getPackageName() + ":fonesi_guest_loader").equals(processName),
                "synthetic bytecode may run only in its dedicated named process");
            ProbeData.check(Process.myUid() == getApplicationInfo().uid,
                "reported UID does not match this host application");
            stage = "canonical host-private directory";
            // Android may expose /data/user/0 as a framework-controlled alias of /data/data.
            // Canonicalize trusted Context directories first; never relax PrivateApkStore's guards.
            File hostRoot = getApplicationContext().getDataDir().getCanonicalFile();
            File filesRoot = getFilesDir().getCanonicalFile();
            ProbeData.check(filesRoot.getPath().startsWith(hostRoot.getPath() + File.separator),
                "files directory must remain inside private host data");
            reports = new File(filesRoot, "guest-code-probe").getCanonicalFile();
            ProbeData.check(reports.isDirectory() || reports.mkdirs(), "private report directory");
            String root = filesRoot.getPath() + File.separator;
            ProbeData.check(reports.getPath().startsWith(root), "private report path");

            stage = "read original test payload identity";
            InstalledSource source = signedPayloadSource();
            stage = "create canonical private APK store";
            File codeRoot = new File(filesRoot, "guest-code-images").getCanonicalFile();
            ProbeData.check(codeRoot.getPath().startsWith(filesRoot.getPath() + File.separator),
                "code store must remain inside private files");
            PrivateApkStore codeStore = new PrivateApkStore(codeRoot.toPath());
            stage = "publish and verify signed private APK";
            PrivateApkCluster cluster = codeStore.publish(source, Build.VERSION.SDK_INT);
            stage = "read back private cluster identity";
            PrivateGuestImageEvidence evidence = new PrivateGuestImageEvidence(codeStore, Build.VERSION.SDK_INT);
            GuestImageRef image = evidence.resolve(cluster.getId());
            ProbeData.check(evidence.matches(image), "signed private image verification");

            stage = "stage durable image reference";
            GuestImageActivationLedger ledger = new GuestImageActivationLedger(
                new File(reports, "activation").toPath(), candidate -> evidence.matches(candidate)
            );
            GuestImageActivationState staged = ledger.stage(runId, 0L, image);
            ProbeData.check(staged.getActive() == null && image.equals(staged.getPending()), "staged but not activated");
            ProbeData.check(image.equals(ledger.readVerified(runId).getPending()), "durable code intent");

            // Native PathRules requires at least one owned route. Only synthetic paths are mapped.
            File syntheticSource = new File(reports, runId + "-source").getCanonicalFile();
            File syntheticGuest = new File(reports, runId + "-guest").getCanonicalFile();
            ProbeData.check((syntheticSource.isDirectory() || syntheticSource.mkdirs()) &&
                (syntheticGuest.isDirectory() || syntheticGuest.mkdirs()), "private synthetic routing directories");
            java.util.Map<String, File> isolatedRules = new java.util.LinkedHashMap<>();
            isolatedRules.put(syntheticSource.getPath(), syntheticGuest);
            stage = "configure dedicated-process native routing";
            NativeRuntime.configure(getApplicationContext(), isolatedRules);
            ProbeData.check(NativeRuntime.state() == NativeRuntime.CONFIGURED, "native configuration");
            stage = "activate dedicated-process native routing";
            NativeRuntime.enable(getApplicationContext());
            ProbeData.check(NativeRuntime.state() == NativeRuntime.ENABLED, "native activation");

            stage = "load DEX and resources from private code image";
            LoadedGuestCode loaded = new GuestCodeLoader(getApplicationContext(), codeStore).load(cluster.getId());
            ProbeData.check(PAYLOAD.equals(loaded.getPackageName()), "private guest package identity");
            Class<?> payloadClass = loaded.getClassLoader().loadClass(PAYLOAD + ".PayloadActivity");
            ProbeData.check(payloadClass.getClassLoader() == loaded.getClassLoader(), "private DEX class loader");

            stage = "read signed payload resource marker";
            Resources resources = loaded.getResources();
            int resourceId = resources.getIdentifier("fonesi_guest_code_probe", "string", PAYLOAD);
            ProbeData.check(resourceId != 0 &&
                "Fonesi signed private payload".equals(resources.getString(resourceId)), "private signed resources");
            ProbeData.check(evidence.matches(image), "private image unchanged after class/resource load");
            ProbeData.check(ledger.readVerified(runId).getActive() == null, "code load is not guest activation");

            stage = "execute bytecode from signed private DEX image";
            final String entryName = PAYLOAD + ".ProbeEntrypoint";
            Class<?> entryClass = loaded.getClassLoader().loadClass(entryName);
            ProbeData.check(entryClass.getClassLoader() == loaded.getClassLoader(),
                "synthetic bytecode must be defined by private guest class loader");
            boolean hostClassAbsent = false;
            try { getClass().getClassLoader().loadClass(entryName); }
            catch (ClassNotFoundException expected) { hostClassAbsent = true; }
            ProbeData.check(hostClassAbsent, "synthetic entrypoint must not be bundled into host APK");

            byte[] nonce = new byte[16];
            new SecureRandom().nextBytes(nonce);
            String challenge = hex(nonce);
            String expectedReply = hex(MessageDigest.getInstance("SHA-256").digest(
                ("FONESI-E12-SYNTHETIC-V1:" + challenge).getBytes(StandardCharsets.UTF_8)));
            Object returned = entryClass.getMethod("execute", String.class).invoke(null, challenge);
            ProbeData.check(expectedReply.equals(returned),
                "private guest bytecode did not execute the challenge contract");
            // Never persist challenge or response. This only proves synthetic class execution.
            ProbeData.check(evidence.matches(image), "private APK changed during guest bytecode execution");
            ProbeData.check(ledger.readVerified(runId).getActive() == null,
                "guest code execution is not provider activation/readback");

            stage = "exercise restricted synthetic guest-scoped data context";
            String scopedName = PAYLOAD + ".ScopedProbeEntrypoint";
            Class<?> scopedClass = loaded.getClassLoader().loadClass(scopedName);
            ProbeData.check(scopedClass.getClassLoader() == loaded.getClassLoader(),
                "scoped storage synthetic code must have a private class loader");
            boolean hostScopedAbsent = false;
            try { getClass().getClassLoader().loadClass(scopedName); }
            catch (ClassNotFoundException expected) { hostScopedAbsent = true; }
            ProbeData.check(hostScopedAbsent, "synthetic scoped bytecode was accidentally packaged into host");

            // Both UUIDs refer only to test data; this serial probe is NOT guest account switching.
            SyntheticGuestScopedContext scopeA = new SyntheticGuestScopedContext(
                getApplicationContext(), loaded, UUID.randomUUID().toString());
            SyntheticGuestScopedContext scopeB = new SyntheticGuestScopedContext(
                getApplicationContext(), loaded, UUID.randomUUID().toString());
            ProbeData.check(!scopeA.getFilesDir().equals(scopeB.getFilesDir()) &&
                !scopeA.getDatabasePath("sample.sqlite").equals(scopeB.getDatabasePath("sample.sqlite")),
                "A/B synthetic file and database paths must be disjoint");
            ProbeData.check(scopeA.getClassLoader() == loaded.getClassLoader() &&
                scopeA.getResources() == loaded.getResources() &&
                PAYLOAD.equals(scopeA.getPackageName()), "synthetic Context is bound to private APK");
            boolean hostServiceDenied = false;
            try { scopeA.getSystemService(Context.ACTIVITY_SERVICE); }
            catch (UnsupportedOperationException expected) { hostServiceDenied = true; }
            ProbeData.check(hostServiceDenied, "restricted synthetic Context must refuse host services");

            String hostPrefBefore = getSharedPreferences(
                "fonesi-e14-test-original", Context.MODE_PRIVATE).getString("marker", null);
            java.lang.reflect.Method readScope = scopedClass.getMethod("read", Context.class);
            java.lang.reflect.Method writeScope = scopedClass.getMethod("write", Context.class, String.class);
            ProbeData.check("EMPTY".equals(readScope.invoke(null, scopeA)) &&
                "EMPTY".equals(readScope.invoke(null, scopeB)), "fresh synthetic scopes must be empty");
            String markerA = UUID.randomUUID().toString().replace("-", "");
            String markerB = UUID.randomUUID().toString().replace("-", "");
            ProbeData.check(!markerA.equals(markerB), "independent test markers");
            writeScope.invoke(null, scopeA, markerA);
            ProbeData.check(markerA.equals(readScope.invoke(null, scopeA)) &&
                "EMPTY".equals(readScope.invoke(null, scopeB)), "A writes must never appear in B");
            writeScope.invoke(null, scopeB, markerB);
            ProbeData.check(markerA.equals(readScope.invoke(null, scopeA)) &&
                markerB.equals(readScope.invoke(null, scopeB)), "A/B scoped storage readback diverged");
            ProbeData.check(java.util.Objects.equals(hostPrefBefore, getSharedPreferences(
                "fonesi-e14-test-original", Context.MODE_PRIVATE).getString("marker", null)),
                "synthetic original host preference marker changed");
            // A/B markers are synthetic test values only. They are never persisted in the report.
            ProbeData.check(evidence.matches(image) && ledger.readVerified(runId).getActive() == null,
                "synthetic scoped IO cannot activate or mutate a signed code image");

            stage = "write successful synthetic receipt";
            result.put("complete", true).put("passed", true).put("run_id", runId)
                .put("api", Build.VERSION.SDK_INT).put("pid", Process.myPid())
                .put("abi", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown")
                .put("signed_private_cluster", true).put("dex_class_loader", true)
                .put("private_resources", true).put("activation_pending", true)
                .put("guest_bytecode_executed", true).put("guest_process_verified", true)
                .put("guest_process_name", processName).put("guest_process_pid", Process.myPid())
                .put("guest_uid_same_as_host", true)
                .put("synthetic_scoped_context_verified", true)
                .put("private_file_state_verified", true)
                .put("shared_preferences_state_verified", true)
                .put("sqlite_state_verified", true)
                .put("same_android_uid", true).put("kernel_uid_isolation", false)
                .put("guest_application_attached", false)
                .put("guest_context_executed", false).put("guest_activity_executed", false)
                .put("binder_provider_tested", false).put("social_accounts_tested", false);
        } catch (Throwable failure) {
            // No paths, package data, tokens, passwords, or raw stack traces in persisted diagnostics.
            try { result.put("complete", true).put("passed", false).put("run_id", runId)
                .put("failure_type", failure.getClass().getName())
                .put("failure_stage", stage); }
            catch (Exception ignored) { /* parent times out if report cannot be written */ }
        } finally {
            if (reports != null) try {
                ProbeData.report(new File(reports, runId + ".json"), result);
            } catch (Exception ignored) { /* parent reports timeout/failure */ }
            stopSelf();
            Process.killProcess(Process.myPid()); // Never hot-swap guest code/hook policy.
        }
    }

    private static String hex(byte[] input) {
        final char[] digits = "0123456789abcdef".toCharArray();
        StringBuilder result = new StringBuilder(input.length * 2);
        for (byte b : input) result.append(digits[(b >>> 4) & 15]).append(digits[b & 15]);
        return result.toString();
    }

    @SuppressWarnings("deprecation")
    private InstalledSource signedPayloadSource() throws Exception {
        final int flags = Build.VERSION.SDK_INT >= 28
            ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo info = getPackageManager().getPackageInfo(PAYLOAD, flags);
        ApplicationInfo app = info.applicationInfo;
        ProbeData.check(app != null && app.sourceDir != null, "test payload installed");
        List<String> paths = new ArrayList<>();
        paths.add(app.sourceDir);
        if (app.splitSourceDirs != null) Collections.addAll(paths, app.splitSourceDirs);
        Signature[] signatures = Build.VERSION.SDK_INT >= 28 && info.signingInfo != null
            ? info.signingInfo.getApkContentsSigners() : info.signatures;
        ProbeData.check(signatures != null && signatures.length > 0, "payload has signing identity");
        Set<String> digests = new LinkedHashSet<>();
        for (Signature signature : signatures) {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
            StringBuilder hex = new StringBuilder(64);
            for (byte value : bytes) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            digests.add(hex.toString());
        }
        long version = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        return new InstalledSource(PAYLOAD, version, digests, paths);
    }
}
