package com.coflnet.core;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Bazaar search identity, including single-enchantment books and contextual menu items. */
public record BazaarGraphTarget(String id, String query) {
    private static final Set<String> CONTROLS = Set.of("View Graphs", "Graphs", "Go Back", "Close",
            "Buy Instantly", "Sell Instantly", "Create Buy Order", "Create Sell Offer", "Manage Orders");

    public static BazaarGraphTarget from(String id, String display, Map<String, Integer> enchantments) {
        String name = BazaarChart.clean(display).replaceFirst("(?i)^(?:BUY|SELL)\\s+", "").trim();
        id = id == null ? "" : id;
        if (id.equals("UNKNOWN_MAP")) id = "";
        if (id.equals("ENCHANTED_BOOK") && enchantments.isEmpty() && !name.equals("Enchanted Book")) id = "";
        if (id.equals("ENCHANTED_BOOK") || name.equals("Enchanted Book")) {
            if (enchantments.size() != 1) return null;
            var enchant = enchantments.entrySet().iterator().next();
            int level = enchant.getValue();
            if (level < 1 || level > 50) return null;
            String key = enchant.getKey().toUpperCase(Locale.ROOT);
            id = "ENCHANTMENT_" + key + "_" + level;
            if (key.startsWith("ULTIMATE_") && !key.equals("ULTIMATE_WISE")) key = key.substring(9);
            var words = new StringBuilder();
            for (String word : key.toLowerCase(Locale.ROOT).split("_")) {
                if (!words.isEmpty()) words.append(' ');
                words.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            name = words + " " + roman(level);
        }
        if (name.isBlank() || name.length() > 100 || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0
                || name.startsWith("/") || CONTROLS.contains(name) || BazaarChart.isChart(name)) return null;
        return new BazaarGraphTarget(id, name);
    }

    public boolean matches(BazaarGraphTarget candidate) {
        if (candidate == null) return false;
        if (!id.isEmpty() && !candidate.id.isEmpty()) return id.equals(candidate.id);
        return query.equalsIgnoreCase(candidate.query);
    }

    private static String roman(int number) {
        StringBuilder result = new StringBuilder();
        int[] values = {50, 40, 10, 9, 5, 4, 1};
        String[] digits = {"L", "XL", "X", "IX", "V", "IV", "I"};
        for (int i = 0; i < values.length; i++) while (number >= values[i]) {
            result.append(digits[i]); number -= values[i];
        }
        return result.toString();
    }
}
