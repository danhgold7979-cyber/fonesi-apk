package vn.fonesi.rebuild.logical;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Explicit defensive copies with constructor null rejection; no newer collection factories. */
final class ImmutableCollections {
    private ImmutableCollections() {}
    static <T> Set<T> copySet(Set<T> source) {
        Objects.requireNonNull(source);
        Set<T> copy = new HashSet<>();
        for (T item : source) copy.add(Objects.requireNonNull(item));
        return Collections.unmodifiableSet(copy);
    }
    static <K,V> Map<K,V> copyMap(Map<K,V> source) {
        Objects.requireNonNull(source);
        Map<K,V> copy = new HashMap<>();
        for (Map.Entry<K,V> entry : source.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey()), Objects.requireNonNull(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }
}
