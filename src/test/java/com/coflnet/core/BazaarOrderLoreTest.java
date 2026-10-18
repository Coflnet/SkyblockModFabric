package com.coflnet.core;

import com.coflnet.core.BazaarOrderLore.Side;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BazaarOrderLoreTest {
    @Test void parsesPartlyFilledBuyOrder() {
        var order = BazaarOrderLore.parse("§a§lBUY §aGlacite Jewel", List.of("§8Worth 3.4M coins", "",
                "§7Order amount: §a20§7x", "§7Filled: §a7§7/20 §8(35%)", "", "§7Price per unit: §6172,341.3 coins",
                "", "§eYou have §a7 items §eto claim!"));
        assertEquals(Side.BUY, order.side());
        assertEquals("Glacite Jewel", order.itemName());
        assertEquals(1_723_413, order.priceTenths());
        assertEquals(13, order.remaining());
        assertTrue(order.claimable());
    }

    @Test void parsesSellOfferWithoutFill() {
        var order = BazaarOrderLore.parse("SELL Vampirism VI", List.of("Offer amount: 1,548x", "Price per unit: 1,649,533 coins",
                "Click to view options!"));
        assertEquals(Side.SELL, order.side());
        assertEquals(1548, order.remaining());
        assertEquals(16_495_330, order.priceTenths());
        assertFalse(order.claimable());
    }

    @Test void otherSlotsAreNoOrders() {
        assertNull(BazaarOrderLore.parse("Claim All Coins", List.of("You don't have any coins to claim.")));
        assertNull(BazaarOrderLore.parse("BUY Ectoplasm", List.of("Order amount: 3x")), "no price");
        assertNull(BazaarOrderLore.parse(null, List.of()));
    }

    @Test void formatsTenths() {
        assertEquals("1,649,533.1", BazaarOrderLore.formatTenths(16_495_331));
        assertEquals("514", BazaarOrderLore.formatTenths(5140));
        assertEquals("0.1", BazaarOrderLore.formatTenths(1));
    }

    @Test void abbreviatedFillNeverUnderstatesTheRemainder() {
        var order = BazaarOrderLore.parse("BUY Enchanted Coal", List.of("Order amount: 30,000x", "Filled: 29k/30k (96.7%)",
                "Price per unit: 120.5 coins"));
        assertEquals(29_000, order.filled());
        assertEquals(1_000, order.remaining());
        var book = new BazaarBook(List.of(new BazaarBook.Level(1210, 500)), List.of(), 1_200, 1_200);
        var estimate = BazaarFillEstimate.estimate(order, book);
        assertEquals(1.25, estimate.hoursToFill(), 1e-9, "the 1,000 left, not the whole 30,000");
        assertTrue(estimate.suggestRelist());
        var huge = BazaarOrderLore.parse("SELL Wheat", List.of("Offer amount: 1,000x", "Filled: 1.2k/1k", "Price per unit: 2 coins"));
        assertEquals(0, huge.remaining(), "a fill is never above the amount");
    }
}
