package com.coflnet.lore;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record PurchaseMessage(String itemName, long coins) {
    private static final Pattern PATTERN = Pattern.compile(
            "^You (?:purchased|bought|claimed) (?:\\d+x\\s+)?(.{1,128}?)(?: from .{1,64}?)? for ([0-9][0-9,]{0,20}) coins[.!]?$");

    public static PurchaseMessage parse(String message) {
        if (message == null || message.length() > 256) {
            return null;
        }
        Matcher match = PATTERN.matcher(message.trim());
        if (!match.matches()) {
            return null;
        }
        try {
            long coins = Long.parseLong(match.group(2).replace(",", ""));
            String itemName = match.group(1).trim();
            return coins > 0 && !itemName.isBlank()
                    ? new PurchaseMessage(itemName, coins)
                    : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
