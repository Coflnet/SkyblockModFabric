package com.coflnet.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ChestChangeDebouncerTest {
    // create auction menu: 54 chest slots + 36 player slots
    private static final int TOTAL = 90;

    @Test
    void listingSlotChangeNeedsReload() {
        // regression: slot 13 (< 36) used to be swallowed by the trade-slot branch
        assertTrue(ChestChangeDebouncer.needsReload(13, TOTAL, true, false));
        assertTrue(ChestChangeDebouncer.needsReload(29, TOTAL, true, false));
    }

    @Test
    void clearingStillCountsAsChange() {
        // the caller reports "changed" for item -> empty as well
        assertTrue(ChestChangeDebouncer.needsReload(13, TOTAL, true, false));
    }

    @Test
    void unchangedPlayerInventoryAndTradeAreIgnored() {
        assertFalse(ChestChangeDebouncer.needsReload(13, TOTAL, false, false));
        assertFalse(ChestChangeDebouncer.needsReload(54, TOTAL, true, false));
        assertFalse(ChestChangeDebouncer.needsReload(89, TOTAL, true, false));
        assertFalse(ChestChangeDebouncer.needsReload(-1, TOTAL, true, false));
        assertFalse(ChestChangeDebouncer.needsReload(13, TOTAL, true, true));
    }

    @Test
    void burstIsCoalescedIntoOneReload() {
        var d = new ChestChangeDebouncer(150, 600);
        assertTrue(d.markChanged(0));
        assertFalse(d.markChanged(50));
        assertFalse(d.markChanged(100));
        assertEquals(150, d.poll(100));      // quiet period restarted by the last packet
        assertEquals(0, d.poll(250));        // due exactly once
        assertEquals(-1, d.poll(300));       // nothing pending, waiter ends
        assertTrue(d.markChanged(400));      // next change starts a fresh waiter
    }

    @Test
    void constantlyChangingMenuStillReloadsAfterMaxWait() {
        var d = new ChestChangeDebouncer(150, 600);
        d.markChanged(0);
        for (long t = 100; t < 600; t += 100) {
            d.markChanged(t);
            assertTrue(d.poll(t) > 0);
        }
        assertEquals(0, d.poll(600));
    }

    @Test
    void resetDropsPendingReload() {
        var d = new ChestChangeDebouncer(150, 600);
        d.markChanged(0);
        d.reset();
        assertEquals(-1, d.poll(1000));
    }
}
