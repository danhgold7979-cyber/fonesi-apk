package vn.fonesi.app.storage;

import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.CancellationSignal;
import android.os.Looper;
import android.os.OperationCanceledException;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import vn.fonesi.apk.foundation.CatalogPage;

/** Dormant internal reader. No UI caller, auth broker, writer or engine activation. */
final class CatalogPageRead {
    private CatalogPageRead() { }
    enum Status { READ_INTERNAL_ONLY, MISSING, BUSY, CANCELLED, DEADLINE, REJECTED, RESOURCE_UNCERTAIN }
    static final class Result {
        final Status status;
        final CatalogPage page;
        private Result(Status status, CatalogPage page) { this.status = status; this.page = page; }
    }
    private static final class Failure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final Status status;
        Failure(Status status) { super("Catalog page unavailable"); this.status = status; }
    }
    private static void require(boolean value) { if (!value) throw new Failure(Status.REJECTED); }
    private static final String[] META = {"meta_id_type", "meta_id", "catalog_id_type", "catalog_id_is_null",
            "catalog_id_byte_length", "catalog_id_over_budget", "catalog_id_bytes", "revision_type",
            "revision_valid", "catalog_revision"};
    private static final String[] PAGE = pageNames();
    private static String[] pageNames() {
        String[] result = new String[37];
        System.arraycopy(META, 2, result, 0, 8);
        result[8] = "row_present"; result[9] = "row_position";
        String[] fields = {"engine_key", "clone_id", "package_name", "label", "group_name"};
        String[] suffixes = {"_type", "_is_null", "_byte_length", "_over_budget", "_bytes"};
        for (int field = 0; field < 5; field++) for (int col = 0; col < 5; col++) {
            result[10 + field * 5 + col] = fields[field] + suffixes[col];
        }
        result[35] = "readiness_inert"; result[36] = "launch_inert";
        return result;
    }
    private static final class Budget {
        final long start = SystemClock.elapsedRealtime();
        final long millis;
        final CancellationSignal signal;
        Budget(long millis, CancellationSignal signal) {
            require(millis >= 1 && millis <= 5000 && signal != null && start >= 0);
            this.millis = millis; this.signal = signal;
        }
        void check() {
            signal.throwIfCanceled();
            long now = SystemClock.elapsedRealtime();
            require(now >= start);
            if (now - start >= millis) throw new Failure(Status.DEADLINE);
        }
    }
    private static final class Frame {
        final ExistingCatalogRead.Lease lease;
        final Budget budget;
        boolean uncertain;
        Frame(ExistingCatalogRead.Lease lease, Budget budget) { this.lease = lease; this.budget = budget; }
        SQLiteDatabase checked() {
            budget.check();
            SQLiteDatabase db = lease.database();
            require(db.isOpen() && db.isReadOnly() && !db.inTransaction());
            budget.check();
            return db;
        }
        ReadCursor query(String sql, Long position) {
            SQLiteDatabase db = checked();
            Cursor cursor = position == null ? db.rawQuery(sql, null, budget.signal)
                    : db.rawQueryWithFactory((database, driver, editTable, query) -> {
                        query.bindLong(1, position.longValue());
                        return new SQLiteCursor(driver, editTable, query);
                    }, sql, null, null, budget.signal);
            return new ReadCursor(cursor, this);
        }
        void inspect(AssetManager assets) {
            try {
                OwnedCatalogWriter.inspectReadOnly(checked(), assets, budget.signal, () -> { checked(); });
            } catch (OwnedCatalogWriter.ReadInspectionFailure failure) {
                if (failure.uncertain) uncertain = true;
                if (failure.uncertain) throw new Failure(Status.RESOURCE_UNCERTAIN);
                budget.check();
                throw new Failure(failure.cancelled ? Status.CANCELLED : Status.REJECTED);
            }
        }
    }
    private static final class ReadCursor implements AutoCloseable {
        final Cursor value;
        final Frame frame;
        ReadCursor(Cursor value, Frame frame) { require(value != null); this.value = value; this.frame = frame; }
        @Override public void close() {
            try { value.close(); }
            catch (RuntimeException failure) {
                frame.uncertain = true;
                throw new Failure(Status.RESOURCE_UNCERTAIN);
            }
        }
    }
    static Result read(Context context, ExistingCatalogRead.Request request,
                       ExistingCatalogRead.AccessBoundary access, CancellationSignal cancellation,
                       CatalogPage.Cursor continuation, long budgetMillis) {
        if (context == null || request == null || access == null || cancellation == null
                || budgetMillis < 1 || budgetMillis > 5000 || Looper.myLooper() == Looper.getMainLooper()) {
            return new Result(Status.REJECTED, null);
        }
        ExistingCatalogRead.Lease lease = null;
        Frame frame = null;
        CatalogPage page = null;
        Status status = Status.REJECTED;
        Budget budget = null;
        try {
            budget = new Budget(budgetMillis, cancellation);
            Budget observed = budget;
            ExistingCatalogRead.Result opened = ExistingCatalogRead.openExisting(context, request,
                    cancellation, exactRequest -> { observed.check(); access.check(exactRequest); observed.check(); });
            if (opened.status == ExistingCatalogRead.Status.RESOURCE_UNCERTAIN) {
                return new Result(Status.RESOURCE_UNCERTAIN, null);
            }
            if (opened.status != ExistingCatalogRead.Status.OPEN_UNVALIDATED) {
                budget.check();
                return new Result(entryStatus(opened.status), null);
            }
            lease = opened.lease;
            frame = new Frame(lease, budget);
            frame.checked();
            Context application = context.getApplicationContext();
            require(application != null);
            AssetManager assets = application.getAssets();
            String meta = asset(assets, "catalog_read_meta.sql", "798643fcf3e883c363f3f10a88bd3b13159d9fba821323b15d1753bc0043e852");
            String first = asset(assets, "catalog_page_first.sql", "e346a5271a1002229fc00c119fd14fa1d24dec0c8f9d01a5beae3472e816a8aa");
            String next = asset(assets, "catalog_page_next.sql", "0fe0e4272b8ca70c77732f84bc6575bf82d4dfe2af4d038bdd9ca8b3055bfc9e");
            frame.inspect(assets);
            CatalogPage.Builder builder = CatalogPage.begin(metadata(frame, meta), continuation);
            long position = continuation == null ? Long.MIN_VALUE : continuation.lastAcceptedPosition();
            int submitted = 0;
            try (ReadCursor resource = frame.query(continuation == null ? first : next, Long.valueOf(position))) {
                Cursor rows = resource.value;
                names(rows, PAGE);
                while (rows.moveToNext()) {
                    frame.checked();
                    require(++submitted <= 129);
                    Object[] raw = projection(rows, submitted == 129);
                    CatalogPage.Step step = builder.accept(raw);
                    frame.checked();
                    if (step == CatalogPage.Step.STOP_BEFORE_ROW) break;
                }
            }
            builder.endInput();
            frame.inspect(assets);
            page = builder.finish(metadata(frame, meta));
            frame.checked();
            status = Status.READ_INTERNAL_ONLY;
        } catch (OperationCanceledException failure) {
            status = Status.CANCELLED;
        } catch (ExistingCatalogRead.Failure failure) {
            status = entryStatus(failure.status);
        } catch (Failure failure) {
            status = failure.status;
        } catch (Exception failure) {
            status = Status.REJECTED;
        } finally {
            if (lease != null) {
                if (frame != null && frame.uncertain) {
                    status = Status.RESOURCE_UNCERTAIN;
                    try { lease.quarantineResources(); }
                    catch (RuntimeException failure) { status = Status.RESOURCE_UNCERTAIN; }
                }
                try { lease.close(); }
                catch (RuntimeException failure) { status = Status.RESOURCE_UNCERTAIN; }
            }
        }
        if (status == Status.READ_INTERNAL_ONLY) {
            try { require(budget != null); budget.check(); }
            catch (OperationCanceledException failure) { status = Status.CANCELLED; }
            catch (Failure failure) { status = failure.status; }
            catch (Exception failure) { status = Status.REJECTED; }
        }
        return new Result(status, status == Status.READ_INTERNAL_ONLY ? page : null);
    }
    private static Status entryStatus(ExistingCatalogRead.Status status) {
        if (status == ExistingCatalogRead.Status.MISSING) return Status.MISSING;
        if (status == ExistingCatalogRead.Status.BUSY) return Status.BUSY;
        if (status == ExistingCatalogRead.Status.CANCELLED) return Status.CANCELLED;
        if (status == ExistingCatalogRead.Status.RESOURCE_UNCERTAIN) return Status.RESOURCE_UNCERTAIN;
        return Status.REJECTED;
    }
    private static void names(Cursor rows, String[] expected) {
        require(rows.getColumnCount() == expected.length && Arrays.equals(rows.getColumnNames(), expected));
    }
    private static Object integer(Cursor rows, int index) {
        if (rows.getType(index) == Cursor.FIELD_TYPE_NULL) return null;
        require(rows.getType(index) == Cursor.FIELD_TYPE_INTEGER);
        return Long.valueOf(rows.getLong(index));
    }
    private static Object kind(Cursor rows, int index) {
        if (rows.getType(index) == Cursor.FIELD_TYPE_NULL) return null;
        require(rows.getType(index) == Cursor.FIELD_TYPE_STRING);
        String text = rows.getString(index);
        require("text".equals(text) || "integer".equals(text) || "null".equals(text)
                || "blob".equals(text) || "real".equals(text));
        return text;
    }
    private static void field(Cursor rows, Object[] raw, int offset, int max) {
        raw[offset] = kind(rows, offset);
        raw[offset + 1] = integer(rows, offset + 1);
        raw[offset + 2] = integer(rows, offset + 2);
        raw[offset + 3] = integer(rows, offset + 3);
        if (rows.getType(offset + 4) == Cursor.FIELD_TYPE_NULL) { raw[offset + 4] = null; return; }
        require("text".equals(raw[offset]) && Long.valueOf(0).equals(raw[offset + 1])
                && Long.valueOf(0).equals(raw[offset + 3]) && raw[offset + 2] instanceof Long);
        long length = ((Long) raw[offset + 2]).longValue();
        require(length >= 0 && length <= max && rows.getType(offset + 4) == Cursor.FIELD_TYPE_BLOB);
        byte[] value = rows.getBlob(offset + 4);
        require(value != null && value.length == length);
        raw[offset + 4] = value;
    }
    private static Object[] projection(Cursor rows, boolean lookahead) {
        Object[] raw = new Object[37];
        field(rows, raw, 0, 36);
        raw[5] = kind(rows, 5); raw[6] = integer(rows, 6); raw[7] = integer(rows, 7);
        raw[8] = integer(rows, 8); raw[9] = integer(rows, 9);
        if (lookahead) return raw;
        if (Long.valueOf(0).equals(raw[8])) {
            for (int i = 9; i < 37; i++) require(rows.getType(i) == Cursor.FIELD_TYPE_NULL);
            return raw;
        }
        for (int offset = 10; offset <= 30; offset += 5) field(rows, raw, offset, 16384);
        raw[35] = integer(rows, 35); raw[36] = integer(rows, 36);
        return raw;
    }
    private static Object[] metadata(Frame frame, String sql) {
        try (ReadCursor resource = frame.query(sql, null)) {
            Cursor rows = resource.value;
            names(rows, META);
            require(rows.moveToFirst()); frame.checked();
            Object[] raw = new Object[10];
            raw[0] = kind(rows, 0); raw[1] = integer(rows, 1);
            field(rows, raw, 2, 36);
            raw[7] = kind(rows, 7); raw[8] = integer(rows, 8); raw[9] = integer(rows, 9);
            require(!rows.moveToNext()); frame.checked();
            return raw;
        }
    }
    private static String asset(AssetManager assets, String name, String expected) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream input = assets.open(name)) {
            byte[] buffer = new byte[512]; int count;
            while ((count = input.read(buffer)) != -1) {
                require(out.size() + count <= 4096); out.write(buffer, 0, count);
            }
        }
        byte[] bytes = out.toByteArray();
        StringBuilder hex = new StringBuilder(64);
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            hex.append(Character.forDigit((value >>> 4) & 15, 16));
            hex.append(Character.forDigit(value & 15, 16));
        }
        require(expected.contentEquals(hex));
        String source = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().trim();
        require(source.endsWith(";"));
        source = source.substring(0, source.length() - 1);
        require(!source.contains(";"));
        return source;
    }
}
