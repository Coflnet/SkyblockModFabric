package com.coflnet.lore;

import net.minecraft.ChatFormatting;

import java.util.Locale;

/**
 * classifies a single backend lore line into one of the known line classes the
 * client engine can restyle. any line that does not match a known class is left
 * for passthrough so backend fields the engine does not understand the many
 * optional cofl lore fields are never altered or dropped.
 */
public final class LoreLineClass {
    public static final String LBIN = "lbin";
    public static final String MEDIAN = "median";
    public static final String BAZAAR = "bazaar";
    public static final String BAZAAR_VOL = "bazaarvol";
    public static final String CRAFT = "craft";
    public static final String MODIFIER = "modifier";
    public static final String OBTAIN = "obtain";

    private LoreLineClass() {
    }

    /** returns the class key for one backend line value or null if unknown. */
    public static String classify(String backendValue) {
        if (backendValue == null) {
            return null;
        }
        String lower = ChatFormatting.stripFormatting(backendValue).toLowerCase(Locale.ROOT);
        if (lower.contains("buy:") && lower.contains("sell:")) {
            return BAZAAR;
        }
        if (lower.contains("vol:") && lower.contains("/") && !lower.contains("med:")) {
            return BAZAAR_VOL;
        }
        if (lower.contains("lbin:")) {
            return LBIN;
        }
        if (lower.contains("med:")) {
            return MEDIAN;
        }
        if (lower.contains("full craft cost:")) {
            return CRAFT;
        }
        if (lower.contains("modifier cost:")) {
            return MODIFIER;
        }
        if (lower.contains("obtain cost:")) {
            return OBTAIN;
        }
        return null;
    }
}
