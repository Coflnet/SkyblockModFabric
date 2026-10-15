package com.coflnet.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuClassifierTest {
    @Test void pinsHypixelStorageTitles() {
        assertTrue(MenuClassifier.isStorageChest("Ender Chest (2/9)"));
        assertTrue(MenuClassifier.isStorageChest("Large Backpack (Slot #3)"));
        assertTrue(MenuClassifier.isStorageChest("Hunting Toolkit"));
        assertFalse(MenuClassifier.isStorageChest("Bazaar ➜ Oddities"));
    }

    @Test void requiresHypixelTradeShapeAndDivider() {
        assertTrue(MenuClassifier.isTradeTitle(45, "You     VerticleFr"));
        assertTrue(MenuClassifier.isTradeMenu(45, "You     VerticleFr",
                new boolean[]{true, true, true, true, true}));
        assertFalse(MenuClassifier.isTradeMenu(45, "You     VerticleFr",
                new boolean[]{true, true, false, true, true}));
        assertFalse(MenuClassifier.isTradeTitle(54, "You     VerticleFr"));
    }

    @Test void createAuctionTitlesTriggerOnAnyChange() {
        assertTrue(MenuClassifier.shouldReloadOnChestChange("Create BIN Auction", null)); // cleared slot
        assertTrue(MenuClassifier.shouldReloadOnChestChange("Create Auction", "§aSome Item"));
        assertFalse(MenuClassifier.isCreateAuction("Auction House"));
    }

    @Test void storageAndOrdinaryMenusDoNotTriggerOnPlainChange() {
        assertFalse(MenuClassifier.shouldReloadOnChestChange("Ender Chest (1/9)", "§aSome Item"));
        assertFalse(MenuClassifier.shouldReloadOnChestChange("Ender Chest (1/9)", "Combine Items"));
        assertFalse(MenuClassifier.shouldReloadOnChestChange("Harp - Hymn", "§aPlain"));
        assertFalse(MenuClassifier.shouldReloadOnChestChange("Experimentation Table", null));
    }

    @Test void markerNamesTriggerInOtherMenus() {
        assertTrue(MenuClassifier.shouldReloadOnChestChange("Anvil", "§aCombine Items"));
        assertTrue(MenuClassifier.shouldReloadOnChestChange("Bazaar Order", "§aFlip Order"));
    }
}
