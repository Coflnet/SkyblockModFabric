package com.coflnet.core;

import com.coflnet.core.BazaarBook.Level;
import com.coflnet.core.BazaarGuide.Stage;
import com.coflnet.core.BazaarOrderLore.Order;
import com.coflnet.core.BazaarOrderLore.Side;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BazaarGuideTest {
    private static final int CONTAINER = 54;

    /** A menu of {@link #CONTAINER} slots plus the 36 inventory slots with the given names at the given indices. */
    private static List<String> menu(Object... indexAndName) {
        List<String> names = new ArrayList<>(Arrays.asList(new String[CONTAINER + 36]));
        for (int i = 0; i < indexAndName.length; i += 2) names.set((Integer) indexAndName[i], (String) indexAndName[i + 1]);
        return names;
    }

    private static BazaarFillEstimate.Estimate slow(Order order) {
        BazaarBook book = new BazaarBook(List.of(new Level(order.priceTenths() + 1, 5000)),
                List.of(new Level(order.priceTenths() - 1, 5000)), 10, 10);
        return BazaarFillEstimate.estimate(order, book);
    }

    @Test void freshOrderIsGuidedOnlyWhereTheMenuTellsTheSide() {
        BazaarGuide guide = new BazaarGuide();
        assertEquals(12, guide.nextSlot("How much do you want to pay?", menu(10, "Same as Top Order", 12, "§6Top Order +0.1"), null, CONTAINER, false, 0));
        assertEquals(13, guide.nextSlot("Confirm Sell Offer", menu(13, "Sell Offer"), null, CONTAINER, false, 0));
        assertEquals(-1, guide.nextSlot("How many do you want?", menu(10, "Custom Amount"), null, CONTAINER, false, 0),
                "the player picks a fresh amount");
        assertEquals(-1, guide.nextSlot("Farming ➜ Wheat", menu(15, "Create Buy Order"), null, CONTAINER, false, 0));
        assertNull(guide.expectedSignValue("to order", 0));
    }

    @Test void activeGoalStepWins() {
        BazaarGuide guide = new BazaarGuide();
        assertEquals(-1, guide.nextSlot("How much do you want to pay?", menu(12, "Top Order +0.1"), null, CONTAINER, true, 0));
    }

    @Test void buyOrderRelistIsGuidedFromCancelToConfirm() {
        BazaarGuide guide = new BazaarGuide();
        Order order = new Order(Side.BUY, "Vermin Vaporizer Chip", 1000, 20, 7, false);
        guide.orderClicked(order, slow(order), false, 0);
        assertEquals(Stage.CANCEL, guide.stage(0));
        assertEquals(13, guide.nextSlot("Order options", menu(11, "Flip Order", 13, "Cancel Order"), null, CONTAINER, false, 1));
        guide.chat("§6[Bazaar] §7Cancelled! Refunded §61,200 coins §7from cancelling Buy Order!", 2);
        assertEquals(Stage.PLACE, guide.stage(2));
        assertEquals(49, guide.nextSlot("Your Bazaar Orders", menu(10, "BUY Ectoplasm", 49, "Go Back"), null, CONTAINER, false, 3));
        assertEquals(45, guide.nextSlot("Bazaar ➜ Farming", menu(11, "Wheat", 45, "Search"), null, CONTAINER, false, 3));
        assertEquals(11, guide.nextSlot("Bazaar ➜ \"vermin\"", menu(11, "Vermin Vaporizer Chip", 45, "Search"), null, CONTAINER, false, 3));
        assertEquals(15, guide.nextSlot("Garden Chips ➜ Vermin Vaporizer", menu(15, "Create Buy Order", 16, "Create Sell Offer"), null, CONTAINER, false, 3),
                "product page titles are cut short");
        assertEquals(-1, guide.nextSlot("Mining ➜ Vermin", menu(15, "Create Buy Order"), null, CONTAINER, true, 3));
        assertEquals(16, guide.nextSlot("How many do you want?", menu(16, "Custom Amount"), null, CONTAINER, false, 4));
        assertEquals("12", guide.expectedSignValue("to order", 5), "the refunded rest (1,200 coins / 100.0) is placed again");
        assertNull(guide.expectedSignValue("Enter query", 5));
        assertEquals(12, guide.nextSlot("How much do you want to pay?", menu(12, "Top Order +0.1"), null, CONTAINER, false, 6));
        assertEquals(-1, guide.nextSlot("At what price are you selling?", menu(12, "Best Offer -0.1"), null, CONTAINER, false, 6));
        guide.chat("[Bazaar] Buy Order Setup! 13x Vermin Vaporizer Chip for 13,013 coins.", 7);
        assertEquals(Stage.NONE, guide.stage(7));
    }

    @Test void sellOfferRelistUsesTheRefundedItemsInTheInventory() {
        BazaarGuide guide = new BazaarGuide();
        Order order = new Order(Side.SELL, "Ectoplasm", 2000, 51, 0, false);
        guide.orderClicked(order, slow(order), false, 0);
        guide.chat("[Bazaar] Cancelled! Refunded 51x Ectoplasm from cancelling Sell Offer!", 1);
        assertEquals(CONTAINER + 3, guide.nextSlot("Your Bazaar Orders", menu(10, "Ectoplasm", CONTAINER + 3, "Ectoplasm"), null, CONTAINER, false, 2),
                "the inventory stack, not an order slot of the same name");
        assertEquals(16, guide.nextSlot("Dungeons ➜ Ectoplasm", menu(15, "Create Buy Order", 16, "Create Sell Offer"), null, CONTAINER, false, 3));
        assertEquals(12, guide.nextSlot("At what price are you selling?", menu(12, "Best Offer -0.1"), null, CONTAINER, false, 4));
        assertNull(guide.expectedSignValue("to order", 4), "sell offers have no amount sign");
        guide.chat("[Bazaar] Sell Offer Setup! 51x Ectoplasm for 100,000 coins.", 5);
        assertEquals(Stage.NONE, guide.stage(5));
    }

    @Test void onlySuggestedOrdersStartARelist() {
        BazaarGuide guide = new BazaarGuide();
        Order order = new Order(Side.BUY, "Ectoplasm", 1000, 20, 0, false);
        BazaarFillEstimate.Estimate quick = BazaarFillEstimate.estimate(order,
                new BazaarBook(List.of(new Level(1001, 1)), List.of(), 1000, 1000));
        guide.orderClicked(order, quick, false, 0);
        assertEquals(Stage.NONE, guide.stage(0), "outbid but filling within the hour");
        guide.orderClicked(order, slow(order), false, 0);
        guide.orderClicked(order, quick, false, 1);
        assertEquals(Stage.NONE, guide.stage(1), "clicking another order abandons the cancel step");
        assertEquals(-1, guide.nextSlot("Order options", menu(13, "Cancel Order"), null, CONTAINER, false, 1));
    }

    @Test void idleRelistExpires() {
        BazaarGuide guide = new BazaarGuide();
        Order order = new Order(Side.BUY, "Ectoplasm", 1000, 20, 0, false);
        guide.orderClicked(order, slow(order), false, 0);
        assertEquals(Stage.CANCEL, guide.stage(BazaarGuide.IDLE_TIMEOUT_MS));
        assertEquals(Stage.NONE, guide.stage(BazaarGuide.IDLE_TIMEOUT_MS + 1));
    }

    @Test void claimableOrderIsClaimedBeforeItsCancel() {
        BazaarGuide guide = new BazaarGuide();
        Order order = new Order(Side.BUY, "Ectoplasm", 1000, 20, 7, true);
        Order other = new Order(Side.BUY, "Ectoplasm", 990, 5, 0, false);
        Order[] slots = new Order[CONTAINER];
        slots[10] = other;
        slots[12] = order;
        guide.orderClicked(order, slow(order), false, 0);
        assertEquals(Stage.CLAIM, guide.stage(0));
        assertEquals(12, guide.nextSlot("Your Bazaar Orders", menu(10, "BUY Ectoplasm", 12, "BUY Ectoplasm"), slots, CONTAINER, false, 1),
                "the clicked order, not another order of the same item");
        assertEquals(-1, guide.nextSlot("Order options", menu(13, "Cancel Order"), null, CONTAINER, false, 1));
        guide.chat("[Bazaar] Claimed 7x Ectoplasm worth 700 coins bought for 100 each!", 2);
        assertEquals(Stage.CANCEL, guide.stage(2));
        assertEquals(12, guide.nextSlot("Your Bazaar Orders", menu(12, "BUY Ectoplasm"), slots, CONTAINER, false, 3), "open its options");
        assertEquals(13, guide.nextSlot("Order options", menu(13, "Cancel Order"), null, CONTAINER, false, 3));
    }

    @Test void activeGoalStepCannotStartALocalRelist() {
        BazaarGuide guide = new BazaarGuide();
        Order order = new Order(Side.BUY, "Ectoplasm", 1000, 20, 0, false);
        guide.orderClicked(order, slow(order), true, 0);
        assertEquals(Stage.NONE, guide.stage(0));
    }

    @Test void relevantMenus() {
        assertTrue(BazaarGuide.isRelevantMenu("Co-op Bazaar Orders"));
        assertTrue(BazaarGuide.isRelevantMenu("Garden Chips ➜ Vermin Vaporizer"));
        assertTrue(BazaarGuide.isRelevantMenu("Confirm Buy Order"));
        assertFalse(BazaarGuide.isRelevantMenu("Ender Chest (1/9)"));
        assertFalse(BazaarGuide.isRelevantMenu(null));
    }

    @Test void armedGoalSignFillGuidesTheMenu() {
        assertFalse(BazaarGuide.goalGuidesMenu(false, null, 0, 10));
        assertTrue(BazaarGuide.goalGuidesMenu(true, null, 0, 10), "a backend highlight in the menu");
        assertTrue(BazaarGuide.goalGuidesMenu(false, "{\"line\":\"to order\",\"value\":\"5\"}", 0, 10), "a click-armed fill never expires");
        assertFalse(BazaarGuide.goalGuidesMenu(false, "{\"line\":\"to order\",\"value\":\"5\"}", 5, 10), "an expired fill");
        assertFalse(BazaarGuide.goalGuidesMenu(false, " ", 0, 10));
    }

    @Test void samePriceOrdersAreNotConfused() {
        BazaarGuide guide = new BazaarGuide();
        Order selected = new Order(Side.BUY, "Ectoplasm", 1000, 20, 7, true);
        Order smaller = new Order(Side.BUY, "Ectoplasm", 1000, 5, 0, false);
        Order[] slots = new Order[CONTAINER];
        slots[10] = smaller;
        slots[12] = selected;
        guide.orderClicked(selected, slow(selected), false, 0);
        guide.chat("[Bazaar] Claimed 7x Ectoplasm worth 700 coins bought for 100 each!", 1);
        slots[12] = new Order(Side.BUY, "Ectoplasm", 1000, 20, 7, false); // claimed: same total amount, nothing to claim
        List<String> names = menu(10, "BUY Ectoplasm", 12, "BUY Ectoplasm");
        assertEquals(12, guide.nextSlot("Your Bazaar Orders", names, slots, CONTAINER, false, 2),
                "the 20-item order, not the 5-item one at the same price");
        slots[14] = new Order(Side.BUY, "Ectoplasm", 1000, 20, 0, false);
        assertEquals(-1, guide.nextSlot("Your Bazaar Orders", names, slots, CONTAINER, false, 2),
                "two indistinguishable orders: no outline");
    }
}
