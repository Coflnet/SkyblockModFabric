package com.coflnet.core;

import java.util.Optional;

/** The command the backend expects the player to type next, until it expires or is sent. */
public final class ExpectedCommandState {
    private record Entry(String command, long expiresAt) {}

    private static volatile Entry entry;

    private ExpectedCommandState() {}

    public static void set(ExpectCommandParser.ExpectedCommand expected, long now) {
        entry = new Entry(expected.command(), now + expected.ttlSeconds() * 1000L);
    }

    public static Optional<String> current(long now) {
        Entry e = entry;
        if (e == null) return Optional.empty();
        if (now >= e.expiresAt()) {
            entry = null;
            return Optional.empty();
        }
        return Optional.of(e.command());
    }

    public static void clear() {
        entry = null;
    }
}
