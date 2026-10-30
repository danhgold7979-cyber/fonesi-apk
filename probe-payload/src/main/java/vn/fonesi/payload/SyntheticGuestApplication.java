package vn.fonesi.payload;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import java.util.UUID;

/**
 * Pure synthetic Application lifecycle marker. Never starts real guests or uses account data.
 * A host-side test attaches this using public Instrumentation.newApplication with a scoped Context.
 */
public final class SyntheticGuestApplication extends Application {
    private static final String PACKAGE = "vn.fonesi.payload";
    private static final String PREFS = "synthetic_application";
    // One token per signed payload classloader/process, shared by both scope Application instances.
    private static final String PROCESS_TOKEN = UUID.randomUUID().toString();
    /** Test-only process identity; not a security credential or OS isolation token. */
    public String processToken() { return PROCESS_TOKEN; }

    @Override public void onCreate() {
        super.onCreate();
        Context base = getBaseContext();
        if (base == null || !PACKAGE.equals(base.getPackageName()) ||
            getClass().getClassLoader() != base.getClassLoader()) {
            throw new IllegalStateException("Synthetic Application was not attached to its own code image");
        }
        int marker = base.getResources().getIdentifier("fonesi_guest_code_probe", "string", PACKAGE);
        if (marker == 0 || !"Fonesi signed private payload".equals(base.getResources().getString(marker))) {
            throw new IllegalStateException("Synthetic Application did not receive signed private resources");
        }
        SharedPreferences prefs = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SyntheticApplicationProcessMarker.State next = SyntheticApplicationProcessMarker.advance(
            prefs.getInt("starts", 0), prefs.getInt("last_pid", -1),
            prefs.getString("last_process_token", null), Process.myPid(), PROCESS_TOKEN);
        if (!prefs.edit().putInt("starts", next.getStarts()).putInt("last_pid", next.getPid())
                .putString("last_process_token", next.getProcessToken()).commit()) {
            throw new IllegalStateException("Synthetic Application process marker commit failed");
        }
    }
}
