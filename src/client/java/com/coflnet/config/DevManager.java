package com.coflnet.config;

/**
 * Utility class for managing developer mode configuration.
 * When dev mode is on, container screens render a "Copy Dump" button that
 * copies the open container's signature (title, size, slots) to the clipboard.
 */
public class DevManager {
    private static CoflModConfig config = null;

    private static void ensureConfig() {
        if (config == null) {
            config = CoflModConfig.load();
        }
    }

    public static void reloadConfig() {
        config = CoflModConfig.load();
    }

    public static CoflModConfig getConfig() {
        ensureConfig();
        return config;
    }

    public static boolean isEnabled() {
        return getConfig().devMode;
    }

    public static void setEnabled(boolean enabled) {
        CoflModConfig cfg = getConfig();
        cfg.devMode = enabled;
        cfg.save();
        reloadConfig();
    }
}
