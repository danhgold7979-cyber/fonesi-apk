package vn.fonesi.payload;

import java.util.UUID;

/** Pure state transition for a test-only Application onCreate marker, independent of Android APIs. */
public final class SyntheticApplicationProcessMarker {
    private SyntheticApplicationProcessMarker() {}

    public static State advance(int starts, int lastPid, String lastProcessToken, int currentPid, String currentProcessToken) {
        if (starts < 0 || starts == Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid persisted Application start count");
        if (currentPid <= 0 || !canonicalUuid(currentProcessToken)) throw new IllegalArgumentException("Invalid current synthetic process identity");
        if (starts == 0) {
            if (lastPid != -1 || lastProcessToken != null) throw new IllegalArgumentException("Fresh scope contains a partial process marker");
        } else {
            if (lastPid <= 0 || !canonicalUuid(lastProcessToken)) throw new IllegalArgumentException("Persisted Application process marker is incomplete");
            if (currentProcessToken.equals(lastProcessToken)) throw new IllegalStateException("Synthetic Application onCreate repeated in the same process");
        }
        return new State(starts + 1, currentPid, currentProcessToken);
    }
    private static boolean canonicalUuid(String value) {
        if (value == null) return false;
        try { return UUID.fromString(value).toString().equals(value); }
        catch (IllegalArgumentException ignored) { return false; }
    }
    public static final class State {
        private final int starts; private final int pid; private final String processToken;
        private State(int starts, int pid, String processToken) { this.starts=starts; this.pid=pid; this.processToken=processToken; }
        public int getStarts() { return starts; } public int getPid() { return pid; } public String getProcessToken() { return processToken; }
    }
}
