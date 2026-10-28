package vn.fonesi.nativeprobe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.TextView;
import java.io.File;
import java.util.UUID;
import org.json.JSONObject;

/** Starts the E21 test process and displays only a synthetic signed-APK receipt. */
public final class SplitCodeProbeActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private String runId;
    private String proof;
    private long deadline;
    private boolean finished;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        status = new TextView(this);
        status.setPadding(24, 36, 24, 24);
        status.setText("Fonesi E21/E22 · signed isolated synthetic APK proof");
        setContentView(status);
        try {
            if (saved != null) {
                runId = UUID.fromString(saved.getString("run_id")).toString();
                proof = saved.getString("proof_level");
                deadline = saved.getLong("deadline");
            } else {
                proof = getIntent().getStringExtra("proof_level");
                if (proof == null) proof = "e21";
                String requested = getIntent().getStringExtra("run_id");
                runId = requested == null ? UUID.randomUUID().toString()
                    : UUID.fromString(requested).toString();
                if (requested != null && !runId.equals(requested))
                    throw new IllegalArgumentException("Noncanonical run ID");
                deadline = SystemClock.elapsedRealtime() + 150_000L;
                startService(new Intent(this, SplitCodeProbeService.class)
                    .putExtra(SplitCodeProbeService.EXTRA_RUN_ID, runId)
                    .putExtra("proof_level", proof));
            }
            ProbeData.check(deadline > 0 && ("e21".equals(proof) || "e22".equals(proof)),
                "Missing or invalid synthetic proof level/deadline");
            handler.post(this::poll);
        } catch (Throwable failure) {
            finished = true;
            status.setText("FAIL: E21 test Activity could not start.");
        }
    }

    private void poll() {
        if (finished || isFinishing()) return;
        try {
            File report = new File(new File(getFilesDir(), "split-code-probe"), runId + ".json");
            if (report.isFile()) {
                JSONObject value = ProbeData.report(report);
                ProbeData.check(value.optBoolean("complete") && runId.equals(value.optString("run_id")),
                    "Stale/incomplete synthetic split receipt");
                finished = true;
                status.setText((value.optBoolean("passed") ? "PASS" : "FAIL") +
                    " · " + proof + " isolated split test. Not third-party clone/account isolation.\n" + value);
            } else if (SystemClock.elapsedRealtime() >= deadline) {
                finished = true;
                status.setText("FAIL: no E21 signed split receipt before timeout.");
            } else {
                handler.postDelayed(this::poll, 500L);
            }
        } catch (Throwable failure) {
            finished = true;
            status.setText("FAIL: E21 receipt invalid: " + failure.getClass().getSimpleName());
        }
    }

    @Override protected void onSaveInstanceState(Bundle saved) {
        saved.putString("run_id", runId);
        saved.putString("proof_level", proof);
        saved.putLong("deadline", deadline);
        super.onSaveInstanceState(saved);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
