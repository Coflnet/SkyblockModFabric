package com.coflnet.core;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses an order slot of {@code Your Bazaar Orders} / {@code Co-op Bazaar Orders}; prices are in tenths of a coin. */
public final class BazaarOrderLore {
    private BazaarOrderLore() {}

    public enum Side { BUY, SELL }

    public record Order(Side side, String itemName, long priceTenths, long amount, long filled, boolean claimable) {
        public long remaining() {
            return Math.max(0, amount - filled);
        }
    }

    private static final Pattern AMOUNT = Pattern.compile("^(?:Order|Offer) amount: ([\\d,]+)x");
    /** Large orders abbreviate the fill ({@code Filled: 29k/30k (96.7%)}). */
    private static final Pattern FILLED = Pattern.compile("^Filled: ([\\d,.]+[kKmMbB]?)/");
    private static final Pattern PRICE = Pattern.compile("^Price per unit: ([\\d,]+(?:\\.\\d)?) coins");
    private static final Pattern CLAIM = Pattern.compile("^You have [\\d,]+ (?:items|coins) to claim!");

    /** @return the order, or null when the slot is not an order with a price and an amount */
    public static Order parse(String name, List<String> loreLines) {
        String title = name == null ? "" : FormattingCodes.strip(name).trim();
        Side side;
        if (title.startsWith("BUY ")) side = Side.BUY;
        else if (title.startsWith("SELL ")) side = Side.SELL;
        else return null;
        String itemName = title.substring(side == Side.BUY ? 4 : 5).trim();
        long price = -1, amount = -1, filled = 0;
        boolean claimable = false;
        for (String raw : loreLines) {
            String line = FormattingCodes.strip(raw).trim();
            Matcher m;
            if ((m = AMOUNT.matcher(line)).find()) amount = digits(m.group(1));
            else if ((m = FILLED.matcher(line)).find()) filled = filledLowerBound(m.group(1));
            else if ((m = PRICE.matcher(line)).find()) price = parseTenths(m.group(1));
            else if (CLAIM.matcher(line).find()) claimable = true;
        }
        if (price <= 0 || amount <= 0 || itemName.isEmpty()) return null;
        return new Order(side, itemName, price, amount, Math.min(filled, amount), claimable);
    }

    /** {@code 1,649,533.1} becomes {@code 16495331}. */
    public static long parseTenths(String price) {
        String plain = price.replace(",", "");
        int dot = plain.indexOf('.');
        if (dot < 0) return Long.parseLong(plain) * 10;
        String fraction = (plain.substring(dot + 1) + "0").substring(0, 1);
        return Long.parseLong(plain.substring(0, dot)) * 10 + Long.parseLong(fraction);
    }

    /** {@code 16495331} becomes {@code 1,649,533.1}; whole prices drop the decimal. */
    public static String formatTenths(long tenths) {
        String whole = SignGuidance.formatAmount(Long.toString(tenths / 10));
        return tenths % 10 == 0 ? whole : whole + "." + tenths % 10;
    }

    /**
     * {@code 29k} reads as 29,000: an abbreviation never claims more was filled than shown, so the
     * remaining amount is not understated. A relist takes its exact amount from the refund line.
     */
    private static long filledLowerBound(String value) {
        Long parsed = NumberParser.parseCoinNumber(value);
        return parsed == null ? 0 : parsed;
    }

    private static long digits(String value) {
        return Long.parseLong(value.replace(",", ""));
    }
}
