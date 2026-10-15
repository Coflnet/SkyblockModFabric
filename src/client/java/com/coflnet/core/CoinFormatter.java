package com.coflnet.core;

import java.util.Locale;

public final class CoinFormatter {
    private CoinFormatter() {
    }

    public static String format(long coins) {
        if (coins >= 1_000_000_000) return String.format(Locale.US, "%.1fB", coins / 1_000_000_000.0);
        if (coins >= 1_000_000) return String.format(Locale.US, "%.1fM", coins / 1_000_000.0);
        if (coins >= 1_000) return String.format(Locale.US, "%.1fK", coins / 1_000.0);
        return String.valueOf(coins);
    }
}
