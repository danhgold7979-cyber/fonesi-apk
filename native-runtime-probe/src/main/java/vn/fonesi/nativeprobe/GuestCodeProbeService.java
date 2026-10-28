package vn.fonesi.nativeprobe;

import android.app.Service;
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
        try {
            reports = new File(getFilesDir(), "guest-code-probe").getCanonicalFile();
            ProbeData.check(reports.isDirectory() || reports.mkdirs(), "private report directory");
            String root = getFilesDir().getCanonicalPath() + File.separator;
            ProbeData.check(reports.getPath().startsWith(root), "private report path");

            InstalledSource source = signedPayloadSource();
            PrivateApkStore codeStore = new PrivateApkStore(new File(getFilesDir(), "guest-code-images").toPath());
            PrivateApkCluster cluster = codeStore.publish(source, Build.VERSION.SDK_INT);
            PrivateGuestImageEvidence evidence = new PrivateGuestImageEvidence(codeStore, Build.VERSION.SDK_INT);
            GuestImageRef image = evidence.resolve(cluster.getId());
            ProbeData.check(evidence.matches(image), "signed private image verification");

            GuestImageActivationLedger ledger = new GuestImageActivationLedger(
                new File(reports, "activation").toPath(), candidate -> evidence.matches(candidate)
            );
            GuestImageActivationState staged = ledger.stage(runId, 0L, image);
            ProbeData.check(staged.getActive() == null && image.equals(staged.getPending()), "staged but not activated");
            ProbeData.check(image.equals(ledger.readVerified(runId).getPending()), "durable code intent");

            // An empty routing map cannot make the host process a guest. This is a new, dedicated process.
            NativeRuntime.configure(getApplicationContext(), Collections.emptyMap());
            ProbeData.check(NativeRuntime.state() == NativeRuntime.CONFIGURED, "native configuration");
            NativeRuntime.enable(getApplicationContext());
            ProbeData.check(NativeRuntime.state() == NativeRuntime.ENABLED, "native activation");

            LoadedGuestCode loaded = new GuestCodeLoader(getApplicationContext(), codeStore).load(cluster.getId());
            ProbeData.check(PAYLOAD.equals(loaded.getPackageName()), "private guest package identity");
            Class<?> payloadClass = loaded.getClassLoader().loadClass(PAYLOAD + ".PayloadActivity");
            ProbeData.check(payloadClass.getClassLoader() == loaded.getClassLoader(), "private DEX class loader");

            Resources resources = loaded.getResources();
            int resourceId = resources.getIdentifier("fonesi_guest_code_probe", "string", PAYLOAD);
            ProbeData.check(resourceId != 0 &&
                "Fonesi signed private payload".equals(resources.getString(resourceId)), "private signed resources");
            ProbeData.check(evidence.matches(image), "private image unchanged after class/resource load");
            ProbeData.check(ledger.readVerified(runId).getActive() == null, "code load is not guest activation");

            result.put("complete", true).put("passed", true).put("run_id", runId)
                .put("api", Build.VERSION.SDK_INT).put("pid", Process.myPid())
                .put("abi", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown")
                .put("signed_private_cluster", true).put("dex_class_loader", true)
                .put("private_resources", true).put("activation_pending", true)
                .put("guest_context_executed", false).put("guest_activity_executed", false)
                .put("binder_provider_tested", false).put("social_accounts_tested", false);
        } catch (Throwable failure) {
            // No paths, package data, tokens, passwords, or raw stack traces in persisted diagnostics.
            try { result.put("complete", true).put("passed", false).put("run_id", runId)
                .put("failure_type", failure.getClass().getName()); }
            catch (Exception ignored) { /* parent times out if report cannot be written */ }
        } finally {
            if (reports != null) try {
                ProbeData.report(new File(reports, runId + ".json"), result);
            } catch (Exception ignored) { /* parent reports timeout/failure */ }
            stopSelf();
            Process.killProcess(Process.myPid()); // Never hot-swap guest code/hook policy.
        }
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
