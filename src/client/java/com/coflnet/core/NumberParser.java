package com.coflnet.core;

import java.math.BigDecimal;
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
            long multiplier = 1;
            if (last == 'k') { multiplier = 1_000; in = in.substring(0, in.length() - 1); }
            else if (last == 'm') { multiplier = 1_000_000; in = in.substring(0, in.length() - 1); }
            else if (last == 'b') { multiplier = 1_000_000_000; in = in.substring(0, in.length() - 1); }
            return scale(in, multiplier);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static long parseAmount(String amount) {
        if (amount == null || amount.trim().isEmpty()) throw new NumberFormatException("Empty amount string");
        String input = amount.trim().toLowerCase(Locale.ROOT);
        if (input.matches("^[0-9]+$")) return Long.parseLong(input);
        if (input.matches("^[0-9]+\\.[0-9]+$")) return scale(input, 1);
        if (input.matches("^[0-9]+\\.?[0-9]*[kmb]$")) {
            char suffix = input.charAt(input.length() - 1);
            String numberPart = input.substring(0, input.length() - 1);
            return switch (suffix) {
                case 'k' -> scale(numberPart, 1_000);
                case 'm' -> scale(numberPart, 1_000_000);
                case 'b' -> scale(numberPart, 1_000_000_000);
                default -> throw new NumberFormatException("Invalid suffix: " + suffix);
            };
        }
        throw new NumberFormatException("Invalid format: " + amount + ". Use formats like: 1000, 2k, 3m, 1.5b");
    }

    /**
     * Multiplies a decimal string in base ten, so "8.2" times a million is 8200000 and not the
     * 8199999 binary floating point yields. Fractions of a coin are dropped and the result
     * saturates at the long range.
     */
    public static long scale(String number, long multiplier) {
        BigDecimal scaled = new BigDecimal(number).multiply(BigDecimal.valueOf(multiplier));
        if (scaled.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) >= 0) return Long.MAX_VALUE;
        if (scaled.compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) <= 0) return Long.MIN_VALUE;
        return scaled.longValue();
    }
}
