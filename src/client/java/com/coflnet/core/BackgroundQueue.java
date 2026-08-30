package com.coflnet.core;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A tiny background task queue for fire-and-forget work that must never run on
 * the caller's thread (typically the render thread) and must never block the
 * caller even if the work itself blocks (e.g. a network call stuck waiting on
 * a lock held by a stuck reconnect attempt).
 *
 * <p>Backed by a single daemon worker thread pulling from a bounded queue.
 * {@link #submit(Runnable)} always returns immediately: if the queue is full,
 * the OLDEST queued task is dropped to make room for the new one.
 *
 * <p>Rationale for "drop oldest": tasks submitted here carry a snapshot of
 * live state (e.g. "the scoreboard looked like this just now"). If the queue
 * is backed up - most likely because the network connection is down or stuck
 * reconnecting - a stale snapshot sitting behind a backlog is worthless once a
 * newer snapshot exists. Dropping the oldest queued entry keeps the freshest
 * pending work and lets it reach the front of the queue sooner once the
 * worker is unstuck, instead of forcing it to wait behind now-irrelevant
 * older tasks.
 *
 * <p>Tasks that throw are caught, logged, and swallowed - the worker thread
 * keeps running for subsequent tasks.
 */
public final class BackgroundQueue {
    public static final int DEFAULT_CAPACITY = 256;

    private final ArrayBlockingQueue<Runnable> queue;
    private final Thread worker;
    private final AtomicLong droppedCount = new AtomicLong();
    private volatile boolean running = true;

    public BackgroundQueue(String threadName) {
        this(threadName, DEFAULT_CAPACITY);
    }

    public BackgroundQueue(String threadName, int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.worker = new Thread(this::runLoop, threadName);
        this.worker.setDaemon(true);
        this.worker.start();
    }

    /**
     * Queues {@code task} to run on the background worker thread. Never
     * blocks the caller: if the queue is already at capacity, the oldest
     * queued (not-yet-started) task is dropped first to make room.
     */
    public void submit(Runnable task) {
        if (task == null) {
            return;
        }
        while (!queue.offer(task)) {
            Runnable dropped = queue.poll();
            if (dropped != null) {
                droppedCount.incrementAndGet();
            } else {
                // The worker drained the queue concurrently between our offer
                // and poll attempts; try the offer again.
                if (queue.offer(task)) {
                    return;
                }
            }
        }
    }

    /** Number of tasks discarded so far because the queue was full. */
    public long droppedCount() {
        return droppedCount.get();
    }

    /** Current number of tasks waiting to run. Mainly for tests/diagnostics. */
    public int queueSize() {
        return queue.size();
    }

    /** Stops the worker thread. Intended for tests; the mod's queue lives for the process lifetime. */
    public void shutdown() {
        running = false;
        worker.interrupt();
    }

    private void runLoop() {
        while (running) {
            Runnable task;
            try {
                task = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                task.run();
            } catch (Throwable t) {
                System.err.println("[BackgroundQueue] task threw an exception, ignoring: " + t);
            }
        }
    }
}
