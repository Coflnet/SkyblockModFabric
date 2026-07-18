package com.coflnet.config;

public final class FlipHudManager {
    private FlipHudManager() {
    }

    public static boolean isEnabled() {
        return CoflModConfig.get().flipHudEnabled;
    }

    public static void setEnabled(boolean enabled) {
        CoflModConfig config = CoflModConfig.get();
        config.flipHudEnabled = enabled;
        config.save();
    }

    public static int getX(int screenWidth, int hudWidth) {
        int configured = CoflModConfig.get().flipHudX;
        int value = configured < 0 ? screenWidth - hudWidth - 8 : configured;
        return clamp(value, 2, Math.max(2, screenWidth - hudWidth - 2));
    }

    public static int getY(int screenHeight, int hudHeight) {
        return clamp(CoflModConfig.get().flipHudY, 2, Math.max(2, screenHeight - hudHeight - 2));
    }

    public static void setPosition(int x, int y, boolean save) {
        CoflModConfig config = CoflModConfig.get();
        config.flipHudX = x;
        config.flipHudY = y;
        if (save) {
            config.save();
        }
    }

    public static void resetPosition() {
        CoflModConfig config = CoflModConfig.get();
        config.flipHudX = -1;
        config.flipHudY = 8;
        config.save();
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
