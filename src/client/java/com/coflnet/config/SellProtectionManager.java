package com.coflnet.config;

/**
 * Utility class for managing sell protection configuration
 */
public class SellProtectionManager {
    public static void reloadConfig() {
        CoflModConfig.reload();
    }

    public static CoflModConfig getConfig() {
        return CoflModConfig.get();
    }

    public static boolean isEnabled() {
        return getConfig().sellProtectionEnabled;
    }

    public static long getMaxAmount() {
        return getConfig().sellProtectionThreshold;
    }

    public static void setEnabled(boolean enabled) {
        CoflModConfig cfg = getConfig();
        cfg.sellProtectionEnabled = enabled;
        cfg.save();
    }

    public static void setMaxAmount(long amount) {
        CoflModConfig cfg = getConfig();
        cfg.sellProtectionThreshold = amount;
        cfg.save();
    }
}
