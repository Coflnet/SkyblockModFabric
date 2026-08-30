package com.coflnet.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records (hookName, elapsedNanos) samples for the mod's render-thread hooks
 * and keeps a rolling count/max/avg per hook plus the most recent samples
 * that crossed a "slow" threshold, so a stall can be *seen* in {@code /cofl
 * perf} output instead of guessed at.
 */
public class PerfStats {
    /** Aggregated timing for one named hook. */
    public static final class HookStats {
        private long count;
        private long totalNanos;
        private long maxNanos;

        synchronized void record(long nanos) {
            count++;
            totalNanos += nanos;
            if (nanos > maxNanos) {
                maxNanos = nanos;
            }
        }

        public synchronized long count() {
            return count;
        }

        public synchronized double avgMillis() {
            return count == 0 ? 0.0 : (totalNanos / (double) count) / 1_000_000.0;
        }

        public synchronized double maxMillis() {
            return maxNanos / 1_000_000.0;
        }
    }

    /** One sample that crossed the slow-sample threshold. */
    public static final class Sample {
        public final String hook;
        public final long elapsedNanos;
        public final long timestampMillis;

        Sample(String hook, long elapsedNanos, long timestampMillis) {
            this.hook = hook;
            this.elapsedNanos = elapsedNanos;
            this.timestampMillis = timestampMillis;
        }
    }

    private final Map<String, HookStats> stats = new ConcurrentHashMap<>();
    private final Deque<Sample> slowSamples = new ArrayDeque<>();
    private final long slowThresholdNanos;
    private final int maxSlowSamples;

    public PerfStats(long slowThresholdNanos, int maxSlowSamples) {
        this.slowThresholdNanos = slowThresholdNanos;
        this.maxSlowSamples = maxSlowSamples;
    }

    /** Records one sample for {@code hook}. Safe to call from any thread. */
    public void record(String hook, long elapsedNanos) {
        stats.computeIfAbsent(hook, h -> new HookStats()).record(elapsedNanos);
        if (elapsedNanos >= slowThresholdNanos) {
            synchronized (slowSamples) {
                slowSamples.addLast(new Sample(hook, elapsedNanos, System.currentTimeMillis()));
                while (slowSamples.size() > maxSlowSamples) {
                    slowSamples.removeFirst();
                }
            }
        }
    }

    public HookStats get(String hook) {
        return stats.get(hook);
    }

    public List<Sample> recentSlowSamples() {
        synchronized (slowSamples) {
            return new ArrayList<>(slowSamples);
        }
    }

    /** Clears all recorded stats and slow samples (backs {@code /cofl perf reset}). */
    public void reset() {
        stats.clear();
        synchronized (slowSamples) {
            slowSamples.clear();
        }
    }

    public boolean isEmpty() {
        return stats.isEmpty();
    }

    /** Renders a readable table of per-hook stats plus recent slow samples. */
    public String format() {
        if (stats.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-32s %8s %10s %10s", "hook", "count", "avg(ms)", "max(ms)"));
        List<String> hooks = new ArrayList<>(stats.keySet());
        Collections.sort(hooks);
        for (String hook : hooks) {
            HookStats s = stats.get(hook);
            if (s == null) continue;
            sb.append('\n').append(String.format("%-32s %8d %10.2f %10.2f",
                    hook, s.count(), s.avgMillis(), s.maxMillis()));
        }
        List<Sample> slow = recentSlowSamples();
        if (!slow.isEmpty()) {
            sb.append("\nslow samples (>=").append(slowThresholdNanos / 1_000_000.0).append("ms):");
            for (Sample sample : slow) {
                sb.append('\n').append(String.format("  %s: %.2fms", sample.hook, sample.elapsedNanos / 1_000_000.0));
            }
        }
        return sb.toString();
    }
}
