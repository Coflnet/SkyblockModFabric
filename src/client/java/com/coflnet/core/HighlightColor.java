package com.coflnet.core;

import java.util.OptionalInt;

/** Parses highlight colours from the backend; malformed values yield empty instead of throwing. */
public final class HighlightColor {
    private HighlightColor() {}

    /** @return the colour with the alpha byte forced to 0xFF */
    public static OptionalInt parse(String value) {
        if (value == null) return OptionalInt.empty();
        String hex = value.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.isEmpty() || hex.length() > 8) return OptionalInt.empty();
        try {
            return OptionalInt.of((int) Long.parseLong(hex, 16) | 0xFF000000);
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }
}
