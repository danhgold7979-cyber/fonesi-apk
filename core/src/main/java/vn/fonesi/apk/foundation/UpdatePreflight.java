package vn.fonesi.apk.foundation;

/** Display-only comparison of untrusted observations. Never an update authorization. */
public final class UpdatePreflight {
    private UpdatePreflight() { }

    public enum Comparison {
        MISSING_OBSERVATION, STALE_OBSERVATION, PACKAGE_MISMATCH,
        EQUAL_OBSERVED_VERSION, SOURCE_OBSERVED_NEWER, SOURCE_OBSERVED_OLDER
    }

    public enum BlockReason { PROVIDER_UNAVAILABLE }

    /** A caller-provided display value; the sequence is not a trusted freshness receipt. */
    public static final class Observation {
        private final String packageLabel;
        private final long observedVersion;
        private final long snapshotSequence;

        public Observation(String packageLabel, long observedVersion, long snapshotSequence) {
            if (!validLabel(packageLabel) || observedVersion < 0 || snapshotSequence < 0) {
                throw new IllegalArgumentException("Invalid display observation");
            }
            this.packageLabel = packageLabel;
            this.observedVersion = observedVersion;
            this.snapshotSequence = snapshotSequence;
        }

        public String packageLabel() { return packageLabel; }
        public long observedVersion() { return observedVersion; }
        public long snapshotSequence() { return snapshotSequence; }
    }

    public static final class Preview {
        private final Comparison comparison;

        private Preview(Comparison comparison) { this.comparison = comparison; }
        public Comparison comparison() { return comparison; }
        public BlockReason blockReason() { return BlockReason.PROVIDER_UNAVAILABLE; }
        public boolean executable() { return false; }
    }

    public static Preview compare(Observation source, Observation target, long expectedSequence) {
        if (expectedSequence < 0) throw new IllegalArgumentException("Invalid expected sequence");
        if (source == null || target == null) return new Preview(Comparison.MISSING_OBSERVATION);
        if (source.snapshotSequence != expectedSequence || target.snapshotSequence != expectedSequence) {
            return new Preview(Comparison.STALE_OBSERVATION);
        }
        if (!source.packageLabel.equals(target.packageLabel)) return new Preview(Comparison.PACKAGE_MISMATCH);
        int order = Long.compare(source.observedVersion, target.observedVersion);
        return new Preview(order == 0 ? Comparison.EQUAL_OBSERVED_VERSION
                : order > 0 ? Comparison.SOURCE_OBSERVED_NEWER : Comparison.SOURCE_OBSERVED_OLDER);
    }

    private static boolean validLabel(String value) {
        if (value == null || value.isEmpty() || value.length() > 255) return false;
        int first = value.codePointAt(0);
        int last = value.codePointBefore(value.length());
        if (space(first) || space(last)) return false;
        for (int i = 0; i < value.length();) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 == value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) return false;
            } else if (Character.isLowSurrogate(c)) return false;
            int cp = value.codePointAt(i);
            if (Character.isISOControl(cp)) return false;
            i += Character.charCount(cp);
        }
        return true;
    }

    private static boolean space(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp);
    }
}
