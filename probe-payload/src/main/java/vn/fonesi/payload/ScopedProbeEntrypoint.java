package vn.fonesi.payload;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Project-controlled, synthetic-only scoped storage exercise.
 * The Context is supplied by a host-side test; this does NOT attach a guest Android Application,
 * launch an Activity, mediate Binder, or prove isolation of third-party account secrets.
 */
public final class ScopedProbeEntrypoint {
    private static final String PACKAGE = "vn.fonesi.payload";
    private static final String PREFS = "synthetic";
    private static final String FILE = "sample.txt";
    private static final String DB = "sample.sqlite";
    private ScopedProbeEntrypoint() {}

    public static void write(Context guest, String marker) throws Exception {
        if (marker == null || !marker.matches("[0-9a-f]{32}"))
            throw new IllegalArgumentException("Invalid synthetic marker");
        checkResourceIdentity(guest);
        SharedPreferences prefs = guest.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.edit().putString("marker", marker).commit())
            throw new IllegalStateException("Synthetic preference write failed");
        try (FileOutputStream out = guest.openFileOutput(FILE, Context.MODE_PRIVATE)) {
            out.write(marker.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        File database = guest.getDatabasePath(DB);
        try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(database, null)) {
            db.execSQL("CREATE TABLE IF NOT EXISTS marker (id INTEGER PRIMARY KEY, value TEXT NOT NULL)");
            ContentValues row = new ContentValues();
            row.put("id", 1);
            row.put("value", marker);
            if (db.insertWithOnConflict("marker", null, row, SQLiteDatabase.CONFLICT_REPLACE) != 1)
                throw new IllegalStateException("Synthetic SQLite write failed");
        }
    }

    /** Empty indicates a new, untouched scope. Every other result must agree across all stores. */
    public static String read(Context guest) throws Exception {
        checkResourceIdentity(guest);
        String preference = guest.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("marker", null);
        File file = guest.getFileStreamPath(FILE);
        File database = guest.getDatabasePath(DB);
        if (preference == null && !file.exists() && !database.exists()) return "EMPTY";
        if (preference == null || !file.isFile() || !database.isFile())
            throw new IllegalStateException("Incomplete synthetic scope state");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (FileInputStream input = guest.openFileInput(FILE)) {
            byte[] buffer = new byte[64];
            for (int count; (count = input.read(buffer)) != -1; ) {
                if (bytes.size() + count > 64) throw new IllegalStateException("Oversized synthetic marker");
                bytes.write(buffer, 0, count);
            }
        }
        String fileMarker = bytes.toString(StandardCharsets.UTF_8.name());
        String databaseMarker;
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(
                database.getPath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor cursor = db.rawQuery("SELECT value FROM marker WHERE id=1", null)) {
            if (!cursor.moveToFirst() || cursor.getCount() != 1)
                throw new IllegalStateException("Missing synthetic SQLite marker");
            databaseMarker = cursor.getString(0);
        }
        if (!preference.matches("[0-9a-f]{32}") ||
                !preference.equals(fileMarker) || !preference.equals(databaseMarker))
            throw new IllegalStateException("Synthetic scope file/preference/SQLite state diverged");
        return preference;
    }

    private static void checkResourceIdentity(Context guest) throws Exception {
        if (!PACKAGE.equals(guest.getPackageName()))
            throw new IllegalStateException("Synthetic scope package mismatch");
        int resource = guest.getResources().getIdentifier(
            "fonesi_guest_code_probe", "string", PACKAGE);
        if (resource == 0 || !"Fonesi signed private payload".equals(
                guest.getResources().getString(resource)))
            throw new IllegalStateException("Synthetic scope resources are not private APK resources");
        if (ScopedProbeEntrypoint.class.getClassLoader() != guest.getClassLoader())
            throw new IllegalStateException("Synthetic scope is not using its private guest loader");
    }
}
