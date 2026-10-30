package vn.fonesi.rebuild.model;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Metadata presentation only: selection never confers provider or operation authority. */
public final class ManagerModel {
    public enum Phase { IDLE, LOADING, LOADED, FAILED }
    public enum Status { UNVERIFIED, QUARANTINED, UNAVAILABLE }

    /** Exact opaque catalog reference, not a verified provider, operation ID or authority. */
    public static final class CatalogKey {
        public final String engineKey;
        public final String cloneId;

        public CatalogKey(String engineKey, String cloneId) {
            this.engineKey = Objects.requireNonNull(engineKey, "engineKey");
            this.cloneId = Objects.requireNonNull(cloneId, "cloneId");
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CatalogKey)) return false;
            CatalogKey key = (CatalogKey) other;
            return engineKey.equals(key.engineKey) && cloneId.equals(key.cloneId);
        }

        @Override public int hashCode() {
            return 31 * engineKey.hashCode() + cloneId.hashCode();
        }
    }

    public static final class Entry {
        public final CatalogKey key;
        public final String packageName;
        public final String name;
        public final String group;
        public final Status status;

        public Entry(CatalogKey key, String packageName, String name, String group, Status status) {
            this.key = Objects.requireNonNull(key, "key");
            this.packageName = required(packageName);
            this.name = required(name);
            this.group = Objects.requireNonNull(group, "group");
            this.status = Objects.requireNonNull(status, "status");
        }
    }

    public static final class Snapshot {
        public final long revision;
        public final Phase phase;
        public final String query;
        /** Null means all groups; empty means exactly the ungrouped metadata entries. */
        public final String group;
        public final List<Entry> visible;
        public final Set<CatalogKey> selected;
        public final int totalCount;

        private Snapshot(long revision, Phase phase, String query, String group,
                         List<Entry> visible, Set<CatalogKey> selected, int totalCount) {
            this.revision = revision;
            this.phase = phase;
            this.query = query;
            this.group = group;
            this.visible = Collections.unmodifiableList(new ArrayList<>(visible));
            this.selected = Collections.unmodifiableSet(new LinkedHashSet<>(selected));
            this.totalCount = totalCount;
        }

        public boolean operationsEnabled() { return false; }
    }

    private long revision;
    private long generation;
    private Phase phase = Phase.IDLE;
    private String query = "";
    private String group;
    private List<Entry> entries = Collections.emptyList();
    private final Set<CatalogKey> selected = new LinkedHashSet<>();

    /** Supersedes pending work and invalidates every prior row/selection. */
    public synchronized long beginRefresh() {
        long nextGeneration = Math.addExact(generation, 1);
        long nextRevision = Math.addExact(revision, 1);
        generation = nextGeneration;
        revision = nextRevision;
        phase = Phase.LOADING;
        entries = Collections.emptyList();
        selected.clear();
        return generation;
    }

    /** Malformed current results throw without partial publication; stale results are ignored. */
    public synchronized boolean publish(long token, List<Entry> incoming) {
        if (phase != Phase.LOADING || token != generation) return false;
        Objects.requireNonNull(incoming, "incoming");
        List<Entry> copy = new ArrayList<>();
        Set<CatalogKey> ids = new HashSet<>();
        for (Entry entry : incoming) {
            Objects.requireNonNull(entry, "entry");
            if (!ids.add(entry.key)) throw new IllegalArgumentException("duplicate catalog key");
            copy.add(entry);
        }
        long nextRevision = Math.addExact(revision, 1);
        entries = Collections.unmodifiableList(copy);
        selected.clear();
        phase = Phase.LOADED;
        revision = nextRevision;
        return true;
    }

    public synchronized boolean fail(long token) {
        if (phase != Phase.LOADING || token != generation) return false;
        long nextRevision = Math.addExact(revision, 1);
        entries = Collections.emptyList();
        selected.clear();
        phase = Phase.FAILED;
        revision = nextRevision;
        return true;
    }

    public synchronized Snapshot filter(long expectedRevision, String newQuery, String newGroup) {
        if (revision != expectedRevision) return snapshot();
        Objects.requireNonNull(newQuery, "query");
        if (!query.equals(newQuery) || !Objects.equals(group, newGroup)) {
            long nextRevision = Math.addExact(revision, 1);
            query = newQuery;
            group = newGroup;
            Set<CatalogKey> visibleIds = new HashSet<>();
            for (Entry entry : visible()) visibleIds.add(entry.key);
            selected.retainAll(visibleIds);
            revision = nextRevision;
        }
        return snapshot();
    }

    /** Explicit desired state makes repeated input idempotent; stale view input never toggles. */
    public synchronized boolean select(long expectedRevision, CatalogKey key, boolean value) {
        Objects.requireNonNull(key, "key");
        if (phase != Phase.LOADED || revision != expectedRevision) return false;
        boolean found = false;
        for (Entry entry : visible()) if (entry.key.equals(key)) found = true;
        if (!found) return false;
        if (selected.contains(key) == value) return true;
        long nextRevision = Math.addExact(revision, 1);
        if (value) selected.add(key); else selected.remove(key);
        revision = nextRevision;
        return true;
    }

    public synchronized boolean selectVisible(long expectedRevision, boolean value) {
        if (phase != Phase.LOADED || revision != expectedRevision) return false;
        Set<CatalogKey> next = new LinkedHashSet<>();
        if (value) for (Entry entry : visible()) next.add(entry.key);
        if (!selected.equals(next)) {
            long nextRevision = Math.addExact(revision, 1);
            selected.clear();
            selected.addAll(next);
            revision = nextRevision;
        }
        return true;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(revision, phase, query, group, visible(), selected, entries.size());
    }

    private List<Entry> visible() {
        List<Entry> result = new ArrayList<>();
        String needle = fold(query);
        for (Entry entry : entries) {
            if (group != null && !group.equals(entry.group)) continue;
            if (fold(entry.name).contains(needle) || fold(entry.group).contains(needle)
                    || fold(entry.packageName).contains(needle)) result.add(entry);
        }
        return result;
    }

    private static String fold(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replace('đ', 'd');
    }

    private static String required(String value) {
        Objects.requireNonNull(value, "metadata");
        if (value.trim().isEmpty()) throw new IllegalArgumentException("empty metadata");
        return value;
    }
}
