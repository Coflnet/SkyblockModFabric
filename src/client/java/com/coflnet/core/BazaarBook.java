package com.coflnet.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The order book of one product from {@code /api/bazaar/{TAG}/snapshot}. The snapshot names its lists
 * from the insta-buyer's side ({@code buyOrders} are the sell offers an insta-buy fills), so the sides
 * are chosen by price: buy orders always sit below sell offers. Each moving-week volume stays with the
 * list of the same name.
 */
public final class BazaarBook {
    public static final double HOURS_PER_WEEK = 168;
    private static final String BAZAAR_SUFFIX = " - bazaar";

    public record Level(long priceTenths, long amount) {}

    /** Buy order levels best (highest) first and sell offer levels best (lowest) first. */
    private final List<Level> buyOrders;
    private final List<Level> sellOffers;
    private final double buyOrderFillsPerHour;
    private final double sellOfferFillsPerHour;

    public BazaarBook(List<Level> buyOrders, List<Level> sellOffers, double buyOrderFillsPerHour, double sellOfferFillsPerHour) {
        this.buyOrders = sorted(buyOrders, Comparator.comparingLong(Level::priceTenths).reversed());
        this.sellOffers = sorted(sellOffers, Comparator.comparingLong(Level::priceTenths));
        this.buyOrderFillsPerHour = buyOrderFillsPerHour;
        this.sellOfferFillsPerHour = sellOfferFillsPerHour;
    }

    public List<Level> levels(BazaarOrderLore.Side side) {
        return side == BazaarOrderLore.Side.BUY ? buyOrders : sellOffers;
    }

    /** Items per hour that fill orders of this side (insta-sells fill buy orders, insta-buys sell offers). */
    public double fillsPerHour(BazaarOrderLore.Side side) {
        return side == BazaarOrderLore.Side.BUY ? buyOrderFillsPerHour : sellOfferFillsPerHour;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BazaarBook book && buyOrders.equals(book.buyOrders) && sellOffers.equals(book.sellOffers)
                && buyOrderFillsPerHour == book.buyOrderFillsPerHour && sellOfferFillsPerHour == book.sellOfferFillsPerHour;
    }

    @Override
    public int hashCode() {
        return Objects.hash(buyOrders, sellOffers, buyOrderFillsPerHour, sellOfferFillsPerHour);
    }

    /** @return the book, or null for a malformed or empty response */
    public static BazaarBook parse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            List<Level> named = levels(root.get("buyOrders"));
            List<Level> other = levels(root.get("sellOrders"));
            double namedVolume = number(root.get("buyMovingWeek")) / HOURS_PER_WEEK;
            double otherVolume = number(root.get("sellMovingWeek")) / HOURS_PER_WEEK;
            if (named.isEmpty() && other.isEmpty()) return null;
            if (holdsSellOffers(named, other)) return new BazaarBook(other, named, otherVolume, namedVolume);
            return new BazaarBook(named, other, namedVolume, otherVolume);
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    /** Whether the snapshot's {@code buyOrders} list is priced above {@code sellOrders}, i.e. holds the sell offers. */
    static boolean holdsSellOffers(List<Level> named, List<Level> other) {
        if (named.isEmpty() || other.isEmpty()) return true; // one side only: trust the documented naming
        long namedLow = named.stream().mapToLong(Level::priceTenths).min().getAsLong();
        long otherHigh = other.stream().mapToLong(Level::priceTenths).max().getAsLong();
        return namedLow >= otherHigh;
    }

    /**
     * Picks the Bazaar tag from {@code /api/item/search/{name}}: only the entry named exactly
     * {@code <name> - bazaar} counts, so an unrelated or differently named result leaves the item unknown.
     *
     * @return the tag, or null when no entry matches
     */
    public static String tagFromSearch(String json, String itemName) {
        try {
            for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
                JsonObject entry = element.getAsJsonObject();
                if (entry.has("id") && entry.get("name").getAsString().equalsIgnoreCase(itemName + BAZAAR_SUFFIX)) {
                    return entry.get("id").getAsString();
                }
            }
            return null;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static List<Level> levels(JsonElement element) {
        List<Level> result = new ArrayList<>();
        if (element == null || !element.isJsonArray()) return result;
        JsonArray array = element.getAsJsonArray();
        for (JsonElement entry : array) {
            JsonObject level = entry.getAsJsonObject();
            long price = Math.round(number(level.get("pricePerUnit")) * 10);
            long amount = (long) number(level.get("amount"));
            if (price > 0 && amount > 0) result.add(new Level(price, amount));
        }
        return result;
    }

    private static double number(JsonElement element) {
        return element == null || element.isJsonNull() ? 0 : element.getAsDouble();
    }

    private static List<Level> sorted(List<Level> levels, Comparator<Level> order) {
        List<Level> copy = new ArrayList<>(levels);
        copy.sort(order);
        return List.copyOf(copy);
    }
}
