package vn.fonesi.app.storage;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Looper;
import android.os.UserManager;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import vn.fonesi.fixture.storage.BuildConfig;
import vn.fonesi.apk.foundation.CatalogKey;
import vn.fonesi.apk.foundation.CatalogOwnership;
import vn.fonesi.apk.foundation.CatalogReservation;
import vn.fonesi.apk.foundation.StyleValue;

/** Fixture target only; never compiled into the production app. No reset/adoption of existing data. */
public final class StorageFixture {
    private static final String TARGET = "vn.fonesi.fixture.storage";
    private static final String GENERATION = "00000000-0000-4000-8000-000000000001";
    private static final String ENGINE = "owned\u0000engine";
    private static final String CLONE = " Case ";
    private static final String[] CASES = {"missing", "fresh_read", "commit_readback", "stale", "cancelled"};
    private static final String SCHEMA_HASH = "a1578b11968a82fe88458401c93d44b9ff8bd0d3410651fc225a972f64f00c9d";
    private final int[] counts = new int[5];
    private int current;
    private Context app;
    private File originalParent, parent, file;
    private StructStat directory, owned;
    private StorageFixture() { }
    private void check(boolean condition) {
        if (!condition) throw new IllegalStateException("Fixture assertion failed");
        counts[current]++;
    }
    public static Bundle run(Context context) {
        StorageFixture fixture = new StorageFixture();
        Bundle output = new Bundle();
        output.putString("schema", "owned-storage-fixture-text-v2");
        output.putString("target", TARGET);
        output.putString("source_sha", BuildConfig.SOURCE_SHA);
        output.putString("cases", "missing,fresh_read,commit_readback,stale,cancelled");
        try {
            fixture.execute(context);
            for (int count : fixture.counts) if (count <= 0) throw new IllegalStateException("Missing case");
            output.putString("result", "PASS");
        } catch (Throwable failure) {
            output.putString("result", "FAIL");
            output.putString("failure", "CASE_" + fixture.current + "_FAILED");
        }
        for (int i = 0; i < CASES.length; i++) {
            output.putInt("assertions_" + CASES[i], fixture.counts[i]);
        }
        return output;
    }
    private void execute(Context context) throws Exception {
        check(BuildConfig.SOURCE_SHA.matches("[0-9a-f]{40}"));
        check(Looper.myLooper() != Looper.getMainLooper());
        check(context != null && TARGET.equals(context.getPackageName()) && !context.isDeviceProtectedStorage());
        app = context.getApplicationContext();
        check(app != null && TARGET.equals(app.getPackageName()) && !app.isDeviceProtectedStorage());
        UserManager users = app.getSystemService(UserManager.class);
        check(users != null && users.isUserUnlocked());
        originalParent = app.getNoBackupFilesDir();
        parent = originalParent.getCanonicalFile();
        directory = observe(originalParent, CatalogOwnership.Role.DIRECTORY);
        same(directory, observe(parent, CatalogOwnership.Role.DIRECTORY));
        file = new File(parent, "manager-catalog-v1.sqlite");
        check(file.getCanonicalFile().equals(file));
        absent(file); sidecarsAbsent();
        ExistingCatalogRead.Request readRequest = new ExistingCatalogRead.Request();
        ExistingCatalogRead.AccessBoundary access = request -> check(request == readRequest);
        CatalogPageRead.Result missing = CatalogPageRead.read(app, readRequest, access,
                new CancellationSignal(), null, 5000);
        check(missing.status == CatalogPageRead.Status.MISSING && missing.page == null);
        absent(file); sidecarsAbsent();

        current = 1;
        createOwned();
        CatalogKey key = CatalogKey.decode(CatalogKey.StorageClass.TEXT, ENGINE.getBytes(StandardCharsets.UTF_8),
                CatalogKey.StorageClass.TEXT, CLONE.getBytes(StandardCharsets.UTF_8)).key();
        CatalogPageRead.Result page = CatalogPageRead.read(app, readRequest, access,
                new CancellationSignal(), null, 5000);
        check(page.status == CatalogPageRead.Status.READ_INTERNAL_ONLY && page.page != null);
        check(page.page.rows().size() == 2 && page.page.snapshot().revision() == 0);
        check(page.page.rows().get(0).key().equals(key));
        String[] sibling = siblingBytes();
        String[] initial = styleBytes();
        String[] selected = selectedBytes();
        try (SQLiteDatabase db = openOwned(true); Cursor inert = db.rawQuery(
                "SELECT typeof(readiness),hex(CAST(readiness AS BLOB)),typeof(launch_allowed),launch_allowed FROM manager_catalog ORDER BY rowid", null)) {
            int count = 0;
            while (inert.moveToNext()) {
                check("text".equals(inert.getString(0)) && "554E5645524946494544".equals(inert.getString(1)));
                check("integer".equals(inert.getString(2)) && inert.getLong(3) == 0); count++;
            }
            check(count == 2);
        }

        current = 2;
        Map<String, Object> fields = new HashMap<>();
        fields.put("accentKey", "teal"); fields.put("badgeText", "  F  ");
        StyleValue.Result validated = StyleValue.validateFields(fields); check(validated.valid());
        StyleValue style = validated.value();
        OwnedCatalogWriter.Snapshot snapshot = new OwnedCatalogWriter.Snapshot(GENERATION, 0);
        ExistingStyleWrite.Request request = new ExistingStyleWrite.Request(snapshot, key, style);
        OwnedCatalogWriter.CommitBoundary boundary = boundary(request);
        ExistingStyleWrite.Result committed = ExistingStyleWrite.apply(app, request, boundary, new CancellationSignal(), 5000);
        check(committed.status == ExistingStyleWrite.Status.COMMITTED && committed.committedRevision == 1);
        check(committed.writerOutcome == OwnedCatalogWriter.Status.COMMITTED);
        String[] changed = styleBytes();
        check(Arrays.equals(changed, new String[]{"text", "7465616C", "text", "2020462020", "1"}));
        check(Arrays.equals(sibling, siblingBytes()) && Arrays.equals(selected, selectedBytes()));
        page = CatalogPageRead.read(app, readRequest, access, new CancellationSignal(), null, 5000);
        check(page.status == CatalogPageRead.Status.READ_INTERNAL_ONLY && page.page.snapshot().revision() == 1);
        check(page.page.rows().get(0).key().equals(key));
        check(!Arrays.equals(initial, changed));

        current = 3;
        ExistingStyleWrite.Result stale = ExistingStyleWrite.apply(app, request, boundary, new CancellationSignal(), 5000);
        check(stale.status == ExistingStyleWrite.Status.STALE_OR_MISSING);
        check(Arrays.equals(changed, styleBytes()) && Arrays.equals(sibling, siblingBytes())
                && Arrays.equals(selected, selectedBytes()));

        current = 4;
        CancellationSignal cancelled = new CancellationSignal(); cancelled.cancel();
        ExistingStyleWrite.Result stopped = ExistingStyleWrite.apply(app, request, boundary, cancelled, 5000);
        check(stopped.status == ExistingStyleWrite.Status.CANCELLED);
        check(Arrays.equals(changed, styleBytes()) && Arrays.equals(sibling, siblingBytes())
                && Arrays.equals(selected, selectedBytes()));
        CatalogReservation.Token token = CatalogProcessGate.tryAcquire();
        check(token != null);
        token.requireActive(); token.close();
        check(true);
        verifyOwned(); sidecarsAbsent();
    }
    private OwnedCatalogWriter.CommitBoundary boundary(ExistingStyleWrite.Request exact) {
        return new OwnedCatalogWriter.CommitBoundary() {
            public void checkBeforeStaging(OwnedCatalogWriter.Snapshot s, CatalogKey k, StyleValue v) {
                check(s == exact.snapshot && k == exact.key && v == exact.style);
            }
            public void atCommit(OwnedCatalogWriter.Snapshot s, CatalogKey k, StyleValue v,
                                 OwnedCatalogWriter.CommitAction action) {
                checkBeforeStaging(s, k, v); action.enter();
            }
        };
    }
    private StructStat observe(File path, CatalogOwnership.Role expected) throws Exception {
        StructStat stat = Os.lstat(path.getPath());
        CatalogOwnership.Role actual = OsConstants.S_ISDIR(stat.st_mode) ? CatalogOwnership.Role.DIRECTORY
                : OsConstants.S_ISREG(stat.st_mode) ? CatalogOwnership.Role.REGULAR_FILE : CatalogOwnership.Role.OTHER;
        check(CatalogOwnership.assess(expected, actual, stat.st_mode & 07777, stat.st_uid, stat.st_gid,
                android.os.Process.myUid(), Os.getgid(), Os.getegid(), Os.geteuid())
                == CatalogOwnership.Decision.OWNERSHIP_CONDITION_MET);
        return stat;
    }
    private void same(StructStat left, StructStat right) { check(left.st_dev == right.st_dev && left.st_ino == right.st_ino); }
    private void absent(File path) throws Exception {
        try { Os.lstat(path.getPath()); }
        catch (ErrnoException failure) { if (failure.errno == OsConstants.ENOENT) { check(true); return; } throw failure; }
        throw new IllegalStateException("Fixture refuses existing data");
    }
    private void sidecarsAbsent() throws Exception {
        for (String suffix : new String[]{"-journal", "-wal", "-shm"}) absent(new File(file.getPath() + suffix));
    }
    private void verifyParent() throws Exception {
        check(originalParent.getCanonicalFile().equals(parent));
        same(directory, observe(originalParent, CatalogOwnership.Role.DIRECTORY));
        same(directory, observe(parent, CatalogOwnership.Role.DIRECTORY));
        check(file.getCanonicalFile().equals(file) && file.getParentFile().equals(parent));
    }
    private void verifyOwned() throws Exception {
        verifyParent(); same(owned, observe(file, CatalogOwnership.Role.REGULAR_FILE));
    }
    private void createOwned() throws Exception {
        verifyParent(); absent(file); sidecarsAbsent();
        FileDescriptor descriptor = Os.open(file.getPath(), OsConstants.O_CREAT | OsConstants.O_EXCL
                | OsConstants.O_RDWR | OsConstants.O_NOFOLLOW, 0600);
        // Any close failure escapes immediately. No reopen, cleanup or uncertain-file adoption.
        try { owned = Os.fstat(descriptor); }
        finally { Os.close(descriptor); }
        same(owned, observe(file, CatalogOwnership.Role.REGULAR_FILE));
        check(owned.st_size == 0); verifyOwned(); sidecarsAbsent();
        byte[] bytes;
        try (InputStream input = app.getAssets().open("catalog_v1_schema.sql")) {
            ByteArrayOutputStream collected = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024]; int count;
            while ((count = input.read(buffer)) != -1) {
                check(collected.size() + count <= 4096); collected.write(buffer, 0, count);
            }
            bytes = collected.toByteArray();
        }
        check(bytes.length <= 4096 && hex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(SCHEMA_HASH.toUpperCase(java.util.Locale.ROOT)));
        String[] ddl = new String(bytes, StandardCharsets.UTF_8).trim().split(";\\s*");
        check(ddl.length == 2);
        try (SQLiteDatabase db = openOwned(false)) {
            mutate(db, "PRAGMA encoding='UTF-8'");
            mutate(db, "PRAGMA application_id=1178815793");
            mutate(db, "PRAGMA user_version=1");
            mutate(db, ddl[0]); mutate(db, ddl[1]);
            mutate(db, "INSERT INTO catalog_meta VALUES(1,?,0)", new Object[]{GENERATION});
            mutate(db, "INSERT INTO manager_catalog(engine_key,clone_id,package_name,label,accent_key,badge_text,readiness,launch_allowed) VALUES(?,?,?,'owned','blue',NULL,'UNVERIFIED',0)",
                    new Object[]{ENGINE, CLONE, "owned.synthetic"});
            mutate(db, "INSERT INTO manager_catalog(engine_key,clone_id,package_name,label,group_name,accent_key,badge_text,readiness,launch_allowed) VALUES('sibling','case','owned.sibling',?,?,'rose',X'00FF','UNVERIFIED',0)",
                    new Object[]{"label\u0000tail", "group\u0000tail"});
        }
        verifyOwned(); sidecarsAbsent();
    }
    private SQLiteDatabase openOwned(boolean readOnly) throws Exception {
        verifyOwned(); sidecarsAbsent();
        return SQLiteDatabase.openDatabase(file.getPath(), null,
                (readOnly ? SQLiteDatabase.OPEN_READONLY : SQLiteDatabase.OPEN_READWRITE) | SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                ignored -> { throw new IllegalStateException("Fixture corruption retained"); });
    }
    private String[] styleBytes() throws Exception {
        try (SQLiteDatabase db = openOwned(true); Cursor cursor = db.rawQuery(
                "SELECT typeof(accent_key),hex(CAST(accent_key AS BLOB)),typeof(badge_text),hex(CAST(badge_text AS BLOB)),(SELECT revision FROM catalog_meta) FROM manager_catalog WHERE rowid=1", null)) {
            check(cursor.moveToFirst()); String[] values = new String[5];
            for (int i = 0; i < values.length; i++) values[i] = cursor.getString(i);
            check(!cursor.moveToNext()); return values;
        }
    }
    private String[] siblingBytes() throws Exception {
        try (SQLiteDatabase db = openOwned(true); Cursor cursor = db.rawQuery(
                "SELECT typeof(engine_key),hex(CAST(engine_key AS BLOB)),typeof(clone_id),hex(CAST(clone_id AS BLOB)),typeof(package_name),hex(CAST(package_name AS BLOB)),typeof(label),hex(CAST(label AS BLOB)),typeof(group_name),hex(CAST(group_name AS BLOB)),typeof(legacy_launch_token),hex(CAST(legacy_launch_token AS BLOB)),typeof(accent_key),hex(CAST(accent_key AS BLOB)),typeof(badge_text),hex(CAST(badge_text AS BLOB)),typeof(cluster_id),hex(CAST(cluster_id AS BLOB)),typeof(readiness),hex(CAST(readiness AS BLOB)),typeof(launch_allowed),hex(CAST(launch_allowed AS BLOB)) FROM manager_catalog WHERE rowid=2", null)) {
            check(cursor.moveToFirst()); String[] values = new String[22];
            for (int i = 0; i < values.length; i++) values[i] = cursor.getString(i);
            check(!cursor.moveToNext()); return values;
        }
    }
    private String[] selectedBytes() throws Exception {
        try (SQLiteDatabase db = openOwned(true); Cursor cursor = db.rawQuery(
                "SELECT typeof(engine_key),hex(CAST(engine_key AS BLOB)),typeof(clone_id),hex(CAST(clone_id AS BLOB)),typeof(package_name),hex(CAST(package_name AS BLOB)),typeof(label),hex(CAST(label AS BLOB)),typeof(group_name),hex(CAST(group_name AS BLOB)),typeof(legacy_launch_token),hex(CAST(legacy_launch_token AS BLOB)),typeof(cluster_id),hex(CAST(cluster_id AS BLOB)),typeof(readiness),hex(CAST(readiness AS BLOB)),typeof(launch_allowed),hex(CAST(launch_allowed AS BLOB)) FROM manager_catalog WHERE rowid=1", null)) {
            check(cursor.moveToFirst()); String[] values = new String[18];
            for (int i = 0; i < values.length; i++) values[i] = cursor.getString(i);
            check(!cursor.moveToNext()); return values;
        }
    }
    private void mutate(SQLiteDatabase db, String sql) throws Exception {
        verifyOwned(); sidecarsAbsent(); db.execSQL(sql);
    }
    private void mutate(SQLiteDatabase db, String sql, Object[] values) throws Exception {
        verifyOwned(); sidecarsAbsent(); db.execSQL(sql, values);
    }
    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02X", value & 255));
        return result.toString();
    }
}
