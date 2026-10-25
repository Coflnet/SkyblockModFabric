package com.coflnet.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds a "Trade completed" chat line back until the trade-window upload queued before it has been
 * sent, so the backend receives the final trade contents before the completion that stores them.
 * Uploads are identified by their increasing request sequence; a superseded upload is covered by
 * the newer one. No Minecraft types, so the ordering is unit-testable.
 */
public final class TradeChatHold {
    /** A held line is forwarded after this long even if the upload never finishes. */
    public static final long MAX_HOLD_MS = 5_000L;
    public static final long NOT_HELD = -1L;

    private record Held(long waitFor, Runnable forward) {}

    private final Map<Long, Held> held = new LinkedHashMap<>();
    private long lastQueued;
    private long lastFinished;
    private long nextToken;

    public static boolean isTradeCompletion(String chat) {
        return chat != null && chat.replaceAll("§.", "").startsWith("Trade completed with ");
    }

    public synchronized void uploadQueued(long sequence) {
        lastQueued = Math.max(lastQueued, sequence);
    }

    /** @return a token for {@link #timeout} if {@code forward} was held, else {@link #NOT_HELD} */
    public synchronized long hold(String chat, Runnable forward) {
        if (lastFinished >= lastQueued || !isTradeCompletion(chat))
            return NOT_HELD;
        long token = nextToken++;
        held.put(token, new Held(lastQueued, forward));
        return token;
    }

    /** The upload with {@code sequence} was sent (or gave up); returns the lines it was holding, in order. */
    public synchronized List<Runnable> uploadFinished(long sequence) {
        lastFinished = Math.max(lastFinished, sequence);
        List<Runnable> released = new ArrayList<>();
        for (Iterator<Held> it = held.values().iterator(); it.hasNext(); ) {
            Held line = it.next();
            if (line.waitFor <= lastFinished) {
                released.add(line.forward);
                it.remove();
            }
        }
        return released;
    }

    /** @return the line held under {@code token} if it is still held, else null */
    public synchronized Runnable timeout(long token) {
        Held line = held.remove(token);
        return line == null ? null : line.forward;
    }

    public synchronized boolean isHolding() {
        return !held.isEmpty();
    }
}
