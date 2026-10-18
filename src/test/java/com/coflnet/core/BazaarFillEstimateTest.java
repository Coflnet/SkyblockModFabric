package com.coflnet.core;

import com.coflnet.core.BazaarBook.Level;
import com.coflnet.core.BazaarOrderLore.Order;
import com.coflnet.core.BazaarOrderLore.Side;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BazaarFillEstimateTest {
    /** Bids 100.2 (300), 100.1 (200), own level 100.0 (150); asks 101.0 (40), own level 101.5 (60), 102.0 (999). */
    private static BazaarBook book(double fillsPerHour) {
        return new BazaarBook(
                List.of(new Level(1001, 200), new Level(1000, 150), new Level(1002, 300)),
                List.of(new Level(1020, 999), new Level(1010, 40), new Level(1015, 60)),
                fillsPerHour, fillsPerHour);
    }

    private static Order buy(long amount, long filled) {
        return new Order(Side.BUY, "Ectoplasm", 1000, amount, filled, filled > 0);
    }

    @Test void buyOrderQueueCountsBetterBidsAndTheRestOfItsLevel() {
        var e = BazaarFillEstimate.estimate(buy(100, 0), book(1000));
        assertTrue(e.outbid());
        assertEquals(300 + 200 + 50, e.itemsAhead());
        assertEquals(2, e.priceGapTenths());
        assertEquals(1003, e.relistPriceTenths(), "one step above the top order");
    }

    @Test void sellOfferQueueCountsCheaperAsks() {
        var e = BazaarFillEstimate.estimate(new Order(Side.SELL, "Ectoplasm", 1015, 60, 0, false), book(1000));
        assertTrue(e.outbid());
        assertEquals(40, e.itemsAhead(), "the own 60 are the whole level, the 102.0 asks are behind");
        assertEquals(5, e.priceGapTenths());
        assertEquals(1009, e.relistPriceTenths(), "one step below the best offer");
    }

    @Test void topOrderIsNotOutbidEvenWithEarlierOrdersAtItsPrice() {
        var e = BazaarFillEstimate.estimate(new Order(Side.BUY, "Ectoplasm", 1002, 100, 0, false), book(1));
        assertFalse(e.outbid());
        assertEquals(200, e.itemsAhead());
        assertFalse(e.suggestRelist());
        assertTrue(BazaarFillEstimate.tooltipLines(null, e).isEmpty());
    }

    @Test void relistOnlyAboveOneHour() {
        // 550 ahead + 100 own = 650 items.
        assertFalse(BazaarFillEstimate.estimate(buy(100, 0), book(650)).suggestRelist(), "exactly one hour");
        assertFalse(BazaarFillEstimate.estimate(buy(100, 0), book(651)).suggestRelist(), "just under an hour");
        var slow = BazaarFillEstimate.estimate(buy(100, 0), book(649));
        assertTrue(slow.suggestRelist(), "just over an hour");
        assertEquals(650.0 / 649, slow.hoursToFill(), 1e-9);
    }

    @Test void missingDataGivesNoSuggestion() {
        var noVolume = BazaarFillEstimate.estimate(buy(100, 0), book(0));
        assertTrue(noVolume.outbid());
        assertTrue(Double.isNaN(noVolume.hoursToFill()));
        assertFalse(noVolume.suggestRelist());
        assertEquals(List.of("§cOutbid: 550 items ahead, 0.2 coins"), BazaarFillEstimate.tooltipLines(buy(100, 0), noVolume));
        assertNull(BazaarFillEstimate.estimate(buy(100, 0), null));
        assertNull(BazaarFillEstimate.estimate(buy(100, 100), book(1)), "fully filled: nothing left to estimate");
    }

    @Test void partlyFilledOrderEstimatesItsRemainderAndClaimsFirst() {
        Order order = buy(100, 60);
        var e = BazaarFillEstimate.estimate(order, book(100));
        assertEquals(300 + 200 + 110, e.itemsAhead(), "own remaining 40 of the 150 at its price");
        assertEquals(6.5, e.hoursToFill(), 1e-9);
        assertEquals(List.of("§cOutbid: 610 items ahead, 0.2 coins", "§7~7h to fill at this price",
                "§eSuggested: claim, cancel and relist at 100.3"), BazaarFillEstimate.tooltipLines(order, e));
    }

    @Test void durationFormat() {
        assertEquals("1m", BazaarFillEstimate.formatDuration(0.001));
        assertEquals("45m", BazaarFillEstimate.formatDuration(0.75));
        assertEquals("3h", BazaarFillEstimate.formatDuration(3.2));
        assertEquals("3d", BazaarFillEstimate.formatDuration(72));
    }
}
