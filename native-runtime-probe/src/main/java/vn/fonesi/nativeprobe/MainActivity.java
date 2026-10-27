package vn.fonesi.nativeprobe;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.WindowManager;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.File;
import java.util.UUID;
import vn.fonesi.engine.NativeRuntime;

public final class MainActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status;
    private File root;
    private File scope;
    private long deadline;
    private boolean finished;
    private boolean dispatched;
    private String runId;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        status = new TextView(this); status.setPadding(28, 40, 28, 28); setContentView(status);
        status.setText("Fonesi native probe\nĐang chuẩn bị dữ liệu file/SQLite mẫu…");
        try {
            String requested = state != null ? state.getString("run_id") : getIntent().getStringExtra("run_id");
            runId = requested == null ? UUID.randomUUID().toString() : UUID.fromString(requested).toString();
            if (state != null && state.getString("scope") != null) {
                root = new File(getFilesDir(), "native-probe").getCanonicalFile();
                scope = new File(root, UUID.fromString(state.getString("scope")).toString());
                dispatched = true; deadline = SystemClock.elapsedRealtime() + 120_000;
                main.post(this::poll); return;
            }
        } catch (Exception error) { finishFailure(error); return; }
        new Thread(() -> {
            try {
                root = new File(getFilesDir(), "native-probe").getCanonicalFile();
                scope = new File(root, UUID.randomUUID().toString());
                ProbeData.check(scope.mkdirs(), "create fresh synthetic scope");
                for (String label : new String[]{"original", "A", "B"}) {
                    File directory = new File(scope, label); ProbeData.check(directory.mkdir(), "create synthetic directory");
                    String value = label.equals("original") ? "ORIGINAL" : label;
                    ProbeData.marker(directory, value); ProbeData.database(directory, value);
                }
                ProbeData.check(NativeRuntime.state() == NativeRuntime.EMPTY, "host process never loaded hooks");
                ProbeData.report(new File(root, "latest.json"), new JSONObject().put("complete", false).put("run_id", runId).put("scope", scope.getName()));
                main.post(() -> { if (!isDestroyed()) startChecks(); });
            } catch (Throwable error) { main.post(() -> { if (!isDestroyed()) finishFailure(error); }); }
        }, "Fonesi-probe-setup").start();
    }
    private void startChecks() {
        try {
            dispatched = true;
            startService(new Intent(this, ProbeService.A.class).putExtra("scope", scope.getPath()));
            startService(new Intent(this, ProbeService.B.class).putExtra("scope", scope.getPath()));
            deadline = SystemClock.elapsedRealtime() + 120_000;
            status.setText("Fonesi native probe\nĐang thử JNI/file/SQLite trong hai tiến trình riêng…");
            main.post(this::poll);
        } catch (Throwable error) { finishFailure(error); }
    }
    private void poll() {
        if (finished || isFinishing()) return;
        File first = new File(scope, "A.json"), second = new File(scope, "B.json");
        try {
            if (first.isFile() && second.isFile()) {
                JSONObject a = ProbeData.report(first), b = ProbeData.report(second);
                ProbeData.check(a.optBoolean("passed") && b.optBoolean("passed"), "both native/SQLite checks must pass: " + a + b);
                ProbeData.check(a.getInt("pid") != b.getInt("pid"), "independent native processes");
                ProbeData.check("ORIGINAL".equals(ProbeData.marker(new File(scope, "original"))), "original file preserved");
                ProbeData.check("ORIGINAL".equals(ProbeData.database(new File(scope, "original"))), "original database preserved");
                for (String label : new String[]{"A", "B"}) {
                    File directory = new File(scope, label);
                    ProbeData.check((label + "_UPDATED").equals(ProbeData.marker(directory)), "independent final file");
                    ProbeData.check((label + "_UPDATED").equals(ProbeData.database(directory)), "independent final database");
                }
                ProbeData.check(NativeRuntime.state() == NativeRuntime.EMPTY, "host process remains unhooked");
                JSONObject result = new JSONObject().put("complete", true).put("passed", true).put("api", Build.VERSION.SDK_INT)
                    .put("run_id", runId)
                    .put("abi", Build.SUPPORTED_ABIS[0]).put("A", a).put("B", b).put("original_preserved", true)
                    .put("social_accounts_tested", false).put("full_clone_engine_tested", false);
                ProbeData.report(new File(root, "latest.json"), result);
                finished = true; status.setText("PASS · JNI/file/SQLite mẫu độc lập.\nChưa thử ứng dụng clone hoặc tài khoản mạng xã hội.\n" + result);
            } else if (SystemClock.elapsedRealtime() >= deadline) finishFailure(new IllegalStateException("Native child result missing or process crashed"));
            else main.postDelayed(this::poll, 500);
        } catch (Throwable error) { finishFailure(error); }
    }
    private void finishFailure(Throwable error) {
        if (finished) return;
        finished = true;
        try {
            JSONObject report = new JSONObject().put("complete", true).put("passed", false).put("error", error.getClass().getName())
                .put("run_id", runId)
                .put("message", String.valueOf(error.getMessage()));
            if (root != null) ProbeData.report(new File(root, "latest.json"), report);
            status.setText("FAIL · Giữ kết quả chưa đạt.\n" + report);
        } catch (Exception ignored) { status.setText("FAIL · Chưa ghi được kết quả thử."); }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("run_id", runId);
        if (dispatched && scope != null) out.putString("scope", scope.getName());
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() { main.removeCallbacksAndMessages(null); super.onDestroy(); }
}
