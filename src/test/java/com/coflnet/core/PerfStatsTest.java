package com.coflnet.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfStatsTest {

    @Test
    void tracksCountAvgAndMaxPerHook() {
        PerfStats stats = new PerfStats(TimeUnit_MS(25), 10);
        stats.record("hookA", TimeUnit_MS(10));
        stats.record("hookA", TimeUnit_MS(20));
        stats.record("hookA", TimeUnit_MS(30));

        PerfStats.HookStats hookA = stats.get("hookA");
        assertEquals(3, hookA.count());
        assertEquals(20.0, hookA.avgMillis(), 0.01);
        assertEquals(30.0, hookA.maxMillis(), 0.01);
    }

    @Test
    void keepsOnlyRecentSlowSamplesUpToLimit() {
        PerfStats stats = new PerfStats(TimeUnit_MS(25), 2);
        stats.record("hookA", TimeUnit_MS(5)); // below threshold, not a slow sample
        stats.record("hookA", TimeUnit_MS(30));
        stats.record("hookA", TimeUnit_MS(40));
        stats.record("hookA", TimeUnit_MS(50));

        List<PerfStats.Sample> slow = stats.recentSlowSamples();
        assertEquals(2, slow.size());
        assertEquals(40_000_000L, slow.get(0).elapsedNanos);
        assertEquals(50_000_000L, slow.get(1).elapsedNanos);
    }

    @Test
    void resetClearsStatsAndSamples() {
        PerfStats stats = new PerfStats(TimeUnit_MS(25), 5);
        stats.record("hookA", TimeUnit_MS(30));
        assertTrue(!stats.isEmpty());

        stats.reset();
        assertTrue(stats.isEmpty());
        assertTrue(stats.recentSlowSamples().isEmpty());
    }

    @Test
    void formatIsEmptyWithNoSamplesAndListsHooksAfterward() {
        PerfStats stats = new PerfStats(TimeUnit_MS(25), 5);
        assertEquals("", stats.format());

        stats.record("hookB", TimeUnit_MS(5));
        String formatted = stats.format();
        assertTrue(formatted.contains("hookB"));
    }

    private static long TimeUnit_MS(long millis) {
        return millis * 1_000_000L;
    }
}
