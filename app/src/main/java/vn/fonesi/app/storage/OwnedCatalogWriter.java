package vn.fonesi.app.storage;

import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.CancellationSignal;
import android.os.Looper;
import android.os.OperationCanceledException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import vn.fonesi.apk.foundation.CatalogKey;
import vn.fonesi.apk.foundation.StyleValue;

/** Dormant worker-only adapter. No file opener, runtime caller or access broker exists yet. */
final class OwnedCatalogWriter {
    private static final long APPLICATION_ID = 1178815793L;
    private static final Pattern UUID_TEXT = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final String INDEX = "sqlite_autoindex_manager_catalog_1";
    private OwnedCatalogWriter() { }

    enum Status { COMMITTED, REJECTED, CANCELLED, STALE_OR_MISSING, BUDGET_EXCEEDED, REVISION_EXHAUSTED, ROLLED_BACK, INDETERMINATE }
    static final class Result {
        final Status status;
        final long committedRevision;
        Result(Status status, long revision) { this.status = status; this.committedRevision = revision; }
    }
    static final class Snapshot {
        final String generation;
        final long revision;
        Snapshot(String generation, long revision) {
            if (generation == null || !UUID_TEXT.matcher(generation).matches() || revision < 0) {
                throw new IllegalArgumentException("Invalid catalog snapshot");
            }
            this.generation = generation;
            this.revision = revision;
        }
    }
    interface CommitAction { void enter(); }
    /** No implementation is provided: real foreground/auth/session broker integration is required. */
    interface CommitBoundary {
        void checkBeforeStaging(Snapshot snapshot, CatalogKey key, StyleValue style);
        void atCommit(Snapshot snapshot, CatalogKey key, StyleValue style, CommitAction action);
    }
    private static final class Reject extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final Status status;
        Reject(Status status) { super("Catalog operation rejected"); this.status = status; }
    }
    private static final class Frame {
        boolean beginAttempted;
        boolean began;
        boolean transaction;
        boolean entered;
        boolean committed;
        boolean uncertain;
    }

    private static final class Tracked<T extends AutoCloseable> implements AutoCloseable {
        final T value;
        final Frame frame;
        Tracked(T value, Frame frame) { this.value = value; this.frame = frame; }
        @Override public void close() {
            try { value.close(); }
            catch (Exception error) {
                frame.uncertain = true;
                throw new Reject(Status.INDETERMINATE);
            }
        }
    }
    private static <T extends AutoCloseable> Tracked<T> tracked(T value, Frame frame) {
        return new Tracked<>(value, frame);
    }

    static final class Sql {
        final String metaDdl;
        final String catalogDdl;
        final String claim;
        final String update;
        final String read;
        private Sql(String schema, String claim, String update, String read) {
            String[] definitions = schema.trim().split(";", -1);
            require(definitions.length == 3 && definitions[2].trim().isEmpty());
            metaDdl = definitions[0].trim(); catalogDdl = definitions[1].trim();
            this.claim = single(claim); this.update = single(update); this.read = single(read);
        }
        static Sql load(AssetManager assets) throws IOException {
            workerOnly();
            return new Sql(
                    asset(assets, "catalog_v1_schema.sql", "a1578b11968a82fe88458401c93d44b9ff8bd0d3410651fc225a972f64f00c9d"),
                    asset(assets, "catalog_claim_revision.sql", "84128d47bb6a897e0186af70240c6b33f6770f87a498fbc79e445c3a553da996"),
                    asset(assets, "catalog_update_existing_style.sql", "504a5c3bebd55a217d2bef32df8419f970471215338e57b17a851004bbfd33fb"),
                    asset(assets, "catalog_read_style.sql", "d17242a23c2ede1d57cf2751615285501e77e5ae0abc71c40ed3ee9de87d74bd"));
        }
        private static String single(String source) {
            String text = source.trim();
            require(text.endsWith(";"));
            text = text.substring(0, text.length() - 1);
            require(!text.contains(";"));
            return text;
        }
        private static String asset(AssetManager assets, String name, String expected) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream input = assets.open(name)) {
                byte[] buffer = new byte[512];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (out.size() + count > 4096) throw new IOException("SQL asset budget exceeded");
                    out.write(buffer, 0, count);
                }
            }
            byte[] bytes = out.toByteArray();
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
                StringBuilder hex = new StringBuilder(64);
                for (byte value : digest) {
                    hex.append(Character.forDigit((value >>> 4) & 15, 16));
                    hex.append(Character.forDigit(value & 15, 16));
                }
                if (!expected.contentEquals(hex)) throw new IOException("SQL asset identity mismatch");
            } catch (NoSuchAlgorithmException error) { throw new IOException("SHA-256 unavailable", error); }
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        }
    }

    /** Handle must be exclusively owned by this worker; inTransaction is not an ownership proof. */
    static Result apply(SQLiteDatabase db, Sql sql, Snapshot snapshot, CatalogKey key,
                        StyleValue style, CancellationSignal cancel, CommitBoundary boundary) {
        workerOnly();
        if (db == null || sql == null || snapshot == null || key == null || style == null
                || cancel == null || boundary == null) return result(Status.REJECTED);
        if (!db.isOpen() || db.isReadOnly() || db.inTransaction()) return result(Status.REJECTED);
        String badge = style.rawBadge();
        if (badge != null && badge.length() > 4096) return result(Status.BUDGET_EXCEEDED);
        byte[] badgeBytes = badge == null ? null : badge.getBytes(StandardCharsets.UTF_8);
        if (badgeBytes != null && badgeBytes.length > 16384) return result(Status.BUDGET_EXCEEDED);
        byte[] engine = key.engineKey().getBytes(StandardCharsets.UTF_8);
        byte[] clone = key.cloneId().getBytes(StandardCharsets.UTF_8);
        Frame frame = new Frame();
        AtomicBoolean available = new AtomicBoolean(true);
        AtomicBoolean violated = new AtomicBoolean(false);
        Thread owner = Thread.currentThread();
        Status failure = Status.ROLLED_BACK;
        try {
            cancel.throwIfCanceled(); boundary.checkBeforeStaging(snapshot, key, style);
            frame.beginAttempted = true;
            db.beginTransactionNonExclusive(); frame.began = true; frame.transaction = true;
            Snapshot current = guard(db, sql, cancel, frame);
            if (!current.generation.equals(snapshot.generation) || current.revision != snapshot.revision) throw new Reject(Status.STALE_OR_MISSING);
            if (snapshot.revision == Long.MAX_VALUE) throw new Reject(Status.REVISION_EXHAUSTED);
            cancel.throwIfCanceled(); boundary.checkBeforeStaging(snapshot, key, style);
            try (Tracked<SQLiteStatement> claimResource = tracked(db.compileStatement(sql.claim), frame)) {
                SQLiteStatement claim = claimResource.value;
                claim.bindBlob(1, snapshot.generation.getBytes(StandardCharsets.UTF_8));
                claim.bindLong(2, snapshot.revision);
                if (claim.executeUpdateDelete() != 1) throw new Reject(Status.STALE_OR_MISSING);
            }
            cancel.throwIfCanceled();
            try (Tracked<SQLiteStatement> updateResource = tracked(db.compileStatement(sql.update), frame)) {
                SQLiteStatement update = updateResource.value;
                update.bindString(1, style.accentKey());
                if (badge == null) update.bindNull(2); else update.bindString(2, badge);
                update.bindBlob(3, engine); update.bindBlob(4, clone);
                if (update.executeUpdateDelete() != 1) throw new Reject(Status.STALE_OR_MISSING);
            }
            cancel.throwIfCanceled();
            try (Tracked<Cursor> rowResource = tracked(db.rawQueryWithFactory((database, driver, editTable, query) -> {
                query.bindBlob(1, engine); query.bindBlob(2, clone);
                return new SQLiteCursor(driver, editTable, query);
            }, sql.read, null, "manager_catalog", cancel), frame)) {
                Cursor row = rowResource.value;
                require(row.getColumnCount() == 8 && row.moveToFirst());
                require("text".equals(text(row, 0)) && Arrays.equals(blob(row, 1), engine));
                require("text".equals(text(row, 2)) && Arrays.equals(blob(row, 3), clone));
                require("text".equals(text(row, 4)) && Arrays.equals(blob(row, 5), style.accentKey().getBytes(StandardCharsets.UTF_8)));
                if (badge == null) require("null".equals(text(row, 6)) && row.isNull(7));
                else require("text".equals(text(row, 6)) && Arrays.equals(blob(row, 7), badgeBytes));
                require(!row.moveToNext());
            }
            current = guard(db, sql, cancel, frame);
            require(current.generation.equals(snapshot.generation) && current.revision == snapshot.revision + 1);
            cancel.throwIfCanceled();
            boundary.atCommit(snapshot, key, style, () -> {
                if (Thread.currentThread() != owner) {
                    violated.set(true); available.set(false); throw new Reject(Status.REJECTED);
                }
                if (!available.compareAndSet(true, false) || violated.get()) {
                    violated.set(true); throw new Reject(Status.REJECTED);
                }
                cancel.throwIfCanceled();
                frame.entered = true;
                db.setTransactionSuccessful();
                db.endTransaction(); frame.transaction = false; frame.committed = true;
            });
            available.set(false);
            require(!violated.get() && frame.committed);
            return new Result(Status.COMMITTED, snapshot.revision + 1);
        } catch (OperationCanceledException error) { failure = Status.CANCELLED;
        } catch (Reject error) { failure = error.status;
        } catch (RuntimeException error) { failure = Status.ROLLED_BACK;
        } finally {
            available.set(false);
            if (frame.transaction && !frame.entered) {
                try { db.endTransaction(); frame.transaction = false; }
                catch (RuntimeException error) { frame.uncertain = true; }
            }
            if ((frame.entered && !frame.committed) || frame.uncertain) closeUncertain(db);
        }
        if (frame.entered || frame.uncertain || (frame.beginAttempted && !frame.began)) {
            closeUncertain(db);
            return result(Status.INDETERMINATE);
        }
        return result(!frame.began && failure == Status.ROLLED_BACK ? Status.REJECTED : failure);
    }

    private static Snapshot guard(SQLiteDatabase db, Sql sql, CancellationSignal cancel, Frame frame) {
        require("UTF-8".equals(pragmaText(db, "PRAGMA main.encoding", cancel, frame)));
        require(pragmaLong(db, "PRAGMA main.application_id", cancel, frame) == APPLICATION_ID);
        require(pragmaLong(db, "PRAGMA main.user_version", cancel, frame) == 1);
        require(pragmaLong(db, "PRAGMA ignore_check_constraints", cancel, frame) == 0);
        require(pragmaLong(db, "PRAGMA read_uncommitted", cancel, frame) == 0);
        int mainCount = 0, tempCount = 0;
        try (Tracked<Cursor> rowsResource = tracked(db.rawQuery("SELECT CASE WHEN typeof(name)='text' AND length(CAST(name AS BLOB))<=16 "
                + "THEN CAST(name AS BLOB) END FROM pragma_database_list() LIMIT 3", null, cancel), frame)) {
            Cursor rows = rowsResource.value;
            require(rows.getColumnCount() == 1);
            while (rows.moveToNext()) {
                byte[] rawName = blob(rows, 0);
                String name = new String(rawName, StandardCharsets.US_ASCII);
                require(Arrays.equals(rawName, name.getBytes(StandardCharsets.US_ASCII)));
                if ("main".equals(name)) mainCount++;
                else if ("temp".equals(name)) tempCount++;
                else throw new Reject(Status.REJECTED);
                require(mainCount <= 1 && tempCount <= 1);
            }
        }
        require(mainCount == 1);
        try (Tracked<Cursor> rowsResource = tracked(db.rawQuery("SELECT 1 FROM sqlite_temp_master LIMIT 1", null, cancel), frame)) {
            Cursor rows = rowsResource.value;
            require(!rows.moveToFirst());
        }
        // SQL/name projections are bounded before crossing CursorWindow into Java.
        String schemaQuery = "SELECT CASE WHEN length(CAST(type AS BLOB))<=16 THEN type END,CASE WHEN length(CAST(name AS BLOB))<=128 THEN name END,"
                + "CASE WHEN length(CAST(tbl_name AS BLOB))<=128 THEN tbl_name END,"
                + "CASE WHEN length(CAST(sql AS BLOB))<=2048 THEN CAST(sql AS BLOB) END,sql IS NULL "
                + "FROM main.sqlite_master ORDER BY type,name LIMIT 4";
        try (Tracked<Cursor> rowsResource = tracked(db.rawQuery(schemaQuery, null, cancel), frame)) {
            Cursor rows = rowsResource.value;
            require(rows.getColumnCount() == 5 && rows.moveToFirst());
            require("index".equals(text(rows, 0)) && INDEX.equals(text(rows, 1))
                    && "manager_catalog".equals(text(rows, 2)) && rows.isNull(3) && integer(rows, 4) == 1);
            require(rows.moveToNext() && "table".equals(text(rows, 0)) && "catalog_meta".equals(text(rows, 1))
                    && "catalog_meta".equals(text(rows, 2)) && Arrays.equals(blob(rows, 3), sql.metaDdl.getBytes(StandardCharsets.UTF_8)) && integer(rows, 4) == 0);
            require(rows.moveToNext() && "table".equals(text(rows, 0)) && "manager_catalog".equals(text(rows, 1))
                    && "manager_catalog".equals(text(rows, 2)) && Arrays.equals(blob(rows, 3), sql.catalogDdl.getBytes(StandardCharsets.UTF_8)) && integer(rows, 4) == 0);
            require(!rows.moveToNext());
        }
        try (Tracked<Cursor> rowsResource = tracked(db.rawQuery("PRAGMA main.index_list('manager_catalog')", null, cancel), frame)) {
            Cursor rows = rowsResource.value;
            require(rows.getColumnCount() == 5 && rows.moveToFirst());
            require(INDEX.equals(text(rows, 1)) && integer(rows, 2) == 1
                    && "pk".equals(text(rows, 3)) && integer(rows, 4) == 0 && !rows.moveToNext());
        }
        try (Tracked<Cursor> rowsResource = tracked(db.rawQuery("PRAGMA main.index_list('catalog_meta')", null, cancel), frame)) {
            Cursor rows = rowsResource.value;
            require(rows.getColumnCount() == 5 && !rows.moveToFirst());
        }
        try (Tracked<Cursor> rowsResource = tracked(db.rawQuery("PRAGMA main.index_xinfo('sqlite_autoindex_manager_catalog_1')", null, cancel), frame)) {
            Cursor rows = rowsResource.value;
            require(rows.getColumnCount() == 6);
            for (int i = 0; i < 3; i++) {
                require(rows.moveToNext() && integer(rows, 0) == i && integer(rows, 1) == (i == 2 ? -1 : i));
                if (i == 2) require(rows.isNull(2)); else require((i == 0 ? "engine_key" : "clone_id").equals(text(rows, 2)));
                require(integer(rows, 3) == 0 && "BINARY".equals(text(rows, 4)) && integer(rows, 5) == (i == 2 ? 0 : 1));
            }
            require(!rows.moveToNext());
        }
        try (Tracked<Cursor> rowResource = tracked(db.rawQuery("SELECT id,typeof(catalog_id),CASE WHEN length(CAST(catalog_id AS BLOB))=36 "
                + "THEN CAST(catalog_id AS BLOB) END,typeof(revision),CASE WHEN typeof(revision)='integer' THEN revision END FROM main.catalog_meta LIMIT 2", null, cancel), frame)) {
            Cursor row = rowResource.value;
            require(row.getColumnCount() == 5 && row.moveToFirst() && integer(row, 0) == 1
                    && "text".equals(text(row, 1)) && "integer".equals(text(row, 3)));
            byte[] id = blob(row, 2);
            require(id.length == 36);
            String generation = new String(id, StandardCharsets.US_ASCII);
            require(Arrays.equals(id, generation.getBytes(StandardCharsets.US_ASCII)) && UUID_TEXT.matcher(generation).matches());
            long revision = integer(row, 4);
            require(revision >= 0 && !row.moveToNext());
            return new Snapshot(generation, revision);
        }
    }
    private static String pragmaText(SQLiteDatabase db, String sql, CancellationSignal cancel, Frame frame) {
        try (Tracked<Cursor> cursorResource = tracked(db.rawQuery(sql, null, cancel), frame)) {
            Cursor cursor = cursorResource.value;
            require(cursor.getColumnCount() == 1 && cursor.moveToFirst());
            String value = text(cursor, 0); require(!cursor.moveToNext()); return value;
        }
    }
    private static long pragmaLong(SQLiteDatabase db, String sql, CancellationSignal cancel, Frame frame) {
        try (Tracked<Cursor> cursorResource = tracked(db.rawQuery(sql, null, cancel), frame)) {
            Cursor cursor = cursorResource.value;
            require(cursor.getColumnCount() == 1 && cursor.moveToFirst());
            long value = integer(cursor, 0); require(!cursor.moveToNext()); return value;
        }
    }
    private static String text(Cursor row, int index) { require(row.getType(index) == Cursor.FIELD_TYPE_STRING); return row.getString(index); }
    private static byte[] blob(Cursor row, int index) { require(row.getType(index) == Cursor.FIELD_TYPE_BLOB); return row.getBlob(index); }
    private static long integer(Cursor row, int index) { require(row.getType(index) == Cursor.FIELD_TYPE_INTEGER); return row.getLong(index); }
    private static void require(boolean value) { if (!value) throw new Reject(Status.REJECTED); }
    private static Result result(Status status) { return new Result(status, -1); }
    private static void workerOnly() { if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Worker thread required"); }
    private static void closeUncertain(SQLiteDatabase db) { try { db.close(); } catch (RuntimeException ignored) { /* Indeterminate remains indeterminate. */ } }
}
