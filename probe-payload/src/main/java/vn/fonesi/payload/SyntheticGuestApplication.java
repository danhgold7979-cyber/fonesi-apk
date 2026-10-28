package vn.fonesi.payload;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Pure synthetic Application lifecycle marker. Never starts real guests or uses account data.
 * A host-side test attaches this using public Instrumentation.newApplication with a scoped Context.
 */
public final class SyntheticGuestApplication extends Application {
    private static final String PACKAGE = "vn.fonesi.payload";
    private static final String PREFS = "synthetic_application";

    @Override public void onCreate() {
        super.onCreate();
        Context base = getBaseContext();
        if (base == null || !PACKAGE.equals(base.getPackageName()) ||
            getClass().getClassLoader() != base.getClassLoader()) {
            throw new IllegalStateException("Synthetic Application was not attached to its own code image");
        }
        int marker = base.getResources().getIdentifier("fonesi_guest_code_probe", "string", PACKAGE);
        if (marker == 0 || !"Fonesi signed private payload".equals(
                base.getResources().getString(marker))) {
            throw new IllegalStateException("Synthetic Application did not receive signed private resources");
        }
        SharedPreferences prefs = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getInt("starts", 0) != 0 ||
            !prefs.edit().putInt("starts", 1).commit()) {
            throw new IllegalStateException("Synthetic Application must run exactly one onCreate per fresh scope");
        }
    }
}
