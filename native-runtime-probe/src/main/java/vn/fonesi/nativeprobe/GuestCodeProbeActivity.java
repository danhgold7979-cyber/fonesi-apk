package vn.fonesi.nativeprobe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.File;
import java.util.UUID;

/** Synthetic technical probe, not a clone manager or a login surface. */
public final class GuestCodeProbeActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status;
    private File reports;
    private String runId;
    private long deadline;
    private boolean finished;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        status = new TextView(this);
        status.setPadding(24, 36, 24, 24);
        status.setText("Fonesi · Code image probe\nChưa chứng minh ứng dụng clone/tài khoản.");
        setContentView(status);
        try {
            reports = new File(getFilesDir(), "guest-code-probe").getCanonicalFile();
            if (saved != null) {
                runId = UUID.fromString(saved.getString("run_id")).toString();
                deadline = saved.getLong("deadline");
                ProbeData.check(deadline > 0, "missing saved deadline");
            } else {
                String requested = getIntent().getStringExtra(GuestCodeProbeService.EXTRA_RUN_ID);
                runId = requested == null ? UUID.randomUUID().toString() : UUID.fromString(requested).toString();
                deadline = SystemClock.elapsedRealtime() + 120_000L;
                startService(new Intent(this, GuestCodeProbeService.class)
                    .putExtra(GuestCodeProbeService.EXTRA_RUN_ID, runId));
            }
            main.post(this::poll);
        } catch (Throwable failure) {
            finished = true;
            status.setText("FAIL: không khởi tạo được bài thử code image (" +
                failure.getClass().getSimpleName() + ").");
        }
    }

    private void poll() {
        if (finished || isFinishing()) return;
        try {
            File file = new File(reports, runId + ".json");
            if (file.isFile()) {
                JSONObject report = ProbeData.report(file);
                ProbeData.check(runId.equals(report.optString("run_id")), "stale/mismatched probe receipt");
                ProbeData.check(report.optBoolean("complete"), "incomplete process receipt");
                finished = true;
                if (report.optBoolean("passed") && report.optBoolean("dex_class_loader") &&
                        report.optBoolean("private_resources") && report.optBoolean("activation_pending")) {
                    status.setText("PASS: tải DEX/tài nguyên APK thử nghiệm từ kho riêng trong process riêng.\n" +
                        "CHƯA kiểm chứng chạy Activity/Application/Binder hay clone tài khoản.\n" + report);
                } else {
                    status.setText("FAIL: Android code-image probe chưa đạt.\n" + report);
                }
            } else if (SystemClock.elapsedRealtime() >= deadline) {
                finished = true;
                status.setText("FAIL: không có biên nhận đúng run ID (process có thể crash/hết hạn).");
            } else {
                main.postDelayed(this::poll, 500L);
            }
        } catch (Throwable failure) {
            finished = true;
            status.setText("FAIL: báo cáo thử không hợp lệ (" + failure.getClass().getSimpleName() + ").");
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("run_id", runId);
        state.putLong("deadline", deadline);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
