package com.coflnet.protection.auction;

import com.coflnet.core.MenuClassifier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Requires Ctrl to create a BIN auction whose price is still the one Hypixel filled in.
 *
 * <p>Hypixel pre-fills a low default price (10 to 5,000 coins) for the item in the
 * "Create BIN Auction" menu. The price first shown for a listing is remembered; while it is
 * unchanged, the "Create BIN Auction" button only works with Ctrl held.</p>
 *
 * <p>Editing the price or the duration replaces the menu with a sign or a sub-menu and then reopens
 * it, so after a click on the price or duration button the remembered price survives the menu's
 * removal and the removal of one sub-menu. Closing a menu with Escape, submitting, removing the
 * listed item, the menu going away after any other click, an auction house or create auction menu
 * going away, and any further menu going away end the listing, so the next listing records its own
 * default.</p>
 *
 * <p>Kept outside {@code com.coflnet.mixin}: classes in that package are owned by Sponge Mixin.</p>
 */
public final class DefaultBinPriceGuard {
    public static final long MIN_GUARDED_PRICE = 10;
    public static final long MAX_GUARDED_PRICE = 5000;
    static final String MENU_TITLE = "Create BIN Auction";
    static final int LISTED_ITEM_SLOT = 13;
    private static final Pattern PRICE_LINE = Pattern.compile("^(?:item price|starting bid):\\s*([\\d,]+)", Pattern.CASE_INSENSITIVE);
    private static final DefaultBinPriceGuard SHARED = new DefaultBinPriceGuard();

    private String listedItem;
    private long defaultPrice = -1;
    /** The price or duration button was clicked; the server will replace the menu and reopen it. */
    private boolean buttonClicked;
    /** The menu was replaced after a button click; one sub-menu may close before it returns. */
    private boolean awaitingReturn;
    private boolean subMenuClosed;

    public static DefaultBinPriceGuard shared() {
        return SHARED;
    }

    /** Decides whether a click on {@code clicked} in a container titled {@code title} has to be cancelled. */
    public boolean blocksClick(String title, AbstractContainerMenu menu, Slot clicked, boolean ctrlDown) {
        Container container = isCreateBinMenu(title) && menu instanceof ChestMenu chest ? chest.getContainer() : null;
        if (container == null) {
            return false;
        }
        ItemStack listed = container.getContainerSize() > LISTED_ITEM_SLOT ? container.getItem(LISTED_ITEM_SLOT) : ItemStack.EMPTY;
        if (listed.isEmpty()) {
            reset();
            return false;
        }
        long shownPrice = findShownPrice(container);
        observe(nameOf(listed), shownPrice);
        if (clicked == null || clicked.container != container || !clicked.hasItem()) {
            return false;
        }
        if (!isSubmitButton(clicked.getItem())) {
            buttonClicked = isEditButton(clicked.getItem()); // close, back, decoration and items do not continue the listing
            return false;
        }
        if (isUntouchedDefault(shownPrice) && !ctrlDown) {
            return true;
        }
        reset();
        return false;
    }

    /** The player closed a container screen themselves, abandoning any listing. */
    public void onMenuClosed() {
        reset();
    }

    /** A container screen went away, whether the player closed it or the server replaced it. */
    public void onMenuRemoved(String title) {
        if (isCreateBinMenu(title) && buttonClicked) {
            buttonClicked = false;
            awaitingReturn = true;
            subMenuClosed = false;
        } else if (!isCreateBinMenu(title) && awaitingReturn && !subMenuClosed && !isAuctionNavigation(title)) {
            subMenuClosed = true; // the duration sub-menu returning to the listing
        } else {
            reset();
        }
    }

    public String blockedMessage() {
        return "§c[SkyCofl] §fBlocked creating this BIN auction at the default price of §6"
                + String.format(Locale.US, "%,d", defaultPrice)
                + " coins§f. Change the price or hold §bCtrl§f to confirm.";
    }

    private void observe(String item, long shownPrice) {
        if (!item.equals(listedItem)) {
            listedItem = item;
            defaultPrice = shownPrice;
        }
        buttonClicked = false;
        awaitingReturn = false;
        subMenuClosed = false;
    }

    private boolean isUntouchedDefault(long shownPrice) {
        return defaultPrice >= MIN_GUARDED_PRICE && defaultPrice <= MAX_GUARDED_PRICE && shownPrice == defaultPrice;
    }

    private void reset() {
        listedItem = null;
        defaultPrice = -1;
        buttonClicked = false;
        awaitingReturn = false;
        subMenuClosed = false;
    }

    private static boolean isCreateBinMenu(String title) {
        return MENU_TITLE.equalsIgnoreCase(stripFormatting(title).trim());
    }

    /** Menus the player passes through on the way to a new listing rather than back to the current one. */
    private static boolean isAuctionNavigation(String title) {
        String plain = stripFormatting(title).trim();
        return plain.toLowerCase(Locale.ROOT).contains("auction house") || MenuClassifier.isCreateAuction(plain);
    }

    /** The price and duration buttons, whose sign or sub-menu returns to the same listing. */
    private static boolean isEditButton(ItemStack stack) {
        String name = nameOf(stack).toLowerCase(Locale.ROOT);
        return name.contains("price") || name.contains("bid") || name.contains("duration");
    }

    private static boolean isSubmitButton(ItemStack stack) {
        String name = nameOf(stack).toLowerCase(Locale.ROOT);
        return name.startsWith("create") && name.contains("auction");
    }

    /** The price Hypixel shows on the menu's buttons, or -1 when no button carries one. */
    private static long findShownPrice(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (i == LISTED_ITEM_SLOT) {
                continue;
            }
            ItemLore lore = container.getItem(i).get(DataComponents.LORE);
            if (lore == null) {
                continue;
            }
            for (Component line : lore.lines()) {
                Matcher matcher = PRICE_LINE.matcher(stripFormatting(line.getString()).trim());
                if (matcher.find()) {
                    return Long.parseLong(matcher.group(1).replace(",", ""));
                }
            }
        }
        return -1;
    }

    private static String nameOf(ItemStack stack) {
        return stripFormatting(stack.getHoverName().getString()).trim();
    }

    private static String stripFormatting(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("§.", "");
    }
}
