package vn.fonesi.apk.foundation;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Owned projection snapshots only. No schema, freshness, auth, file or provider authority. */
public final class CatalogPage {
    private static final int MAX_ROWS = 128;
    private static final int MAX_BYTES = 262144;
    private static final Pattern GENERATION = Pattern.compile("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    public enum Error { INVALID_PROJECTION, UNSUPPORTED_DATA, BUDGET_EXCEEDED, SNAPSHOT_CHANGED, STATE, WRONG_THREAD, CANCELLED }
    public enum Step { ADDED, EMPTY_SENTINEL, STOP_BEFORE_ROW }
    public static final class Failure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Error error;
        private Failure(Error error) { super("Catalog page unavailable"); this.error = error; }
        public Error error() { return error; }
    }
    public static final class Snapshot {
        private final String generation;
        private final long revision;
        private Snapshot(String generation, long revision) { this.generation = generation; this.revision = revision; }
        public String generation() { return generation; }
        public long revision() { return revision; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Snapshot)) return false;
            Snapshot value = (Snapshot) other;
            return revision == value.revision && generation.equals(value.generation);
        }
        @Override public int hashCode() { return 31 * generation.hashCode() + (int) (revision ^ (revision >>> 32)); }
    }
    public static final class Cursor {
        private final Snapshot snapshot;
        private final long lastAcceptedPosition;
        private Cursor(Snapshot snapshot, long position) { this.snapshot = snapshot; lastAcceptedPosition = position; }
        public Snapshot snapshot() { return snapshot; }
        public long lastAcceptedPosition() { return lastAcceptedPosition; }
    }
    public static final class Row {
        private final long position;
        private final CatalogKey key;
        private final String packageName;
        private final String label;
        private final String group;
        private Row(long position, CatalogKey key, String packageName, String label, String group) {
            this.position = position; this.key = key; this.packageName = packageName;
            this.label = label; this.group = group;
        }
        public long position() { return position; }
        public CatalogKey key() { return key; }
        public String packageName() { return packageName; }
        public String label() { return label; }
        public String nullableGroup() { return group; }
    }
    private final Snapshot snapshot;
    private final List<Row> rows;
    private final Cursor continuation;
    private final int rawFieldBytes;
    private CatalogPage(Snapshot snapshot, List<Row> rows, boolean more, int bytes) {
        this.snapshot = snapshot;
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        continuation = more ? new Cursor(snapshot, rows.get(rows.size() - 1).position) : null;
        rawFieldBytes = bytes;
    }
    public Snapshot snapshot() { return snapshot; }
    public List<Row> rows() { return rows; }
    public Cursor continuation() { return continuation; }
    public int rawFieldBytes() { return rawFieldBytes; }

    /** Stable/owned arrays during each call; pure code cannot prove a fresh Android read. */
    public static Builder begin(Object[] beforeMetadata, Cursor continuation) {
        Snapshot before = metadata(beforeMetadata);
        need(continuation == null || continuation.snapshot.equals(before), Error.SNAPSHOT_CHANGED);
        return new Builder(before, continuation);
    }
    private enum State { OPEN, STOPPED, ENDED, FINISHED, FAILED }
    public static final class Builder {
        private final Thread owner = Thread.currentThread();
        private final Snapshot before;
        private final Cursor incoming;
        private final List<Row> rows = new ArrayList<>();
        private final Set<CatalogKey> keys = new HashSet<>();
        private State state = State.OPEN;
        private int submitted;
        private int bytes;
        private boolean sentinel;
        private boolean positionSeen;
        private long lastSeen;
        private boolean more;
        private Builder(Snapshot before, Cursor incoming) { this.before = before; this.incoming = incoming; }
        private void owner() {
            // A foreign thread must not mutate the legitimate owner's state while failing.
            need(Thread.currentThread() == owner, Error.WRONG_THREAD);
        }
        private void discard() { rows.clear(); keys.clear(); state = State.FAILED; }

        public Step accept(Object[] input) {
            owner();
            try {
                need(state == State.OPEN, Error.STATE);
                need(input != null && input.length == 37 && ++submitted <= 129, Error.INVALID_PROJECTION);
                need(before.equals(snapshot(input, 0)), Error.SNAPSHOT_CHANGED);
                long present = bit(input[8]);
                if (present == 0) {
                    need(submitted == 1 && rows.isEmpty() && !sentinel, Error.INVALID_PROJECTION);
                    for (int i = 9; i < input.length; i++) need(input[i] == null, Error.INVALID_PROJECTION);
                    sentinel = true;
                    return Step.EMPTY_SENTINEL;
                }
                need(!sentinel, Error.INVALID_PROJECTION);
                long position = integer(input[9]);
                need(!positionSeen || position > lastSeen, Error.INVALID_PROJECTION);
                need(incoming == null || position > incoming.lastAcceptedPosition, Error.INVALID_PROJECTION);
                positionSeen = true; lastSeen = position;
                if (rows.size() == MAX_ROWS) {
                    more = true; state = State.STOPPED;
                    return Step.STOP_BEFORE_ROW; // Deliberately unvalidated lookahead payload.
                }
                byte[][] fields = new byte[5][];
                int rowBytes = 0;
                for (int i = 0; i < fields.length; i++) {
                    fields[i] = field(input, 10 + i * 5, 16384, i == 4);
                    if (fields[i] != null) rowBytes += fields[i].length;
                }
                need(bit(input[35]) == 1 && bit(input[36]) == 1, Error.UNSUPPORTED_DATA);
                if (bytes + rowBytes > MAX_BYTES) {
                    need(!rows.isEmpty(), Error.BUDGET_EXCEEDED);
                    more = true; state = State.STOPPED;
                    return Step.STOP_BEFORE_ROW;
                }
                CatalogKey.Result decoded = CatalogKey.decode(CatalogKey.StorageClass.TEXT, fields[0],
                        CatalogKey.StorageClass.TEXT, fields[1]);
                need(decoded.isValid(), decoded.error() == CatalogKey.Error.BUDGET_EXCEEDED
                        ? Error.BUDGET_EXCEEDED : Error.UNSUPPORTED_DATA);
                CatalogKey key = decoded.key();
                need(!keys.contains(key), Error.INVALID_PROJECTION);
                String packageName = text(fields[2]);
                String label = text(fields[3]);
                String group = fields[4] == null ? null : text(fields[4]);
                need(!packageName.isEmpty() && !label.isEmpty(), Error.UNSUPPORTED_DATA);
                rows.add(new Row(position, key, packageName, label, group));
                keys.add(key); bytes += rowBytes;
                return Step.ADDED;
            } catch (Failure failure) {
                discard();
                throw new Failure(failure.error);
            }
        }
        /** Caller must actually finish/close its Cursor; this method cannot prove that fact. */
        public void endInput() {
            owner();
            try {
                need(state == State.OPEN || state == State.STOPPED, Error.STATE);
                need(submitted > 0 && (sentinel || !rows.isEmpty()), Error.INVALID_PROJECTION);
                state = State.ENDED;
            } catch (Failure failure) { discard(); throw new Failure(failure.error); }
        }
        /** Caller supplies a separate fresh post-close metadata result, not a cached array. */
        public CatalogPage finish(Object[] afterMetadata) {
            owner();
            try {
                need(state == State.ENDED, Error.STATE);
                need(before.equals(metadata(afterMetadata)), Error.SNAPSHOT_CHANGED);
                CatalogPage page = new CatalogPage(before, rows, more, bytes);
                rows.clear(); keys.clear(); state = State.FINISHED;
                return page;
            } catch (Failure failure) { discard(); throw new Failure(failure.error); }
        }
        public void cancel() {
            owner();
            need(state != State.FINISHED && state != State.FAILED, Error.STATE);
            discard();
            throw new Failure(Error.CANCELLED);
        }
    }
    private static void need(boolean condition, Error error) { if (!condition) throw new Failure(error); }
    private static long integer(Object value) {
        need(value instanceof Long, Error.INVALID_PROJECTION);
        return (Long) value;
    }
    private static long bit(Object value) {
        long result = integer(value);
        need(result == 0 || result == 1, Error.INVALID_PROJECTION);
        return result;
    }
    private static String type(Object value) {
        need(value instanceof String, Error.INVALID_PROJECTION);
        String result = (String) value;
        need(result.equals("null") || result.equals("text") || result.equals("integer")
                || result.equals("real") || result.equals("blob"), Error.INVALID_PROJECTION);
        return result;
    }
    private static byte[] field(Object[] row, int offset, int bound, boolean nullable) {
        String kind = type(row[offset]);
        long nullFlag = bit(row[offset + 1]);
        long length = integer(row[offset + 2]);
        long over = bit(row[offset + 3]);
        Object payload = row[offset + 4];
        need(nullFlag == (kind.equals("null") ? 1 : 0), Error.INVALID_PROJECTION);
        if (!kind.equals("text")) {
            need(length == -1 && over == 0 && payload == null, Error.INVALID_PROJECTION);
            need(nullable && kind.equals("null"), Error.UNSUPPORTED_DATA);
            return null;
        }
        need(length >= 0 && over == (length > bound ? 1 : 0), Error.INVALID_PROJECTION);
        if (over == 1) {
            need(payload == null, Error.INVALID_PROJECTION);
            throw new Failure(Error.BUDGET_EXCEEDED);
        }
        need(payload instanceof byte[], Error.INVALID_PROJECTION);
        byte[] bytes = (byte[]) payload;
        need(bytes.length == length, Error.INVALID_PROJECTION);
        return bytes;
    }
    private static String text(byte[] bytes) {
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            need(value.length() <= 4096, Error.BUDGET_EXCEEDED);
            need(Arrays.equals(bytes, value.getBytes(StandardCharsets.UTF_8)), Error.UNSUPPORTED_DATA);
            return value;
        } catch (CharacterCodingException failure) { throw new Failure(Error.UNSUPPORTED_DATA); }
    }
    private static Snapshot metadata(Object[] row) {
        need(row != null && row.length == 10, Error.INVALID_PROJECTION);
        need(type(row[0]).equals("integer") && integer(row[1]) == 1, Error.UNSUPPORTED_DATA);
        return snapshot(row, 2);
    }
    private static Snapshot snapshot(Object[] row, int offset) {
        byte[] generationBytes = field(row, offset, 36, false);
        need(generationBytes.length == 36, Error.UNSUPPORTED_DATA);
        String generation = text(generationBytes);
        need(GENERATION.matcher(generation).matches(), Error.UNSUPPORTED_DATA);
        String revisionType = type(row[offset + 5]);
        long valid = bit(row[offset + 6]);
        if (!revisionType.equals("integer")) {
            need(valid == 0 && row[offset + 7] == null, Error.INVALID_PROJECTION);
            throw new Failure(Error.UNSUPPORTED_DATA);
        }
        long revision = integer(row[offset + 7]);
        need(valid == (revision >= 0 ? 1 : 0), Error.INVALID_PROJECTION);
        need(valid == 1, Error.UNSUPPORTED_DATA);
        return new Snapshot(generation, revision);
    }
}
