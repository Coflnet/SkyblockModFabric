package com.coflnet.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Stopgap thread-safe drop-in replacement for a bare {@link HashMap} field
 * that is written from background threads and read every frame on the render
 * thread. Overrides the handful of methods actually used across the call
 * sites (get/getOrDefault/put/putAll/remove/clear/containsKey/size) with
 * synchronized versions.
 *
 * <p>This is NOT a general-purpose concurrent map: iteration views
 * (entrySet/keySet/values) and any method not overridden here are still
 * unsynchronized. It exists only because a dependency exposes a
 * {@code public static HashMap} field directly - replacing the field's value
 * with an instance of this class at startup is the only way to add
 * thread-safety without forking that dependency. Once the dependency
 * switches to a proper concurrent map, this class (and the swap) should be
 * removed.
 */
public class SynchronizedHashMap<K, V> extends HashMap<K, V> {
    public SynchronizedHashMap() {
        super();
    }

    public SynchronizedHashMap(int initialCapacity) {
        super(initialCapacity);
    }

    @Override
    public synchronized V get(Object key) {
        return super.get(key);
    }

    @Override
    public synchronized V getOrDefault(Object key, V defaultValue) {
        return super.getOrDefault(key, defaultValue);
    }

    @Override
    public synchronized V put(K key, V value) {
        return super.put(key, value);
    }

    @Override
    public synchronized void putAll(Map<? extends K, ? extends V> m) {
        super.putAll(m);
    }

    @Override
    public synchronized V remove(Object key) {
        return super.remove(key);
    }

    @Override
    public synchronized void clear() {
        super.clear();
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        return super.containsKey(key);
    }

    @Override
    public synchronized int size() {
        return super.size();
    }
}
