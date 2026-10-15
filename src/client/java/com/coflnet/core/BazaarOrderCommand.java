package com.coflnet.core;

import java.util.Arrays;

/** Parser for {@code /cofl bzorder [search] <amount> <item name...>}. */
public final class BazaarOrderCommand {
    public static final String USAGE = "Usage: /cofl bzorder [search] <amount> <item name>";

    private BazaarOrderCommand() {
    }

    public record Parsed(boolean search, long amount, String itemName) {
    }

    /** @param argsAfterCofl the arguments after {@code /cofl bzorder} */
    public static Parsed parse(String[] argsAfterCofl) {
        if (argsAfterCofl == null) throw new IllegalArgumentException(USAGE);
        int i = 0;
        boolean search = false;
        if (argsAfterCofl.length > 0 && argsAfterCofl[0].equalsIgnoreCase("search")) {
            search = true;
            i = 1;
        }
        if (i >= argsAfterCofl.length) throw new IllegalArgumentException(USAGE);
        long amount;
        try {
            amount = NumberParser.parseAmount(argsAfterCofl[i]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(USAGE);
        }
        if (amount <= 0) throw new IllegalArgumentException(USAGE);
        String name = String.join(" ", Arrays.copyOfRange(argsAfterCofl, i + 1, argsAfterCofl.length)).trim();
        if (name.isEmpty()) throw new IllegalArgumentException(USAGE);
        return new Parsed(search, amount, name);
    }

    /** True when an armed sign fill with this deadline (epoch ms, 0 = never expires) is stale. */
    public static boolean isExpired(long expiresAtMillis, long nowMillis) {
        return expiresAtMillis > 0 && nowMillis > expiresAtMillis;
    }
}
