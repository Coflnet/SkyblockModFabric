package com.coflnet.core;

/** Lets a per-frame diagnostic message through once; remembers the last {@code capacity} distinct messages. */
public final class LogDedupe {
    private final BoundedLruMap<String, Boolean> seen;

    public LogDedupe(int capacity) {
        this.seen = new BoundedLruMap<>(capacity);
    }

    /** @return true the first time a message is seen (while it is still remembered) */
    public boolean shouldLog(String message) {
        return seen.put(message, Boolean.TRUE) == null;
    }
}
