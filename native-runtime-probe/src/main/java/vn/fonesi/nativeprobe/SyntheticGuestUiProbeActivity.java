package vn.fonesi.nativeprobe;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.view.View;
import android.widget.TextView;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.json.JSONObject;
import vn.fonesi.apk.PrivateApkCluster;
import vn.fonesi.apk.PrivateApkStore;
import vn.fonesi.engine.GuestCodeLoader;
import vn.fonesi.engine.LoadedGuestCode;
import vn.fonesi.engine.NativeRuntime;

/**
 * E24: real Android HOST Activity with an attached View created by independently signed,
 * private SYNTHETIC payload DEX. Lifecycle callbacks are relayed, not guest ActivityThread.
 * No arbitrary APK launch, UID sandbox, permissions, network, credentials or account data.
 */
public final class SyntheticGuestUiProbeActivity extends Activity {
    private static final String GUEST_PACKAGE = "vn.fonesi.payload";
    private static final String ENTRY = GUEST_PACKAGE + ".SyntheticGuestScreen";
    private static final String PREFIX = "FONESI-E24-PRIVATE-SCREEN:";
    private static final List<String> COMPLETE = Arrays.asList(
        "CREATE", "START", "RESUME", "PAUSE", "STOP", "DESTROY");
    private final List<String> androidEvents = new ArrayList<>();
    private File reports;
    private String runId;
    private String challenge;
    private Class<?> privateEntrypoint;
    private PrivateApkStore store;
    private PrivateApkCluster cluster;
    private View signedView;
    private boolean verifiedPrivateClass;
    private boolean verifiedPrivateResource;
    private boolean viewAttached;
    private boolean destroyed;
    private boolean reported;
    private String failureStage;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        androidEvents.add("CREATE");
        setContentView(new TextView(this) {{
            setText("Fonesi E24 signed PRIVATE synthetic View proof; no guest account.");
        }});
        try {
            // Fail closed on Activity recreation: never reuse a prior private classloader/process.
            if (saved != null) throw new IllegalStateException("Synthetic screen cannot hot-restart");
            String raw = getIntent().getStringExtra("run_id");
            runId = UUID.fromString(raw).toString();
            if (!runId.equals(raw)) throw new IllegalArgumentException("Noncanonical test run UUID");
            String process = Build.VERSION.SDK_INT >= 28 ? Application.getProcessName() : null;
            ProbeData.check((getPackageName() + ":fonesi_guest_ui").equals(process) &&
                Process.is64Bit() && Process.myUid() == getApplicationInfo().uid,
                "Host synthetic UI must run in its dedicated 64-bit process");
            File owned = getApplicationContext().getDataDir().getCanonicalFile();
            File files = getFilesDir().getCanonicalFile();
            ProbeData.check(files.getPath().startsWith(owned.getPath() + File.separator),
                "Receipt root must remain host private");
            reports = new File(files, "guest-ui-probe").getCanonicalFile();
            ProbeData.check((reports.isDirectory() || reports.mkdirs()) &&
                reports.getPath().startsWith(files.getPath() + File.separator),
                "Cannot create safe synthetic UI receipt directory");
            byte[] random = new byte[16];
            new SecureRandom().nextBytes(random);
            StringBuilder nonce = new StringBuilder(32);
            for (byte b : random) nonce.append(Character.forDigit((b >>> 4) & 15, 16))
                                       .append(Character.forDigit(b & 15, 16));
            challenge = nonce.toString();
            new Thread(this::prepare, "Fonesi-E24-signed-screen-loader").start();
        } catch (Throwable error) {
            failure("activity_create", error);
        }
    }

    private void prepare() {
        String stage = "signed_installed_synthetic_source";
        try {
            File owned = getApplicationContext().getDataDir().getCanonicalFile();
            File files = getFilesDir().getCanonicalFile();
            File code = new File(files, "e24-private-payload").getCanonicalFile();
            ProbeData.check(code.getPath().startsWith(files.getPath() + File.separator),
                "Private screen code store escaped app");
            PrivateApkStore localStore = new PrivateApkStore(code.toPath());
            stage = "private_signed_apk_publish_and_readback";
            PrivateApkCluster localCluster = localStore.publish(
                SyntheticSignedPayloadSource.fromInstalled(this), Build.VERSION.SDK_INT);
            ProbeData.check(localStore.open(localCluster.getId(), Build.VERSION.SDK_INT).equals(localCluster) &&
                localCluster.getApks().getBase().getPackageName().equals(GUEST_PACKAGE) &&
                localCluster.getApks().getSplits().isEmpty(),
                "Synthetic UI code must be a verified, single signed private payload APK");

            stage = "synthetic_dedicated_process_native_routing";
            File src = new File(files, "e24-inert-source").getCanonicalFile();
            File dst = new File(files, "e24-inert-target").getCanonicalFile();
            ProbeData.check((src.isDirectory() || src.mkdirs()) && (dst.isDirectory() || dst.mkdirs()),
                "Cannot create private inert native routes");
            Map<String, File> routes = new LinkedHashMap<>();
            routes.put(src.getPath(), dst);
            NativeRuntime.configure(getApplicationContext(), routes);
            ProbeData.check(NativeRuntime.state() == NativeRuntime.CONFIGURED,
                "E24 native routing configuration was not accepted");
            NativeRuntime.enable(getApplicationContext());
            ProbeData.check(NativeRuntime.state() == NativeRuntime.ENABLED,
                "E24 native routing could not be enabled");

            stage = "signed_private_dex_and_resources";
            LoadedGuestCode image = new GuestCodeLoader(getApplicationContext(), localStore)
                .load(localCluster.getId());
            ProbeData.check(GUEST_PACKAGE.equals(image.getPackageName()),
                "Signed private payload package mismatch");
            int marker = image.getResources().getIdentifier(
                "fonesi_guest_code_probe", "string", GUEST_PACKAGE);
            ProbeData.check(marker != 0 && "Fonesi signed private payload".equals(
                image.getResources().getString(marker)), "Private signed Resources marker not found");
            Class<?> screen = image.getClassLoader().loadClass(ENTRY);
            ProbeData.check(screen.getClassLoader() == image.getClassLoader(),
                "Signed private DEX, not host app classloader, must define the UI screen");
            try {
                getClassLoader().loadClass(ENTRY);
                throw new SecurityException("Synthetic screen was improperly packaged inside host APK");
            } catch (ClassNotFoundException required) { /* genuine private code */ }
            ProbeData.check(localStore.open(localCluster.getId(), Build.VERSION.SDK_INT)
                .equals(localCluster), "Private DEX/resources changed while preparing UI");
            runOnUiThread(() -> display(screen, localStore, localCluster));
        } catch (Throwable error) {
            final String failedAt = stage;
            runOnUiThread(() -> failure(failedAt, error));
        }
    }

    private void display(Class<?> screen, PrivateApkStore verifiedStore,
            PrivateApkCluster verifiedCluster) {
        if (destroyed || reported) return;
        try {
            if (!androidEvents.equals(Arrays.asList("CREATE", "START", "RESUME")))
                throw new IllegalStateException("Expected live/resumed Android host Activity");
            privateEntrypoint = screen;
            store = verifiedStore;
            cluster = verifiedCluster;
            signedView = (View) screen.getMethod("createView", Activity.class, String.class)
                .invoke(null, this, challenge);
            if (!(signedView instanceof TextView) ||
                !"FONESI-E24-SIGNED-PRIVATE-VIEW".equals(signedView.getTag()) ||
                !((TextView) signedView).getText().toString().equals(
                    PREFIX + hex(MessageDigest.getInstance("SHA-256").digest(
                        (PREFIX + challenge).getBytes(StandardCharsets.UTF_8)))))
                throw new SecurityException("Private signed View marker failed challenge verification");
            verifiedPrivateClass = true;
            verifiedPrivateResource = true;
            // The Android registered host Activity owns the Window. The private DEX created its View.
            setContentView(signedView);
            for (String event : androidEvents)
                screen.getMethod("lifecycle", String.class).invoke(null, event);
            signedView.post(() -> {
                if (destroyed || reported) return;
                try {
                    ProbeData.check(signedView.isAttachedToWindow() &&
                        signedView.getWindowToken() != null && signedView.getRootView() != null,
                        "Signed private View did not actually attach to the Android window");
                    ProbeData.check(store.open(cluster.getId(), Build.VERSION.SDK_INT).equals(cluster),
                        "Signed private UI APK changed after attachment");
                    viewAttached = true;
                    finish(); // Trigger real Android host PAUSE, STOP and DESTROY.
                } catch (Throwable error) {
                    failure("real_android_view_attachment", error);
                }
            });
        } catch (Throwable error) {
            failure("private_view_factory_or_lifecycle_replay", error);
        }
    }

    private void record(String event) {
        androidEvents.add(event);
        if (privateEntrypoint != null) {
            try {
                privateEntrypoint.getMethod("lifecycle", String.class).invoke(null, event);
            } catch (Throwable failure) {
                failure("host_lifecycle_relay_" + event, failure);
            }
        }
    }

    @Override protected void onStart() {
        super.onStart();
        record("START");
    }

    @Override protected void onResume() {
        super.onResume();
        record("RESUME");
    }

    @Override protected void onPause() {
        record("PAUSE");
        super.onPause();
    }

    @Override protected void onStop() {
        record("STOP");
        super.onStop();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        try {
            record("DESTROY");
            if (!reported) {
                ProbeData.check(viewAttached && verifiedPrivateClass && verifiedPrivateResource,
                    "Private View/code/resources were not all verified");
                ProbeData.check(COMPLETE.equals(androidEvents),
                    "Actual Android Activity lifecycle was incomplete");
                String privateSequence = (String) privateEntrypoint
                    .getMethod("completedTranscript").invoke(null);
                ProbeData.check(String.join(",", COMPLETE).equals(privateSequence),
                    "Synthetic private bytecode did not receive every host lifecycle callback");
                ProbeData.check(store.open(cluster.getId(), Build.VERSION.SDK_INT).equals(cluster) &&
                    NativeRuntime.state() == NativeRuntime.ENABLED, "Signed private image changed");
                JSONObject receipt = new JSONObject()
                    .put("complete", true).put("passed", true).put("run_id", runId)
                    .put("api", Build.VERSION.SDK_INT)
                    .put("pid", Process.myPid()).put("uid", Process.myUid())
                    .put("process_name", Build.VERSION.SDK_INT >= 28 ? Application.getProcessName() : "unsupported")
                    .put("real_host_activity_lifecycle_verified", true)
                    .put("signed_private_guest_view_rendered", true)
                    .put("private_guest_view_attached_to_window", true)
                    .put("private_guest_lifecycle_relay_verified", true)
                    .put("signed_private_resources_verified", true)
                    .put("signed_private_image_rechecked", true)
                    .put("lifecycle_transcript", privateSequence)
                    .put("same_android_uid", true).put("kernel_uid_isolation", false)
                    .put("actual_guest_activity_attached", false)
                    .put("third_party_guest_components_tested", false)
                    .put("social_accounts_tested", false);
                saveReceipt(receipt);
            }
        } catch (Throwable error) {
            failure("final_lifecycle_receipt", error);
        } finally {
            super.onDestroy();
        }
    }

    private void failure(String stage, Throwable error) {
        if (reported) return;
        failureStage = stage;
        try {
            JSONObject receipt = new JSONObject()
                .put("complete", true).put("passed", false).put("run_id", runId)
                .put("failure_stage", stage)
                .put("failure_type", error.getClass().getSimpleName())
                .put("actual_guest_activity_attached", false);
            saveReceipt(receipt);
        } catch (Throwable ignored) {
            // Parent runner treats a missing receipt as a hard failure, never PASS.
        }
        if (!isFinishing()) finish();
    }

    private void saveReceipt(JSONObject receipt) throws Exception {
        if (reported) return;
        if (reports == null || runId == null || !UUID.fromString(runId).toString().equals(runId))
            throw new IllegalStateException("No validated private E24 receipt location");
        ProbeData.report(new File(reports, runId + ".json"), receipt);
        reported = true;
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(Character.forDigit((b >>> 4) & 15, 16))
                                .append(Character.forDigit(b & 15, 16));
        return out.toString();
    }
}
