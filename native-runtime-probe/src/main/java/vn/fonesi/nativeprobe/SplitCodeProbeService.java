package vn.fonesi.nativeprobe;

import android.app.Application;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import java.io.File;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import vn.fonesi.apk.PrivateApkCluster;
import vn.fonesi.apk.PrivateApkStore;
import vn.fonesi.engine.IsolatedSplitCodeProbeLoader;
import vn.fonesi.engine.DependentFeatureSplitProbeLoader;
import vn.fonesi.engine.NativeRuntime;

/** E21 synthetic-only service. Not exported; no real APK components or accounts are dispatched. */
public final class SplitCodeProbeService extends Service {
    static final String EXTRA_RUN_ID = "run_id";
    private final AtomicBoolean attempted = new AtomicBoolean(false);

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !attempted.compareAndSet(false, true)) return START_NOT_STICKY;
        final String id;
        final String proof;
        try {
            String requested = intent.getStringExtra("proof_level");
            proof = requested == null ? "e21" : requested;
            if (!"e21".equals(proof) && !"e22".equals(proof))
                throw new IllegalArgumentException("Unsupported synthetic proof level");
            id = UUID.fromString(intent.getStringExtra(EXTRA_RUN_ID)).toString();
            if (!id.equals(intent.getStringExtra(EXTRA_RUN_ID))) throw new IllegalArgumentException();
        } catch (Exception invalid) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        new Thread(() -> execute(id, proof, startId), "Fonesi-private-splits").start();
        return START_NOT_STICKY;
    }

    private void execute(String runId, String proof, int startId) {
        String stage = "process_identity";
        File reports = null;
        JSONObject result = new JSONObject();
        try {
            final String processName = Build.VERSION.SDK_INT >= 28 ? Application.getProcessName() : null;
            ProbeData.check((getPackageName() + ":fonesi_guest_split").equals(processName),
                "E21 must run in its explicitly named guest process");
            ProbeData.check(Process.is64Bit() && Process.myUid() == getApplicationInfo().uid,
                "E21 process ABI/UID mismatch");
            stage = "host_private_paths";
            File owned = getApplicationContext().getDataDir().getCanonicalFile();
            File files = getFilesDir().getCanonicalFile();
            ProbeData.check(files.getPath().startsWith(owned.getPath() + File.separator),
                "Private files path escaped app");
            reports = new File(files, "split-code-probe").getCanonicalFile();
            ProbeData.check(reports.isDirectory() || reports.mkdirs(), "No private receipt directory");
            ProbeData.check(reports.getPath().startsWith(files.getPath() + File.separator),
                "Private receipt escaped host files");

            stage = "installed_signed_split_identity";
            vn.fonesi.core.InstalledSource input = SyntheticSignedSplitSource.fromInstalled(this, "e22".equals(proof));
            stage = "copy_and_audit_signed_private_split_cluster";
            File privateRoot = new File(files, "e21-private-images").getCanonicalFile();
            ProbeData.check(privateRoot.getPath().startsWith(files.getPath() + File.separator),
                "Private APK image store escaped host files");
            PrivateApkStore store = new PrivateApkStore(privateRoot.toPath());
            PrivateApkCluster cluster = store.publish(input, Build.VERSION.SDK_INT);
            ProbeData.check(store.open(cluster.getId(), Build.VERSION.SDK_INT).equals(cluster),
                "Private APK split image identity changed");

            stage = "enable_synthetic_native_routing";
            File source = new File(files, "e21-sample-source").getCanonicalFile();
            File target = new File(files, "e21-sample-target").getCanonicalFile();
            ProbeData.check((source.isDirectory() || source.mkdirs()) &&
                (target.isDirectory() || target.mkdirs()), "Cannot create synthetic-only native routes");
            Map<String, File> routes = new LinkedHashMap<>();
            routes.put(source.getPath(), target);
            NativeRuntime.configure(getApplicationContext(), routes);
            ProbeData.check(NativeRuntime.state() == NativeRuntime.CONFIGURED,
                "Native route configuration not accepted");
            NativeRuntime.enable(getApplicationContext());
            ProbeData.check(NativeRuntime.state() == NativeRuntime.ENABLED,
                "Native route is not enabled");

            stage = "private_isolated_dex_resources_and_challenge";
            byte[] random = new byte[16];
            new SecureRandom().nextBytes(random);
            StringBuilder nonce = new StringBuilder(32);
            for (byte b : random) nonce.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            if ("e22".equals(proof)) {
                new DependentFeatureSplitProbeLoader(getApplicationContext(), store)
                    .verify(cluster.getId(), nonce.toString());
            } else {
                new IsolatedSplitCodeProbeLoader(getApplicationContext(), store)
                    .verify(cluster.getId(), nonce.toString());
            }
            ProbeData.check(store.open(cluster.getId(), Build.VERSION.SDK_INT).equals(cluster),
                "Signed image readback after execution failed");

            stage = "write_success_receipt";
            result.put("complete", true).put("passed", true).put("run_id", runId)
                .put("proof_level", proof)
                .put("api", Build.VERSION.SDK_INT).put("pid", Process.myPid())
                .put("uid", Process.myUid()).put("process_name", processName)
                .put("private_split_graph_verified", true)
                .put("private_dependency_graph_verified", "e22".equals(proof))
                .put("private_dependent_feature_code_executed", "e22".equals(proof))
                .put("private_dependent_resources_verified", "e22".equals(proof))
                .put("private_isolated_base_code_executed", true)
                .put("private_isolated_feature_code_executed", true)
                .put("private_isolated_resources_verified", true)
                .put("signed_private_cluster_rechecked", true)
                .put("same_android_uid", true).put("kernel_uid_isolation", false)
                .put("guest_activity_executed", false)
                .put("third_party_guest_components_tested", false)
                .put("social_accounts_tested", false);
        } catch (Throwable failure) {
            try {
                result.put("complete", true).put("passed", false).put("run_id", runId)
                    .put("failure_stage", stage).put("failure_type", failure.getClass().getSimpleName());
            } catch (Exception ignored) { /* A missing receipt will cause test FAIL. */ }
        } finally {
            if (reports != null) try {
                ProbeData.report(new File(reports, runId + ".json"), result);
            } catch (Exception ignored) { /* Parent fails closed if no valid receipt. */ }
            stopSelf(startId);
            Process.killProcess(Process.myPid()); // No unsafe hot swap of native hooks or private DEX.
        }
    }
}
