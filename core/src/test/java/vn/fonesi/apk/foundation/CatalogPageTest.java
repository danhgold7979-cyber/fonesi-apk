package vn.fonesi.apk.foundation;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public final class CatalogPageTest {
    private static final String GEN = "00000000-0000-0000-0000-000000000123";
    private static int checks;
    private CatalogPageTest() { }
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    private static Object[] meta(String generation, long revision) {
        byte[] raw = generation.getBytes(StandardCharsets.UTF_8);
        return new Object[]{"integer", 1L, "text", 0L, (long) raw.length, 0L, raw,
                "integer", revision >= 0 ? 1L : 0L, revision};
    }
    private static Object[] meta() { return meta(GEN, 0); }
    private static void field(Object[] row, int offset, String value) {
        if (value == null) {
            row[offset] = "null"; row[offset + 1] = 1L; row[offset + 2] = -1L;
            row[offset + 3] = 0L; row[offset + 4] = null;
        } else rawField(row, offset, value.getBytes(StandardCharsets.UTF_8));
    }
    private static void rawField(Object[] row, int offset, byte[] raw) {
        row[offset] = "text"; row[offset + 1] = 0L; row[offset + 2] = (long) raw.length;
        row[offset + 3] = raw.length > 16384 ? 1L : 0L;
        row[offset + 4] = raw.length > 16384 ? null : raw;
    }
    private static Object[] row(long position, String engine, String clone, String label, String group) {
        Object[] row = new Object[37]; Object[] m = meta();
        System.arraycopy(m, 2, row, 0, 8);
        row[8] = 1L; row[9] = position;
        field(row, 10, engine); field(row, 15, clone); field(row, 20, "owned.fixture");
        field(row, 25, label); field(row, 30, group); row[35] = 1L; row[36] = 1L;
        return row;
    }
    private static Object[] row(long position) { return row(position, "owned", Long.toString(position), "Label", null); }
    private static Object[] empty() {
        Object[] result = new Object[37]; System.arraycopy(meta(), 2, result, 0, 8);
        result[8] = 0L; return result;
    }
    private static void failure(CatalogPage.Error expected, Runnable action) {
        boolean threw = false;
        try { action.run(); }
        catch (CatalogPage.Failure value) {
            threw = true;
            check(value.error() == expected, "precise typed failure");
            check(value.getMessage().equals("Catalog page unavailable") && value.getCause() == null, "no payload/cause leak");
        }
        check(threw, "failure required");
    }
    private static CatalogPage finish(CatalogPage.Builder builder) {
        builder.endInput(); return builder.finish(meta());
    }
    private static void rejectRow(Object[] row, CatalogPage.Error error) {
        CatalogPage.Builder builder = CatalogPage.begin(meta(), null);
        failure(error, () -> builder.accept(row));
        failure(CatalogPage.Error.STATE, () -> builder.finish(meta()));
    }
    private static CatalogPage.Builder fullBudget() {
        CatalogPage.Builder b = CatalogPage.begin(meta(), null);
        for (int i = 0; i < 12; i++) {
            Object[] large = row(i + 1, "e".repeat(4096), String.valueOf((char) ('a' + i)).repeat(4096), "l".repeat(4096), "g".repeat(4096));
            field(large, 20, "p".repeat(4096)); b.accept(large);
        }
        Object[] exact = row(13, "e".repeat(4096), "m".repeat(4096), "l".repeat(4096), null);
        field(exact, 20, "p".repeat(4096)); b.accept(exact);
        return b;
    }
    public static void main(String[] args) throws InterruptedException {
        CatalogPage.Builder b = CatalogPage.begin(meta(), null);
        check(b.accept(empty()) == CatalogPage.Step.EMPTY_SENTINEL, "explicit empty sentinel");
        CatalogPage page = finish(b);
        check(page.rows().isEmpty() && page.continuation() == null && page.rawFieldBytes() == 0, "valid empty page");
        CatalogPage.Builder zero = CatalogPage.begin(meta(), null);
        failure(CatalogPage.Error.INVALID_PROJECTION, zero::endInput);
        CatalogPage.Builder unfinished = CatalogPage.begin(meta(), null);
        unfinished.accept(row(1)); failure(CatalogPage.Error.STATE, () -> unfinished.finish(meta()));

        b = CatalogPage.begin(meta(), null);
        for (long position : new long[]{Long.MIN_VALUE, -1, 0, Long.MAX_VALUE}) {
            check(b.accept(row(position)) == CatalogPage.Step.ADDED, "signed positions are ordinary rows");
        }
        page = finish(b);
        check(page.rows().get(0).position() == Long.MIN_VALUE && page.rows().get(3).position() == Long.MAX_VALUE, "no cursor increment/wrap");
        check(page.continuation() == null, "complete iteration has no fabricated continuation");

        b = CatalogPage.begin(meta(), null);
        for (long i = 1; i <= 128; i++) b.accept(row(i));
        Object[] peek = row(129); for (int i = 10; i < 37; i++) peek[i] = new Object();
        check(b.accept(peek) == CatalogPage.Step.STOP_BEFORE_ROW, "129 is deliberately unaccepted payload");
        page = finish(b); CatalogPage.Cursor cursor = page.continuation();
        check(page.rows().size() == 128 && cursor.lastAcceptedPosition() == 128, "128 published and last accepted cursor");
        CatalogPage.Builder next = CatalogPage.begin(meta(), cursor);
        next.accept(row(129)); check(finish(next).rows().get(0).position() == 129, "continuation excludes only accepted cursor");
        CatalogPage.Builder staleCursor = CatalogPage.begin(meta(), cursor);
        failure(CatalogPage.Error.INVALID_PROJECTION, () -> staleCursor.accept(row(128)));
        failure(CatalogPage.Error.SNAPSHOT_CHANGED, () -> CatalogPage.begin(meta(GEN, 1), cursor));
        failure(CatalogPage.Error.SNAPSHOT_CHANGED, () -> CatalogPage.begin(meta("00000000-0000-0000-0000-000000000456", 0), cursor));
        rejectRow(peek, CatalogPage.Error.INVALID_PROJECTION); // Next-page candidate payload cannot inherit peek acceptance.
        CatalogPage.Builder extra = CatalogPage.begin(meta(), null);
        for (long i = 1; i <= 129; i++) extra.accept(row(i));
        failure(CatalogPage.Error.STATE, () -> extra.accept(row(130)));
        failure(CatalogPage.Error.STATE, extra::endInput);

        b = fullBudget();
        check(b.accept(row(14)) == CatalogPage.Step.STOP_BEFORE_ROW, "aggregate stop before decoding next row");
        page = finish(b);
        check(page.rows().size() == 13 && page.rawFieldBytes() == 262144 && page.continuation().lastAcceptedPosition() == 13, "exact aggregate budget no row skip");

        for (int kind = 0; kind < 3; kind++) {
            CatalogPage.Builder full = fullBudget(); Object[] candidate = row(14);
            CatalogPage.Error expected;
            if (kind == 0) { candidate[27] = 999L; expected = CatalogPage.Error.INVALID_PROJECTION; }
            else if (kind == 1) { field(candidate, 25, "x".repeat(16385)); expected = CatalogPage.Error.BUDGET_EXCEEDED; }
            else { candidate[35] = 0L; expected = CatalogPage.Error.UNSUPPORTED_DATA; }
            failure(expected, () -> full.accept(candidate));
            failure(CatalogPage.Error.STATE, () -> full.finish(meta()));
        }
        CatalogPage.Builder pendingUtf8 = fullBudget(); Object[] invalidPending = row(14);
        rawField(invalidPending, 25, new byte[]{(byte) 0xff});
        check(pendingUtf8.accept(invalidPending) == CatalogPage.Step.STOP_BEFORE_ROW, "aggregate-stop payload not decoded or claimed valid");
        CatalogPage.Cursor pendingCursor = finish(pendingUtf8).continuation();
        CatalogPage.Builder validatePending = CatalogPage.begin(meta(), pendingCursor);
        failure(CatalogPage.Error.UNSUPPORTED_DATA, () -> validatePending.accept(invalidPending));
        CatalogPage.Builder changedPeek = CatalogPage.begin(meta(), null);
        for (long i = 1; i <= 128; i++) changedPeek.accept(row(i));
        Object[] changedLookahead = row(129); changedLookahead[7] = 1L;
        failure(CatalogPage.Error.SNAPSHOT_CHANGED, () -> changedPeek.accept(changedLookahead));
        rejectRow(new Object[0], CatalogPage.Error.INVALID_PROJECTION);
        rejectRow(null, CatalogPage.Error.INVALID_PROJECTION);

        for (String[] pair : new String[][]{{"", ""}, {" ", "\u0000x"}, {"Case", "id"}, {"case", "id"}, {"é", "e\u0301"}}) {
            b = CatalogPage.begin(meta(), null);
            Object[] value = row(0, pair[0], pair[1], "a\u0000b", "");
            b.accept(value); Arrays.fill((byte[]) value[29], (byte) 'x'); Arrays.fill((byte[]) value[14], (byte) 'x');
            page = finish(b);
            check(page.rows().get(0).key().engineKey().equals(pair[0]) && page.rows().get(0).key().cloneId().equals(pair[1]), "exact immutable key");
            check(page.rows().get(0).label().equals("a\u0000b") && page.rows().get(0).nullableGroup().equals(""), "immutable NUL label and empty group");
        }
        b = CatalogPage.begin(meta(), null); b.accept(row(1)); page = finish(b);
        check(page.rows().get(0).nullableGroup() == null, "stored null not rewritten to empty");
        boolean immutable = false;
        try { page.rows().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable && page.rows().size() == 1, "immutable list");

        for (int index : new int[]{8, 9, 11, 12, 13, 35, 36}) {
            Object[] value = row(1); value[index] = Integer.valueOf(1);
            rejectRow(value, CatalogPage.Error.INVALID_PROJECTION);
        }
        for (int index : new int[]{35, 36}) {
            Object[] value = row(1); value[index] = 0L;
            rejectRow(value, CatalogPage.Error.UNSUPPORTED_DATA);
        }
        Object[] inconsistent = row(1); inconsistent[12] = 999L;
        rejectRow(inconsistent, CatalogPage.Error.INVALID_PROJECTION);
        Object[] wrongNull = row(1); wrongNull[11] = 1L;
        rejectRow(wrongNull, CatalogPage.Error.INVALID_PROJECTION);
        Object[] blob = row(1); blob[25] = "blob"; blob[26] = 0L; blob[27] = -1L; blob[28] = 0L; blob[29] = null;
        rejectRow(blob, CatalogPage.Error.UNSUPPORTED_DATA);
        for (byte[] raw : new byte[][]{{(byte) 0xff}, {(byte) 0xc0, (byte) 0xaf}, {(byte) 0xed, (byte) 0xa0, (byte) 0x80}}) {
            Object[] value = row(1); rawField(value, 25, raw); byte[] before = raw.clone();
            rejectRow(value, CatalogPage.Error.UNSUPPORTED_DATA); check(Arrays.equals(before, raw), "malformed bytes retained");
        }
        for (String oversized : new String[]{"x".repeat(4097), "x".repeat(16384), "x".repeat(16385), "😀".repeat(2049)}) {
            Object[] value = row(1); field(value, 25, oversized); rejectRow(value, CatalogPage.Error.BUDGET_EXCEEDED);
        }
        for (int offset : new int[]{20, 25}) {
            Object[] value = row(1); field(value, offset, ""); rejectRow(value, CatalogPage.Error.UNSUPPORTED_DATA);
        }
        b = CatalogPage.begin(meta(), null); b.accept(row(1, "e", "i", "\u0800".repeat(4096), null));
        check(finish(b).rows().get(0).label().length() == 4096, "valid UTF8/UTF16 boundary");

        CatalogPage.Builder duplicate = CatalogPage.begin(meta(), null); duplicate.accept(row(1, "e", "i", "a", null));
        failure(CatalogPage.Error.INVALID_PROJECTION, () -> duplicate.accept(row(2, "e", "i", "a", null)));
        CatalogPage.Builder order = CatalogPage.begin(meta(), null); order.accept(row(0));
        failure(CatalogPage.Error.INVALID_PROJECTION, () -> order.accept(row(0)));
        CatalogPage.Builder mixed = CatalogPage.begin(meta(), null); mixed.accept(empty());
        failure(CatalogPage.Error.INVALID_PROJECTION, () -> mixed.accept(row(1)));
        CatalogPage.Builder doubleEmpty = CatalogPage.begin(meta(), null); doubleEmpty.accept(empty());
        failure(CatalogPage.Error.INVALID_PROJECTION, () -> doubleEmpty.accept(empty()));
        Object[] badSentinel = empty(); badSentinel[35] = 1L; rejectRow(badSentinel, CatalogPage.Error.INVALID_PROJECTION);
        Object[] changed = row(1); changed[7] = 1L; rejectRow(changed, CatalogPage.Error.SNAPSHOT_CHANGED);
        CatalogPage.Builder after = CatalogPage.begin(meta(), null); after.accept(row(1)); after.endInput();
        failure(CatalogPage.Error.SNAPSHOT_CHANGED, () -> after.finish(meta(GEN, 1)));
        failure(CatalogPage.Error.UNSUPPORTED_DATA, () -> CatalogPage.begin(meta(GEN, -1), null));
        failure(CatalogPage.Error.UNSUPPORTED_DATA, () -> CatalogPage.begin(meta("bad", 0), null));
        failure(CatalogPage.Error.INVALID_PROJECTION, () -> CatalogPage.begin(new Object[0], null));
        CatalogPage.Builder cancelled = CatalogPage.begin(meta(), null); cancelled.accept(row(1));
        failure(CatalogPage.Error.CANCELLED, cancelled::cancel);
        failure(CatalogPage.Error.STATE, cancelled::endInput);
        CatalogPage.Builder terminal = CatalogPage.begin(meta(), null); terminal.accept(row(1)); finish(terminal);
        failure(CatalogPage.Error.STATE, () -> terminal.accept(row(2)));
        failure(CatalogPage.Error.STATE, terminal::cancel);

        CatalogPage.Builder owned = CatalogPage.begin(meta(), null);
        AtomicReference<CatalogPage.Error> foreign = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try { owned.accept(row(1)); } catch (CatalogPage.Failure error) { foreign.set(error.error()); }
        });
        thread.start(); thread.join();
        check(foreign.get() == CatalogPage.Error.WRONG_THREAD, "foreign thread no data/state mutation");
        owned.accept(row(1)); check(finish(owned).rows().size() == 1, "owner retains valid state");
        System.out.println("PASS CatalogPage owned HOST assertions=" + checks + "; no Android/schema/auth/provider acceptance");
    }
}
