package com.coflnet;

import com.coflnet.core.PerfStats;

/**
 * Dev-mode performance tracing for the mod's render-thread hooks (mixins,
 * screen-event handlers, the tooltip callback, ...).
 *
 * <p>{@link #begin()} / {@link #end(String, long)} are meant to wrap a hook's
 * body. They are cheap to call even when dev mode is off: a single volatile
 * boolean read, no config lookup, so leaving the calls in place has no
 * measurable cost in normal play.
 *
 * <p>Also drives an optional render-thread watchdog: while dev mode is on, a
 * daemon thread checks that {@link #recordFrame()} is still being called
 * regularly (fed from a client tick event); if no frame is recorded for more
 * than {@link #STALL_THRESHOLD_NANOS}, it captures and logs the render
 * thread's stack trace (rate-limited to once per {@link
 * #STALL_LOG_RATE_LIMIT_NANOS}) so a stall shows up as an actual stack trace
 * instead of a guess.
 */
public final class PerfTracer {
    private static final long SLOW_HOOK_THRESHOLD_NANOS = 25_000_000L; // 25ms
    private static final long STALL_THRESHOLD_NANOS = 300_000_000L; // 300ms
    private static final long STALL_LOG_RATE_LIMIT_NANOS = 5_000_000_000L; // 5s
    private static final long WATCHDOG_POLL_MS = 50L;
    private static final int MAX_SLOW_SAMPLES = 50;

    private static final PerfStats STATS = new PerfStats(SLOW_HOOK_THRESHOLD_NANOS, MAX_SLOW_SAMPLES);

    private static volatile boolean enabled = false;
    private static volatile Thread renderThread;
    private static volatile long lastFrameNanos = System.nanoTime();
    private static volatile long lastStallLogNanos = 0L;

    private static final Object watchdogLock = new Object();
    private static Thread watchdogThread;

    private PerfTracer() {
    }

    public static PerfStats stats() {
        return STATS;
    }

    /** Returns a start timestamp, or 0 when dev mode is off (cheap no-op path). */
    public static long begin() {
        return enabled ? System.nanoTime() : 0L;
    }

    /** Records the elapsed time for {@code hook} if {@code start} came from an enabled {@link #begin()}. */
    public static void end(String hook, long start) {
        if (start == 0L) {
            return;
        }
        long elapsed = System.nanoTime() - start;
        STATS.record(hook, elapsed);
        if (elapsed >= SLOW_HOOK_THRESHOLD_NANOS) {
            System.out.println("[CoflPerf] " + hook + " took " + (elapsed / 1_000_000.0) + "ms");
        }
    }

    /**
     * Marks that a frame/tick just happened on the calling thread. Cheap
     * (two volatile writes); safe to call unconditionally every client tick.
     */
    public static void recordFrame() {
        renderThread = Thread.currentThread();
        lastFrameNanos = System.nanoTime();
    }

    /** Wired from DevManager so the watchdog only runs while dev mode is on. */
    public static void setDevModeEnabled(boolean value) {
        enabled = value;
        if (value) {
            startWatchdog();
        } else {
            stopWatchdog();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static void startWatchdog() {
        synchronized (watchdogLock) {
            if (watchdogThread != null && watchdogThread.isAlive()) {
                return;
            }
            lastFrameNanos = System.nanoTime();
            Thread thread = new Thread(PerfTracer::watchdogLoop, "CoflSky-RenderWatchdog");
            thread.setDaemon(true);
            watchdogThread = thread;
            thread.start();
        }
    }

    private static void stopWatchdog() {
        synchronized (watchdogLock) {
            if (watchdogThread != null) {
                watchdogThread.interrupt();
                watchdogThread = null;
            }
        }
    }

    private static void watchdogLoop() {
        while (enabled) {
            try {
                Thread.sleep(WATCHDOG_POLL_MS);
            } catch (InterruptedException e) {
                return;
            }
            Thread rt = renderThread;
            if (rt == null) {
                continue;
            }
            long now = System.nanoTime();
            long sinceFrame = now - lastFrameNanos;
            if (sinceFrame < STALL_THRESHOLD_NANOS) {
                continue;
            }
            if (now - lastStallLogNanos < STALL_LOG_RATE_LIMIT_NANOS) {
                continue;
            }
            lastStallLogNanos = now;
            StackTraceElement[] trace = rt.getStackTrace();
            StringBuilder sb = new StringBuilder();
            sb.append("[CoflPerf] Render thread stalled for ").append(sinceFrame / 1_000_000).append("ms:");
            for (StackTraceElement element : trace) {
                sb.append("\n    at ").append(element);
            }
            System.out.println(sb);
            STATS.record("renderThreadStall", sinceFrame);
        }
    }
}
