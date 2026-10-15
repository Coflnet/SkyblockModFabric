package com.coflnet.core;

import java.util.Optional;

/** The command the backend expects the player to type next, until it expires or is sent. */
public final class ExpectedCommandState {
    private record Entry(String command, long expiresAt) {}

    private static Entry entry;

    private ExpectedCommandState() {}

    public static synchronized void set(ExpectCommandParser.ExpectedCommand expected, long now) {
        entry = new Entry(expected.command(), now + expected.ttlSeconds() * 1000L);
    }

    public static synchronized Optional<String> current(long now) {
        Entry e = entry;
        if (e == null) return Optional.empty();
        if (now >= e.expiresAt()) {
            entry = null;
            return Optional.empty();
        }
        return Optional.of(e.command());
    }

    /** Consume only the expected command; unrelated chat and commands keep their guidance. */
    public static synchronized void consume(String submitted, long now) {
        current(now).filter(command -> submitted != null && command.trim().equals(submitted.trim()))
                .ifPresent(command -> clear());
    }

    public static synchronized void clear() {
        entry = null;
    }
}
