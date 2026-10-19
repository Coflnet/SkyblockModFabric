package com.coflnet.core;

import com.coflnet.core.BazaarOrderLore.Order;
import com.coflnet.core.BazaarOrderLore.Side;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks the next slot to outline while the player places a Bazaar order. A fresh order is only
 * guided where Hypixel's menu already tells the side (price and confirm menus); a relist that the
 * player started by clicking a suggested order is guided through claim, cancel, back to the product
 * and placing it again. An active goal step guiding the same menu always wins.
 */
public final class BazaarGuide {
    /** A relist nobody advances for this long is dropped. */
    public static final long IDLE_TIMEOUT_MS = 5 * 60_000;
    public static final String ORDER_OPTIONS = "Order options";
    public static final String AMOUNT_SIGN_LINE = "to order";
    private static final String AMOUNT_MENU = "How many do you want?";
    private static final String BUY_PRICE_MENU = "How much do you want to pay?";
    private static final String SELL_PRICE_MENU = "At what price are you selling?";
    private static final String CONFIRM_BUY = "Confirm Buy Order";
    private static final String CONFIRM_SELL = "Confirm Sell Offer";

    public enum Stage { NONE, CLAIM, CANCEL, PLACE }

    private static final Pattern REFUNDED_ITEMS = Pattern.compile("^\\[Bazaar\\] Cancelled! Refunded ([\\d,]+)x ");
    private static final Pattern REFUNDED_COINS = Pattern.compile("^\\[Bazaar\\] Cancelled! Refunded ([\\d,]+) coins ");

    private Stage stage = Stage.NONE;
    private Side side;
    private String itemName;
    private long priceTenths;
    /** The selected order's total amount; unlike its fill it stays the same when the order is claimed. */
    private long orderAmount;
    private long amount;
    private long lastProgressAt;

    /**
     * Whether a goal step guides the open menu: the backend highlights a slot in it, or a goal sign
     * fill is armed and not expired. Then every local mark, tooltip line and relist start is held back.
     */
    public static boolean goalGuidesMenu(boolean backendHighlightInMenu, String armedSignFill, long fillExpiresAt, long now) {
        boolean fillArmed = armedSignFill != null && !armedSignFill.isBlank()
                && !BazaarOrderCommand.isExpired(fillExpiresAt, now);
        return backendHighlightInMenu || fillArmed;
    }

    /** Menus the guide or the order marks can act on; every other menu is left alone. */
    public static boolean isRelevantMenu(String title) {
        if (title == null) return false;
        return MenuClassifier.isBazaarOrders(title) || ORDER_OPTIONS.equals(title) || title.contains("➜ ")
                || List.of(AMOUNT_MENU, BUY_PRICE_MENU, SELL_PRICE_MENU, CONFIRM_BUY, CONFIRM_SELL).contains(title);
    }

    /**
     * The player clicked an order in the orders menu; a suggested relist starts (claiming first when
     * something is claimable), any other order ends one. A goal step guiding the menu starts nothing.
     */
    public synchronized void orderClicked(Order order, BazaarFillEstimate.Estimate estimate, boolean goalGuidesMenu, long now) {
        if (goalGuidesMenu) return;
        if (order != null && estimate != null && estimate.suggestRelist()) {
            stage = order.claimable() ? Stage.CLAIM : Stage.CANCEL;
            side = order.side();
            itemName = order.itemName();
            priceTenths = order.priceTenths();
            orderAmount = order.amount();
            amount = order.remaining();
            lastProgressAt = now;
        } else if (stage == Stage.CLAIM || stage == Stage.CANCEL) {
            reset();
        }
    }

    /** Advances on Hypixel's claim, cancel and setup confirmations. */
    public synchronized void chat(String line, long now) {
        if (stage == Stage.NONE || line == null) return;
        String text = FormattingCodes.strip(line);
        if (stage == Stage.CLAIM && text.startsWith("[Bazaar] Claimed ")) {
            stage = Stage.CANCEL;
            lastProgressAt = now;
        } else if (stage == Stage.CANCEL && text.startsWith("[Bazaar] Cancelled! Refunded ")) {
            amount = refundedAmount(text);
            stage = Stage.PLACE;
            lastProgressAt = now;
        } else if (stage == Stage.PLACE && text.startsWith(side == Side.BUY ? "[Bazaar] Buy Order Setup!" : "[Bazaar] Sell Offer Setup!")
                && text.contains("x " + itemName + " for ")) {
            reset();
        }
    }

    /** The refund is exact where the order lore abbreviates its fill: items for an offer, coins / price for an order. */
    private long refundedAmount(String text) {
        Matcher items = REFUNDED_ITEMS.matcher(text);
        if (side == Side.SELL && items.find()) return Long.parseLong(items.group(1).replace(",", ""));
        Matcher coins = REFUNDED_COINS.matcher(text);
        if (side == Side.BUY && coins.find()) {
            long refunded = Math.round(Long.parseLong(coins.group(1).replace(",", "")) * 10.0 / priceTenths);
            if (refunded > 0) return refunded;
        }
        return amount;
    }

    public synchronized Stage stage(long now) {
        if (stage != Stage.NONE && now - lastProgressAt > IDLE_TIMEOUT_MS) reset();
        return stage;
    }

    /**
     * @param slotNames       hover names of every slot of the open menu, container slots first
     * @param slotOrders      the parsed order per slot (orders menu only), or null
     * @param containerSlots  how many slots belong to the container (the rest is the player inventory)
     * @param goalGuidesMenu  a goal step highlights a slot in this menu or has a sign fill armed
     * @return the slot to outline, or -1
     */
    public synchronized int nextSlot(String title, List<String> slotNames, Order[] slotOrders, int containerSlots,
                                     boolean goalGuidesMenu, long now) {
        if (goalGuidesMenu || title == null) return -1;
        return switch (stage(now)) {
            case NONE -> find(slotNames, 0, containerSlots, orderStep(title, null));
            case CLAIM, CANCEL -> cancelStep(title, slotNames, slotOrders, containerSlots);
            case PLACE -> placeStep(title, slotNames, containerSlots);
        };
    }

    /** The amount to type on the Custom Amount sign of a buy order relist, or null. */
    public synchronized String expectedSignValue(String fourthLine, long now) {
        if (stage(now) != Stage.PLACE || side != Side.BUY || !AMOUNT_SIGN_LINE.equals(fourthLine)) return null;
        return Long.toString(amount);
    }

    public synchronized void reset() {
        stage = Stage.NONE;
        side = null;
        itemName = null;
        priceTenths = 0;
        orderAmount = 0;
        amount = 0;
    }

    /**
     * Claiming and opening the options are both a click on the order itself; cancelling is in its
     * options. Two orders alike in side, item, price and amount cannot be told apart, so neither is outlined.
     */
    private int cancelStep(String title, List<String> slotNames, Order[] slotOrders, int containerSlots) {
        if (ORDER_OPTIONS.equals(title)) return stage == Stage.CANCEL ? find(slotNames, 0, containerSlots, "Cancel Order") : -1;
        if (!MenuClassifier.isBazaarOrders(title) || slotOrders == null) return -1;
        int match = -1;
        for (int i = 0; i < slotOrders.length; i++) {
            if (!isSelected(slotOrders[i])) continue;
            if (match >= 0) return -1;
            match = i;
        }
        return match;
    }

    private boolean isSelected(Order order) {
        return order != null && order.side() == side && order.priceTenths() == priceTenths
                && order.amount() == orderAmount && order.itemName().equals(itemName);
    }

    private int placeStep(String title, List<String> slotNames, int containerSlots) {
        int create = find(slotNames, 0, containerSlots, side == Side.BUY ? "Create Buy Order" : "Create Sell Offer");
        if (create >= 0) return isProductPage(title, itemName) ? create : -1;
        if (AMOUNT_MENU.equals(title)) return side == Side.BUY ? find(slotNames, 0, containerSlots, "Custom Amount") : -1;
        String step = orderStep(title, side);
        if (step != null) return find(slotNames, 0, containerSlots, step);
        if (side == Side.SELL && (MenuClassifier.isBazaarOrders(title) || title.startsWith("Bazaar ➜ "))) {
            // Clicking the refunded stack in the inventory opens its product page.
            return find(slotNames, containerSlots, slotNames.size(), itemName);
        }
        if (MenuClassifier.isBazaarOrders(title)) return find(slotNames, 0, containerSlots, "Go Back");
        if (title.startsWith("Bazaar ➜ ")) {
            // A category page or search result listing the product, else the search.
            int product = find(slotNames, 0, containerSlots, itemName);
            return product >= 0 ? product : find(slotNames, 0, containerSlots, "Search");
        }
        return -1;
    }

    /** Price and confirm menus: their titles already tell the side. */
    private static String orderStep(String title, Side expectedSide) {
        Side menuSide;
        String button;
        switch (title) {
            case BUY_PRICE_MENU -> { menuSide = Side.BUY; button = "Top Order +0.1"; }
            case CONFIRM_BUY -> { menuSide = Side.BUY; button = "Buy Order"; }
            case SELL_PRICE_MENU -> { menuSide = Side.SELL; button = "Best Offer -0.1"; }
            case CONFIRM_SELL -> { menuSide = Side.SELL; button = "Sell Offer"; }
            default -> { return null; }
        }
        return expectedSide == null || expectedSide == menuSide ? button : null;
    }

    /** Product page titles read {@code <category> ➜ <item>} and cut long item names short. */
    static boolean isProductPage(String title, String itemName) {
        int arrow = title.indexOf("➜ ");
        if (arrow < 0 || itemName == null) return false;
        String shown = title.substring(arrow + 2).trim();
        return !shown.isEmpty() && !shown.startsWith("\"") && itemName.startsWith(shown);
    }

    private static int find(List<String> slotNames, int from, int to, String name) {
        if (name == null) return -1;
        for (int i = from; i < Math.min(to, slotNames.size()); i++) {
            String slot = slotNames.get(i);
            if (slot != null && FormattingCodes.strip(slot).trim().equals(name)) return i;
        }
        return -1;
    }
}
