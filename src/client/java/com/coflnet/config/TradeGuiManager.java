package com.coflnet.config;

/**
 * Manages the trade overlay toggle. When enabled, the SkyCofl TradeGUI replaces
 * the Hypixel trade window. Independent of {@link DevManager} (dev mode only
 * controls the Copy Dump diagnostic button).
 */
public class TradeGuiManager {
    public static void reloadConfig() {
        CoflModConfig.reload();
    }

    public static CoflModConfig getConfig() {
        return CoflModConfig.get();
    }

    public static boolean isEnabled() {
        return getConfig().tradeGuiEnabled;
    }

    public static void setEnabled(boolean enabled) {
        CoflModConfig cfg = getConfig();
        cfg.tradeGuiEnabled = enabled;
        cfg.save();
    }

    /** Persisted TradeGUI item-list column count, clamped to 1-3. */
    public static int getListColumns() {
        int c = getConfig().tradeListColumns;
        return (c < 1 || c > 3) ? 1 : c;
    }

    public static void setListColumns(int columns) {
        CoflModConfig cfg = getConfig();
        cfg.tradeListColumns = (columns < 1 || columns > 3) ? 1 : columns;
        cfg.save();
    }
}
