package com.coflnet.core;

import com.coflnet.core.BazaarOrderLore.Order;
import com.coflnet.core.BazaarOrderLore.Side;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether an own Bazaar order is outbid, how many items are queued ahead of it and roughly how long
 * it needs to fill. The queue ahead is everything at a better price plus the rest of its own price
 * level, which may have been placed earlier; the time is that queue plus the order's own remaining
 * amount divided by the hourly volume filling that side.
 */
public final class BazaarFillEstimate {
    private BazaarFillEstimate() {}

    /** Outbid orders that need longer than this get a relist suggestion. */
    public static final double RELIST_THRESHOLD_HOURS = 1.0;
    /** One Bazaar price step, in tenths of a coin. */
    public static final long PRICE_STEP_TENTHS = 1;

    /** @param hoursToFill {@link Double#NaN} when no volume is known */
    public record Estimate(boolean outbid, long itemsAhead, long priceGapTenths, long relistPriceTenths,
                           double hoursToFill, boolean suggestRelist) {}

    /** @return null when there is nothing to estimate (no book, or nothing left to fill) */
    public static Estimate estimate(Order order, BazaarBook book) {
        if (order == null || book == null || order.remaining() <= 0) return null;
        List<BazaarBook.Level> levels = book.levels(order.side());
        long better = 0, samePrice = 0;
        for (BazaarBook.Level level : levels) {
            if (isBetter(order.side(), level.priceTenths(), order.priceTenths())) better += level.amount();
            else if (level.priceTenths() == order.priceTenths()) samePrice += level.amount();
        }
        long ahead = better + Math.max(0, samePrice - order.remaining());
        boolean outbid = better > 0;
        long best = levels.isEmpty() ? order.priceTenths() : levels.get(0).priceTenths();
        long gap = outbid ? Math.abs(best - order.priceTenths()) : 0;
        long relistPrice = order.side() == Side.BUY ? best + PRICE_STEP_TENTHS : best - PRICE_STEP_TENTHS;
        double flow = book.fillsPerHour(order.side());
        double hours = flow > 0 ? (ahead + order.remaining()) / flow : Double.NaN;
        boolean relist = outbid && hours > RELIST_THRESHOLD_HOURS && relistPrice > 0;
        return new Estimate(outbid, ahead, gap, relistPrice, hours, relist);
    }

    private static boolean isBetter(Side side, long levelPrice, long ownPrice) {
        return side == Side.BUY ? levelPrice > ownPrice : levelPrice < ownPrice;
    }

    /** Tooltip lines for an order; none while it holds the best price. */
    public static List<String> tooltipLines(Order order, Estimate estimate) {
        List<String> lines = new ArrayList<>();
        if (estimate == null || !estimate.outbid()) return lines;
        lines.add("§cOutbid: " + SignGuidance.formatAmount(Long.toString(estimate.itemsAhead())) + " items ahead, "
                + BazaarOrderLore.formatTenths(estimate.priceGapTenths()) + " coins");
        if (!Double.isNaN(estimate.hoursToFill())) {
            lines.add("§7~" + formatDuration(estimate.hoursToFill()) + " to fill at this price");
        }
        if (estimate.suggestRelist()) {
            String steps = order.claimable() ? "claim, cancel and relist" : "cancel and relist";
            lines.add("§eSuggested: " + steps + " at " + BazaarOrderLore.formatTenths(estimate.relistPriceTenths()));
        }
        return lines;
    }

    /** {@code 0.5} becomes {@code 30m}, {@code 3.2} becomes {@code 3h}, {@code 72} becomes {@code 3d}. */
    public static String formatDuration(double hours) {
        if (hours < 1) return Math.max(1, Math.round(hours * 60)) + "m";
        if (hours < 48) return Math.round(hours) + "h";
        return Math.round(hours / 24) + "d";
    }
}
