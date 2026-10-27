package vn.fonesi.nativeprobe;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class ProbeData {
    private ProbeData() {}
    static void check(boolean value, String scenario) { if (!value) throw new IllegalStateException(scenario); }
    static void marker(File directory, String value) throws IOException {
        try (FileOutputStream out = new FileOutputStream(new File(directory, "marker"))) {
            out.write(value.getBytes(StandardCharsets.UTF_8)); out.getFD().sync();
        }
    }
    static String marker(File directory) throws IOException {
        try (FileInputStream in = new FileInputStream(new File(directory, "marker"))) {
            byte[] value = new byte[128]; int count = in.read(value);
            check(count >= 0 && in.read() == -1, "bounded marker");
            return new String(value, 0, count, StandardCharsets.UTF_8);
        }
    }
    static void database(File directory, String value) {
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(new File(directory, "account.db"), null);
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS account (id INTEGER PRIMARY KEY, label TEXT NOT NULL)");
            ContentValues row = new ContentValues(); row.put("id", 1); row.put("label", value);
            check(db.insertWithOnConflict("account", null, row, SQLiteDatabase.CONFLICT_REPLACE) == 1, "database write");
        } finally { db.close(); }
    }
    static String database(File directory) {
        SQLiteDatabase db = SQLiteDatabase.openDatabase(new File(directory, "account.db").getPath(), null, SQLiteDatabase.OPEN_READONLY);
        try (Cursor cursor = db.rawQuery("SELECT label FROM account WHERE id=1", null)) {
            check(cursor.moveToFirst(), "database marker exists"); return cursor.getString(0);
        } finally { db.close(); }
    }
    static void report(File file, JSONObject value) throws IOException {
        AtomicFile atomic = new AtomicFile(file); FileOutputStream stream = atomic.startWrite();
        try { stream.write(value.toString().getBytes(StandardCharsets.UTF_8)); atomic.finishWrite(stream); }
        catch (Exception error) { atomic.failWrite(stream); throw error; }
    }
    static JSONObject report(File file) throws Exception {
        byte[] bytes = new AtomicFile(file).readFully(); check(bytes.length <= 16384, "bounded report");
        return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
    }
}
