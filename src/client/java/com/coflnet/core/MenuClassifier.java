package com.coflnet.core;

public final class MenuClassifier {
    private MenuClassifier() {
    }

    public static boolean isBazaarOrders(String title) {
        return "Your Bazaar Orders".equals(title) || "Co-op Bazaar Orders".equals(title);
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
