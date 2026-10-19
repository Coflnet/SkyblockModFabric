package com.coflnet.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Allows one fetch per key at a time and at most one per {@code minIntervalMs}, failed ones included. */
public final class FetchThrottle {
    private final long minIntervalMs;
    private final Map<String, Long> startedAt = new HashMap<>();
    private final Set<String> inFlight = new HashSet<>();

    public FetchThrottle(long minIntervalMs) {
        this.minIntervalMs = minIntervalMs;
    }

    /** @return true when the caller should fetch {@code key} now; it must then call {@link #finish} */
    public synchronized boolean tryStart(String key, long now) {
        if (inFlight.contains(key)) return false;
        Long last = startedAt.get(key);
        if (last != null && now - last < minIntervalMs) return false;
        startedAt.put(key, now);
        inFlight.add(key);
        return true;
    }

    public synchronized void finish(String key) {
        inFlight.remove(key);
    }
}
