package com.coflnet.config;

/**
 * Utility class for managing the Angry Co-op protection configuration.
 */
public class AngryCoopProtectionManager {
    public static void reloadConfig() {
        CoflModConfig.reload();
    }

    public static CoflModConfig getConfig() {
        return CoflModConfig.get();
    }

    public static boolean isEnabled() {
        return getConfig().angryCoopProtectionEnabled;
    }

    public static void setEnabled(boolean enabled) {
        CoflModConfig cfg = getConfig();
        cfg.angryCoopProtectionEnabled = enabled;
        cfg.save();
    }
}
