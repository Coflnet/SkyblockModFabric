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
    void bazaarFillsAndCancellationsRefreshGoals() {
        assertTrue(GoalProgressTrigger.isProgressMessage(
                "[Bazaar] Your Buy Order for 1,280x Enchanted Diamond was filled!"));
        assertTrue(GoalProgressTrigger.isProgressMessage(
                "§e[Bazaar] Your Sell Offer for 8x Diamante's Handle was filled!"));
        assertTrue(GoalProgressTrigger.isProgressMessage(
                "[Bazaar] Cancelled! Refunded 52x Green Candy from cancelling Sell Offer!"));
        assertTrue(GoalProgressTrigger.isProgressMessage("You cancelled your auction for ◆ Hot Rune I!"));
        assertFalse(GoalProgressTrigger.isProgressMessage(
                "[MVP+] Player: [Bazaar] Your Buy Order for 1x Diamond was filled!"));
        assertFalse(GoalProgressTrigger.isProgressMessage(
                "[Bazaar] Your Buy Order for Diamond is not filled!"));
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
    void forcedLoginRefreshSurvivesWaitingForPlayerAndIgnoresFingerprint() {
        var t = new GoalProgressTrigger();
        Object menu = new Object();
        t.observeInventory(menu, 42);
        t.forceRefresh(1000);
        assertTrue(t.hasForcedRefresh()); // caller can bypass the cleared goal HUD gate
        assertFalse(t.observeInventory(menu, 42)); // no inventory change needed
        // The client does not poll while the player or description context is unavailable.
        assertTrue(t.hasForcedRefresh());
        assertTrue(t.pollDue(20_000));
        assertTrue(t.consumeForcedRefresh());
        assertFalse(t.hasForcedRefresh());
        assertFalse(t.pollDue(20_001));
        assertFalse(t.consumeForcedRefresh());
    }

    @Test
    void disconnectDropsForcedRefreshAndResetsPreviousSessionThrottle() {
        var t = new GoalProgressTrigger(400, 2000);
        t.forceRefresh(0);
        assertTrue(t.pollDue(400));
        t.forceRefresh(500);
        t.reset();
        assertFalse(t.hasForcedRefresh());
        assertFalse(t.pollDue(10_000));
        t.forceRefresh(600);
        assertTrue(t.pollDue(1000)); // new session does not inherit the old minimum interval
        assertTrue(t.consumeForcedRefresh());
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

}
