package com.coflnet.config;

/**
 * Utility class for the default BIN price protection configuration.
 */
public class DefaultBinPriceProtectionManager {
    public static boolean isEnabled() {
        return CoflModConfig.get().defaultBinPriceProtectionEnabled;
    }

    public static void setEnabled(boolean enabled) {
        CoflModConfig cfg = CoflModConfig.get();
        cfg.defaultBinPriceProtectionEnabled = enabled;
        cfg.save();
    }
}
