package com.coflnet.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ChestOpenPolicyTest {
    private static boolean[] slots(int total) {
        boolean[] s = new boolean[total];
        Arrays.fill(s, true);
        return s;
    }

    @Test
    void emptyChestPartDefers() {
        assertFalse(ChestOpenPolicy.shouldUploadOnOpen(slots(90), false, false));
    }

    @Test
    void filledChestSlotUploadsNow() {
        boolean[] s = slots(90);
        s[53] = false;
        assertTrue(ChestOpenPolicy.shouldUploadOnOpen(s, false, false));
    }

    @Test
    void emptyChestWithFilledPlayerInventoryDefers() {
        boolean[] s = slots(90);
        for (int i = 54; i < 90; i++) s[i] = false;
        assertFalse(ChestOpenPolicy.shouldUploadOnOpen(s, false, false));
    }

    @Test
    void playerInventoryScreenUploadsNow() {
        assertTrue(ChestOpenPolicy.shouldUploadOnOpen(slots(46), true, false));
    }

    @Test
    void tradeUploadsNow() {
        assertTrue(ChestOpenPolicy.shouldUploadOnOpen(slots(81), false, true));
    }

    @Test
    void deferredMarkCoalescesWithSlotPacketMark() {
        ChestChangeDebouncer d = new ChestChangeDebouncer(150, 600);
        assertTrue(d.markChanged(0));
        assertFalse(d.markChanged(100));
        assertEquals(150, d.poll(100));
        assertEquals(0, d.poll(250));
        assertEquals(-1, d.poll(251));
    }

    @Test
    void resetOnCloseDropsPendingUpload() {
        ChestChangeDebouncer d = new ChestChangeDebouncer(150, 600);
        d.markChanged(0);
        d.reset();
        assertEquals(-1, d.poll(1000));
    }
}
