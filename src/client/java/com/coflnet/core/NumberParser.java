package com.coflnet.core;

import java.util.Locale;

public final class NumberParser {
    private NumberParser() {
    }

    public static Long parseCoinNumber(String token) {
        if (token == null) return null;
        String in = token.toLowerCase(Locale.ROOT).replace(",", "").replace(" ", "").trim();
        if (in.isEmpty()) return null;
        try {
            char last = in.charAt(in.length() - 1);
            double multiplier = 1.0;
            if (last == 'k') { multiplier = 1_000.0; in = in.substring(0, in.length() - 1); }
            else if (last == 'm') { multiplier = 1_000_000.0; in = in.substring(0, in.length() - 1); }
            else if (last == 'b') { multiplier = 1_000_000_000.0; in = in.substring(0, in.length() - 1); }
            return (long) (Double.parseDouble(in) * multiplier);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static long parseAmount(String amount) {
        if (amount == null || amount.trim().isEmpty()) throw new NumberFormatException("Empty amount string");
        String input = amount.trim().toLowerCase(Locale.ROOT);
        if (input.matches("^[0-9]+$")) return Long.parseLong(input);
        if (input.matches("^[0-9]+\\.[0-9]+$")) return (long) Double.parseDouble(input);
        if (input.matches("^[0-9]+\\.?[0-9]*[kmb]$")) {
            char suffix = input.charAt(input.length() - 1);
            String numberPart = input.substring(0, input.length() - 1);
            double value;
            try { value = Double.parseDouble(numberPart); }
            catch (NumberFormatException exception) { throw new NumberFormatException("Invalid number part: " + numberPart); }
            return switch (suffix) {
                case 'k' -> (long) (value * 1_000);
                case 'm' -> (long) (value * 1_000_000);
                case 'b' -> (long) (value * 1_000_000_000);
                default -> throw new NumberFormatException("Invalid suffix: " + suffix);
            };
        }
        throw new NumberFormatException("Invalid format: " + amount + ". Use formats like: 1000, 2k, 3m, 1.5b");
    }
}
