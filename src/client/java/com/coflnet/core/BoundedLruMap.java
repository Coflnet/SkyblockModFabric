package com.coflnet.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A small least-recently-used cache bound to a fixed maximum size, backed by
 * an access-ordered {@link LinkedHashMap}. Once the map would exceed
 * {@code maxSize} entries, the least-recently-used entry is evicted
 * automatically on the next {@link #put(Object, Object)}.
 *
 * <p>All public operations are synchronized so an instance can be safely
 * shared between the render thread and background worker threads without
 * external locking - the mod uses these for long-lived static maps (item id
 * tracking, per-inventory timestamps, ...) that would otherwise grow without
 * bound for the lifetime of the game client.
 */
public class BoundedLruMap<K, V> {
    private final int maxSize;
    private final LinkedHashMap<K, V> delegate;

    public BoundedLruMap(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        this.maxSize = maxSize;
        // accessOrder=true so a get() counts as a "use" and protects the entry from eviction.
        this.delegate = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > BoundedLruMap.this.maxSize;
            }
        };
    }

    public synchronized V put(K key, V value) {
        return delegate.put(key, value);
    }

    public synchronized V get(K key) {
        return delegate.get(key);
    }

    public synchronized V getOrDefault(K key, V defaultValue) {
        return delegate.getOrDefault(key, defaultValue);
    }

    public synchronized boolean containsKey(K key) {
        return delegate.containsKey(key);
    }

    public synchronized V remove(K key) {
        return delegate.remove(key);
    }

    public synchronized void clear() {
        delegate.clear();
    }

    public synchronized int size() {
        return delegate.size();
    }

    public synchronized boolean isEmpty() {
        return delegate.isEmpty();
    }
}
