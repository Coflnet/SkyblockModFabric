package com.coflnet.core;

public final class MenuClassifier {
    private MenuClassifier() {
    }

    public static boolean isBazaarOrders(String title) {
        return "Your Bazaar Orders".equals(title) || "Co-op Bazaar Orders".equals(title);
    }

    /** The auction listing menus; their listing slot changes while the menu stays open. */
    public static boolean isCreateAuction(String title) {
        return "Create Auction".equals(title) || "Create BIN Auction".equals(title);
    }

    /** Packet item names that mark a menu whose descriptions need an immediate refresh. */
    public static boolean isReloadMarkerName(String itemName) {
        return itemName != null
                && (itemName.contains("Combine Items") // anvil result
                || itemName.equals("§aFlip Order")); // bazaar order flip prices loaded
    }

    /**
     * Whether a real change of a chest slot should reload descriptions. Storage never does; the
     * create-auction menus always do; everything else only for marker items.
     */
    public static boolean shouldReloadOnChestChange(String title, String packetItemName) {
        if (isStorageChest(title)) {
            return false;
        }
        return isCreateAuction(title) || isReloadMarkerName(packetItemName);
    }

    public static boolean isStorageChest(String title) {
        if (title == null) {
            return false;
        }
        return title.startsWith("Ender Chest")
                || title.contains("Backpack (Slot")
                || title.equals("Chest") || title.equals("Large Chest")
                || title.equals("Chest Storage") || title.equals("Medium Shelves") || title.contains("Chest+")
                || title.contains("Huntaxe") || title.startsWith("Hunting Toolkit");
    }

    public static boolean isTradeTitle(int containerSize, String title) {
        return containerSize == 45 && title.startsWith("You");
    }

    public static boolean isTradeMenu(int containerSize, String title, boolean[] dividerGlassPanes) {
        if (!isTradeTitle(containerSize, title)) {
            return false;
        }
        for (boolean dividerGlassPane : dividerGlassPanes) {
            if (!dividerGlassPane) {
                return false;
            }
        }
        return true;
    }
}
