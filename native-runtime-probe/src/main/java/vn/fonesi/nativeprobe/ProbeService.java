package vn.fonesi.nativeprobe;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import vn.fonesi.engine.NativeRuntime;

public abstract class ProbeService extends Service {
    private boolean started;
    protected abstract String marker();
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (started || intent == null) return START_NOT_STICKY;
        started = true;
        final String directory = intent.getStringExtra("scope");
        new Thread(() -> runProbe(directory), "Fonesi-native-probe").start();
        return START_NOT_STICKY;
    }
    private void runProbe(String directory) {
        File scope = null;
        JSONObject result = new JSONObject();
        try {
            scope = new File(directory).getCanonicalFile();
            File root = new File(getFilesDir(), "native-probe").getCanonicalFile();
            ProbeData.check(scope.getPath().startsWith(root.getPath() + File.separator), "probe owned scope");
            String label = marker(); File original = new File(scope, "original"); File guest = new File(scope, label);
            ProbeData.check("ORIGINAL".equals(ProbeData.marker(original)), "source marker before hooks");
            Map<String, File> map = new LinkedHashMap<>(); map.put(original.getPath(), guest);
            NativeRuntime.configure(getApplicationContext(), map);
            ProbeData.check(NativeRuntime.state() == NativeRuntime.CONFIGURED, "configure does not enable hooks");
            NativeRuntime.enable(getApplicationContext());
            ProbeData.check(label.equals(ProbeData.marker(original)), "Java file input routes to guest");
            ProbeData.check(label.equals(ProbeData.database(original)), "Android SQLite reads guest database");
            ProbeData.marker(original, label + "_UPDATED"); ProbeData.database(original, label + "_UPDATED");
            ProbeData.check((label + "_UPDATED").equals(ProbeData.marker(guest)), "file output reaches owned guest");
            ProbeData.check((label + "_UPDATED").equals(ProbeData.database(guest)), "SQLite update reaches owned guest");
            result.put("passed", true).put("marker", label).put("native_state", NativeRuntime.state()).put("pid", Process.myPid());
        } catch (Throwable failure) {
            Log.e("FonesiNativeProbe", "Synthetic native check failed", failure);
            try { result.put("passed", false).put("error", failure.getClass().getName()).put("message", String.valueOf(failure.getMessage())); }
            catch (Exception ignored) { /* Main process times out if no valid report can be persisted. */ }
        }
        if (scope != null) try { ProbeData.report(new File(scope, marker() + ".json"), result); }
        catch (Exception failure) { Log.e("FonesiNativeProbe", "Could not persist synthetic result", failure); }
        stopSelf();
        Process.killProcess(Process.myPid()); // Dedicated synthetic process only; no hot-swapping native policy.
    }
    public static final class A extends ProbeService { @Override protected String marker() { return "A"; } }
    public static final class B extends ProbeService { @Override protected String marker() { return "B"; } }
}
