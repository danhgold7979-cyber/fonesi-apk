package vn.fonesi.nativeprobe;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/** Host-side, explicit test Activity proving a remote internal Binder round-trip only. */
public final class SyntheticChannelProbeActivity extends Activity {
    private static final String DESCRIPTOR = "vn.fonesi.nativeprobe.ISyntheticGuestChannel";
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status;
    private File reports;
    private String runId;
    private boolean bound;
    private boolean finished;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName component, IBinder binder) {
            new Thread(() -> verify(component, binder), "Fonesi-synthetic-binder-check").start();
        }
        @Override public void onServiceDisconnected(ComponentName component) {
            fail("Unexpected synthetic child process disconnect");
        }
        @Override public void onBindingDied(ComponentName component) {
            fail("Synthetic child Binder died");
        }
        @Override public void onNullBinding(ComponentName component) {
            fail("No synthetic child Binder returned");
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        status = new TextView(this);
        status.setPadding(24, 36, 24, 24);
        status.setText("Fonesi internal Binder probe\nSynthetic data only; no guest accounts.");
        setContentView(status);
        try {
            String requested = saved != null ? saved.getString("run_id") : getIntent().getStringExtra("run_id");
            runId = requested == null ? UUID.randomUUID().toString() : UUID.fromString(requested).toString();
            reports = new File(getFilesDir(), "binder-probe").getCanonicalFile();
            if (!reports.isDirectory() && !reports.mkdirs()) throw new IllegalStateException("No private report directory");
            File canonicalFiles = getFilesDir().getCanonicalFile();
            if (!reports.getPath().startsWith(canonicalFiles.getPath() + File.separator))
                throw new IllegalStateException("Report escaped private storage");
            Intent explicit = new Intent(this, SyntheticGuestChannelService.class);
            if (!bindService(explicit, connection, Context.BIND_AUTO_CREATE))
                throw new IllegalStateException("Android refused internal channel binding");
            bound = true;
            main.postDelayed(() -> fail("Synthetic Binder response timed out"), 90_000L);
        } catch (Throwable error) {
            fail("Synthetic Binder setup failed: " + error.getClass().getSimpleName());
        }
    }

    private void verify(ComponentName component, IBinder binder) {
        try {
            if (!getPackageName().equals(component.getPackageName()) ||
                !SyntheticGuestChannelService.class.getName().equals(component.getClassName()))
                throw new SecurityException("Unexpected bound component");
            if (!DESCRIPTOR.equals(binder.getInterfaceDescriptor()) ||
                binder.queryLocalInterface(DESCRIPTOR) != null)
                throw new SecurityException("Expected a remote-process AIDL proxy");
            if (Build.VERSION.SDK_INT >= 28 && !getPackageName().equals(Application.getProcessName()))
                throw new SecurityException("Client Activity is not in the host main process");
            if (Process.myUid() != getApplicationInfo().uid)
                throw new SecurityException("Host UID changed");

            ISyntheticGuestChannel channel = ISyntheticGuestChannel.Stub.asInterface(binder);
            String challenge = UUID.randomUUID().toString().replace("-", "");
            String expected = SyntheticGuestChannelService.hex(MessageDigest.getInstance("SHA-256")
                .digest((SyntheticGuestChannelService.DOMAIN + challenge)
                    .getBytes(StandardCharsets.UTF_8)));
            String response = channel.exchange(challenge);
            String[] values = response != null ? response.split("\\|", -1) : new String[0];
            if (values.length != 4 || !"E15".equals(values[0]) || !expected.equals(values[1]))
                throw new IllegalStateException("Synthetic channel challenge response mismatch");
            int remotePid = Integer.parseInt(values[2]);
            int remoteUid = Integer.parseInt(values[3]);
            if (remotePid <= 0 || remotePid == Process.myPid() || remoteUid != Process.myUid())
                throw new IllegalStateException("Binder did not run in a separate same-UID process");

            // A distinct AIDL request must execute the project-owned code from a signed,
            // independently published private DEX image inside that same Binder process.
            String guestChallenge = UUID.randomUUID().toString().replace("-", "");
            String guestExpected = SyntheticGuestChannelService.hex(MessageDigest.getInstance("SHA-256")
                .digest(("FONESI-E12-SYNTHETIC-V1:" + guestChallenge).getBytes(StandardCharsets.UTF_8)));
            String guestResponse = channel.exchangeGuest(guestChallenge);
            String[] guestParts = guestResponse != null ? guestResponse.split("\\|", -1) : new String[0];
            if (guestParts.length != 4 || !"E16".equals(guestParts[0]) ||
                !guestExpected.equals(guestParts[1]) ||
                Integer.parseInt(guestParts[2]) != remotePid ||
                Integer.parseInt(guestParts[3]) != remoteUid)
                throw new IllegalStateException("Remote private guest bytecode proof mismatch");

            String appChallenge = UUID.randomUUID().toString().replace("-", "");
            String appExpected = SyntheticGuestChannelService.hex(MessageDigest.getInstance("SHA-256")
                .digest(("FONESI-E12-SYNTHETIC-V1:" + appChallenge).getBytes(StandardCharsets.UTF_8)));
            String appResponse = channel.exchangeGuestApplication(appChallenge);
            String[] appParts = appResponse != null ? appResponse.split("\\|", -1) : new String[0];
            if (appParts.length != 5 || !"E17".equals(appParts[0]) ||
                !appExpected.equals(appParts[1]) ||
                Integer.parseInt(appParts[2]) != remotePid ||
                Integer.parseInt(appParts[3]) != remoteUid ||
                !"1".equals(appParts[4]))
                throw new IllegalStateException("Synthetic private Application attach/onCreate proof mismatch");

            JSONObject report = new JSONObject().put("complete", true).put("passed", true)
                .put("run_id", runId).put("api", Build.VERSION.SDK_INT)
                .put("host_pid", Process.myPid()).put("remote_pid", remotePid)
                .put("host_uid", Process.myUid()).put("remote_uid", remoteUid)
                .put("remote_binder_verified", true).put("challenge_response_verified", true)
                .put("remote_process_distinct", true).put("same_android_uid", true)
                .put("private_guest_bytecode_via_binder_verified", true)
                .put("private_guest_resources_via_binder_verified", true)
                .put("synthetic_guest_application_attached", true)
                .put("synthetic_guest_application_on_create_verified", true)
                .put("guest_application_attached", false) // No ActivityThread/PackageManager guest registration.
                .put("full_guest_application_lifecycle_verified", false)
                .put("guest_activity_executed", false)
                .put("third_party_binder_virtualized", false)
                .put("social_accounts_tested", false).put("kernel_uid_isolation", false);
            main.post(() -> complete(report));
        } catch (Throwable failure) {
            fail("Synthetic Binder exchange failed: " + failure.getClass().getSimpleName());
        }
    }

    private void fail(String detail) {
        main.post(() -> {
            try {
                String safeReason = detail.replaceAll("[^a-zA-Z0-9 _:-]", "");
                complete(new JSONObject().put("complete", true).put("passed", false)
                    .put("run_id", runId).put("failure_stage", "internal_binder")
                    .put("failure_type", safeReason.substring(0, Math.min(safeReason.length(), 100))));
            } catch (Exception ignored) { /* no stale success if a failure cannot be saved */ }
        });
    }

    private void complete(JSONObject report) {
        if (finished) return;
        finished = true;
        main.removeCallbacksAndMessages(null);
        try {
            if (reports == null || runId == null) throw new IllegalStateException("Missing report state");
            ProbeData.report(new File(reports, runId + ".json"), report);
            status.setText((report.optBoolean("passed") ? "PASS" : "FAIL") +
                " · Internal synthetic Binder IPC\nNo real guest Application/Activity or account isolation.\n" + report);
        } catch (Exception error) {
            status.setText("FAIL · Synthetic channel report could not be saved.");
        }
        if (bound) {
            bound = false;
            unbindService(connection);
        }
    }

    @Override protected void onSaveInstanceState(Bundle saved) {
        saved.putString("run_id", runId);
        super.onSaveInstanceState(saved);
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (bound) {
            bound = false;
            unbindService(connection);
        }
        super.onDestroy();
    }
}
