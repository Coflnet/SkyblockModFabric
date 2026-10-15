package com.coflnet.core;

import CoflCore.handlers.DescriptionHandler;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One worker, one pending snapshot per upload destination; only the newest response may be displayed. */
public final class DescriptionQueue {
    private static final int CAPACITY = 256;
    private static final Logger LOGGER = LoggerFactory.getLogger(DescriptionQueue.class);
    private static final String TIMING = "Description refresh menu={} queue_ms={} request_ms={} dispatch_ms={} apply_ms={} total_ms={} outcome={}";
    private final LinkedHashMap<DescriptionRequest.Destination, Pending> pending = new LinkedHashMap<>();
    private final BoundedLruMap<DescriptionRequest.Destination, Long> lastStart = new BoundedLruMap<>(CAPACITY);
    private final Consumer<Runnable> dispatch;
    private final long throttleMs;
    private DescriptionRequest lastRequest;
    private long generation;
    private long sequence;
    private boolean running;
    private long lastSlowLogNanos;

    public DescriptionQueue(Consumer<Runnable> dispatch, long throttleMs) {
        this.dispatch = dispatch;
        this.throttleMs = throttleMs;
    }

    public synchronized boolean submit(DescriptionRequest request,
                                       Predicate<DescriptionHandler.DescModification[][]> apply, Runnable failed) {
        if (!request.forceGoalRefresh() && request.sameSnapshot(lastRequest)) return true;
        if (!pending.containsKey(request.destination()) && pending.size() >= CAPACITY) {
            LOGGER.warn("Description refresh menu={} outcome=queue_full", category(request.title()));
            failed.run();
            return false;
        }
        var previous = pending.get(request.destination());
        if (previous != null) logTiming(previous, 0, 0, 0, "coalesced");
        if (previous != null && previous.request.forceGoalRefresh()) request = request.withGoalRefresh();
        lastRequest = request;
        pending.put(request.destination(), new Pending(request, apply, failed, generation, ++sequence, System.nanoTime()));
        if (!running) {
            running = true;
            Thread.ofVirtual().name("CoflSky-Descriptions").start(this::drain);
        }
        return true;
    }

    /** Force another upload without discarding already queued storage snapshots. */
    public synchronized void invalidateDeduplication() { lastRequest = null; }

    /** A detached trade request shares display freshness with ordinary inventory requests. */
    public synchronized long reserveResponse() { lastRequest = null; return ++sequence; }

    public synchronized boolean isCurrent(long responseSequence) { return responseSequence == sequence; }

    /** A new session must never send or display snapshots captured in the previous one. */
    public synchronized void reset() {
        generation++;
        sequence++;
        pending.clear();
        lastStart.clear();
        lastRequest = null;
    }

    private void drain() {
        while (true) {
            Pending next;
            synchronized (this) {
                if (pending.isEmpty()) { running = false; return; }
                next = pending.entrySet().iterator().next().getValue();
            }
            long started = 0;
            try {
                Long previousStart = lastStart.get(next.request.destination());
                long delay = previousStart == null ? 0 : throttleMs - (System.currentTimeMillis() - previousStart);
                if (delay > 0) Thread.sleep(delay);
                synchronized (this) {
                    if (next.generation != generation) continue;
                    Pending newer = pending.remove(next.request.destination());
                    if (newer != null) next = newer;
                    lastStart.put(next.request.destination(), System.currentTimeMillis());
                }
                Pending completed = next;
                started = System.nanoTime();
                var response = completed.request.fetch();
                long requestStarted = started;
                long fetched = System.nanoTime();
                dispatch.accept(() -> {
                    long applyStarted = System.nanoTime();
                    String outcome = "failed";
                    try {
                        synchronized (this) {
                            if (completed.generation != generation || completed.sequence != sequence) {
                                outcome = "superseded";
                                return;
                            }
                        }
                        outcome = completed.apply.test(response) ? "applied" : "context_changed";
                    } finally {
                        logTiming(completed, requestStarted, fetched, applyStarted, outcome);
                    }
                });
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                synchronized (this) { running = false; }
                return;
            } catch (RuntimeException failure) {
                LOGGER.warn("Description refresh menu={} outcome=failed type={}", category(next.request.title()), failure.getClass().getSimpleName());
                logTiming(next, started, System.nanoTime(), 0, "failed");
                synchronized (this) {
                    if (lastRequest == next.request) lastRequest = null;
                }
                next.failed.run();
            }
        }
    }

    private void logTiming(Pending request, long started, long fetched, long applyStarted, String outcome) {
        long now = System.nanoTime();
        long total = now - request.enqueued;
        long queueMs = ((started == 0 ? now : started) - request.enqueued) / 1_000_000;
        long requestMs = started == 0 ? 0 : (fetched - started) / 1_000_000;
        long dispatchMs = applyStarted == 0 ? 0 : (applyStarted - fetched) / 1_000_000;
        long applyMs = applyStarted == 0 ? 0 : (now - applyStarted) / 1_000_000;
        String menu = category(request.request.title());
        LOGGER.debug(TIMING, menu, queueMs, requestMs, dispatchMs, applyMs, total / 1_000_000, outcome);
        if (total < 1_000_000_000L) return;
        synchronized (this) {
            if (lastSlowLogNanos != 0 && now - lastSlowLogNanos < 10_000_000_000L) return;
            lastSlowLogNanos = now;
        }
        LOGGER.info(TIMING, menu, queueMs, requestMs, dispatchMs, applyMs, total / 1_000_000, outcome);
    }

    private static String category(String title) {
        return "Co-op Bazaar Orders".equals(title) ? "bazaar_orders_coop"
                : "Your Bazaar Orders".equals(title) ? "bazaar_orders" : "other";
    }

    private record Pending(DescriptionRequest request, Predicate<DescriptionHandler.DescModification[][]> apply,
                           Runnable failed, long generation, long sequence, long enqueued) {}
}
