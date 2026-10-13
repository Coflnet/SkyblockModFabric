package com.coflnet.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GoalProgressTriggerTest {
    @Test
    void verifiedLogLinesMatch() {
        assertTrue(GoalProgressTrigger.isProgressMessage("You purchased Wither Leggings for 3,800,000 coins!"));
        assertTrue(GoalProgressTrigger.isProgressMessage("You claimed Wither Leggings from [MVP+] Wzxh's auction!"));
        assertTrue(GoalProgressTrigger.isProgressMessage("You Supercrafted Necron's Leggings!"));
        assertTrue(GoalProgressTrigger.isProgressMessage("[Bazaar] Bought 8x Diamante's Handle for 4,514,236 coins!"));
    }

    @Test
    void formattingCodesAreStripped() {
        assertTrue(GoalProgressTrigger.isProgressMessage("\u00a7aYou \u00a7bpurchased Wither Leggings for 1 coins!"));

    }

    @Test
    void quotedChatDoesNotMatch() {
        assertFalse(GoalProgressTrigger.isProgressMessage(
                "[\u00a71C\u00a76hat\u00a7f] \u00a76Clastical\u00a7f: \u00a7fYou purchased Trapper Crest for 500 coins!"));
        assertFalse(GoalProgressTrigger.isProgressMessage(
                "[117] \u00a7b[MVP\u00a7c+\u00a7b] Name\u00a7f: You purchased Trapper Crest for 500 coins!"));
        assertFalse(GoalProgressTrigger.isProgressMessage(" You purchased X"));
        assertFalse(GoalProgressTrigger.isProgressMessage(null));
        assertFalse(GoalProgressTrigger.isProgressMessage(""));
    }

    @Test
    void burstCollapsesIntoOneRefreshAfterDelay() {
        var t = new GoalProgressTrigger(400, 2000);
        t.trigger(1000);
        t.trigger(1010); // purchase + claim in the same tick
        assertFalse(t.pollDue(1300));
        assertFalse(t.pollDue(1409));
        assertTrue(t.pollDue(1410));
        assertFalse(t.pollDue(1411));
    }

    @Test
    void atMostOneRefreshPerInterval() {
        var t = new GoalProgressTrigger(400, 2000);
        t.trigger(0);
        assertTrue(t.pollDue(400));
        t.trigger(500);
        assertFalse(t.pollDue(900));
        assertFalse(t.pollDue(2399));
        assertTrue(t.pollDue(2400));
    }

    @Test
    void nothingPendingNothingDue() {
        var t = new GoalProgressTrigger(400, 2000);
        assertFalse(t.pollDue(100_000));
        t.trigger(0);
        t.reset();
        assertFalse(t.pollDue(100_000));
    }

    @Test
    void fingerprintIgnoresOrderAndSplitting() {
        long a = GoalProgressTrigger.fingerprint(new String[] {"A", "B", "", null, "A"}, new int[] {10, 1, 0, 0, 5});
        long b = GoalProgressTrigger.fingerprint(new String[] {"A", null, "B", "A"}, new int[] {15, 0, 1, 0});
        long c = GoalProgressTrigger.fingerprint(new String[] {"B", "A"}, new int[] {1, 15});
        assertEquals(a, b);
        assertEquals(a, c);
    }

    @Test
    void fingerprintDetectsGainLossAndCountChange() {
        long base = GoalProgressTrigger.fingerprint(new String[] {"A", "B"}, new int[] {1, 1});
        assertNotEquals(base, GoalProgressTrigger.fingerprint(new String[] {"A"}, new int[] {1}));
        assertNotEquals(base, GoalProgressTrigger.fingerprint(new String[] {"A", "B", "C"}, new int[] {1, 1, 1}));
        assertNotEquals(base, GoalProgressTrigger.fingerprint(new String[] {"A", "B"}, new int[] {1, 2}));
        assertNotEquals(base, GoalProgressTrigger.fingerprint(new String[] {"A", "C"}, new int[] {1, 1}));
    }

    @Test
    void observeInventoryOnlyReportsChangesWithinAMenu() {
        var t = new GoalProgressTrigger();
        Object menu = new Object();
        assertFalse(t.observeInventory(menu, 5)); // baseline
        assertFalse(t.observeInventory(menu, 5));
        assertTrue(t.observeInventory(menu, 6));
        assertFalse(t.observeInventory(menu, 6));
        assertFalse(t.observeInventory(new Object(), 7)); // new menu re-baselines
    }

    @Test
    void fingerprintCheckIsRateLimited() {
        var t = new GoalProgressTrigger();
        assertTrue(t.shouldCheckFingerprint(0));
        assertFalse(t.shouldCheckFingerprint(100));
        assertTrue(t.shouldCheckFingerprint(GoalProgressTrigger.FINGERPRINT_CHECK_INTERVAL_MS));
    }

    @Test
    void watchWindowCoversLateItemWithoutScreen() {
        var t = new GoalProgressTrigger();
        t.openWatch(1000);
        assertTrue(t.isWatching(1000));
        assertTrue(t.isWatching(1000 + GoalProgressTrigger.WATCH_WINDOW_MS - 1));
        assertFalse(t.isWatching(1000 + GoalProgressTrigger.WATCH_WINDOW_MS));
        assertFalse(new GoalProgressTrigger().isWatching(0));
    }

    @Test
    void changeInsideWindowTriggersWithNoScreenSentinel() {
        var t = new GoalProgressTrigger();
        t.openWatch(0);
        assertFalse(t.observeInventory(GoalProgressTrigger.NO_SCREEN, 1)); // baseline
        assertTrue(t.isWatching(3000));
        assertTrue(t.observeInventory(GoalProgressTrigger.NO_SCREEN, 2));
    }

    @Test
    void changeAfterWindowIsNotWatched() {
        var t = new GoalProgressTrigger();
        t.openWatch(0);
        t.observeInventory(GoalProgressTrigger.NO_SCREEN, 1);
        assertFalse(t.isWatching(GoalProgressTrigger.WATCH_WINDOW_MS + 1)); // caller skips observing and drops the baseline
        t.menuClosed();
        assertFalse(t.observeInventory(GoalProgressTrigger.NO_SCREEN, 2)); // re-baseline, no trigger
    }

    @Test
    void resetClosesWatchWindow() {
        var t = new GoalProgressTrigger();
        t.openWatch(0);
        t.reset();
        assertFalse(t.isWatching(1));
    }

    @Test
    void repairOnlyAfterNoScreenUploadWithOpenContainer() {
        assertTrue(GoalProgressTrigger.shouldRepairOpenScreen(true, true));
        assertFalse(GoalProgressTrigger.shouldRepairOpenScreen(true, false));
        assertFalse(GoalProgressTrigger.shouldRepairOpenScreen(false, true));
    }
}
