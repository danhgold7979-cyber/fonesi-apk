package vn.fonesi.engine;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Native component only. Host providers must also verify guest lifecycle and compatibility. */
public final class NativeRuntime {
    public static final int EMPTY = 0, CONFIGURED = 1, ENABLED = 2, FAILED = 3;
    private static boolean loaded;
    private NativeRuntime() {}

    public static synchronized void configure(Context hostContext, Map<String, File> bindings) throws IOException {
        requireGuestProcess(hostContext);
        File owned = hostContext.getDataDir().getCanonicalFile();
        String prefix = owned.getPath() + File.separator;
        Map<String, String> checked = new LinkedHashMap<>();
        for (Map.Entry<String, File> binding : bindings.entrySet()) {
            String from = binding.getKey();
            if (from == null || !from.startsWith("/") || from.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid guest source path");
            String to = binding.getValue().getCanonicalPath();
            if (!to.startsWith(prefix)) throw new IllegalArgumentException("Guest destination must belong to the host private directory");
            checked.put(from, to);
        }
        load();
        configureNative(owned.getPath(), checked.keySet().toArray(new String[0]), checked.values().toArray(new String[0]));
    }

    public static synchronized void enable(Context hostContext) {
        requireGuestProcess(hostContext);
        if (!loaded || !enableNative() || stateNative() != ENABLED)
            throw new IllegalStateException("Native runtime is not ready; guest must not be started");
    }
    public static synchronized int state() { return loaded ? stateNative() : EMPTY; }
    private static void load() {
        if (!loaded) { System.loadLibrary("fonesi_runtime"); loaded = true; }
    }
    private static void requireGuestProcess(Context context) {
        String name = null;
        if (Build.VERSION.SDK_INT >= 28) name = Application.getProcessName();
        else {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null && manager.getRunningAppProcesses() != null)
                for (ActivityManager.RunningAppProcessInfo process : manager.getRunningAppProcesses())
                    if (process.pid == Process.myPid()) { name = process.processName; break; }
        }
        if (name == null || !name.startsWith(context.getPackageName() + ":fonesi_guest_"))
            throw new IllegalStateException("Native routing is restricted to a dedicated Fonesi guest process");
    }
    private static native void configureNative(String root, String[] from, String[] to);
    private static native boolean enableNative();
    private static native int stateNative();
}
