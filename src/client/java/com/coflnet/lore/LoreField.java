package com.coflnet.lore;

import java.util.List;

/**
 * catalog of the lore fields the coflnet backend can append to an item as
 * exposed by the {@code /cofl lore} chat menu. Each entry pairs the backend
 * field KEY (the exact token the {@code /cofl lore add|rm|up|down} commands
 * expect with a human display name and a short description for the gui.
 *  
 * the backend owns which fields are appended and on which line this catalog is
 * only the menu of choices the gui offers. adding a field here that the backend
 * does not recognise simply means the backend ignores the add command so the
 * list mirrors the 27 fields observed in a real {@code /cofl lore} dump.
 */
public final class LoreField {
    public final String key;
    public final String display;
    public final String description;

    private LoreField(String key, String display, String description) {
        this.key = key;
        this.display = display;
        this.description = description;
    }

    /** the full backend field catalog in the menu order from the dump. */
    public static final List<LoreField> ALL = List.of(
            new LoreField("LBIN", "Lowest BIN", "Lowest active bin price for the item."),
            new LoreField("MEDIAN", "Median", "Median sell price from recent sales."),
            new LoreField("VOLUME", "Volume", "How many of this item sell over the sample window."),
            new LoreField("CRAFT_COST", "Clean Craft", "Cost to craft the item from its base components (the clean craft line)."),
            new LoreField("FullCraftCost", "Full Craft Cost", "Total craft cost including all applied modifiers."),
            new LoreField("BazaarBuy", "Bazaar Buy", "Bazaar insta buy / buy order price."),
            new LoreField("BazaarSell", "Bazaar Sell", "Bazaar insta sell / sell order price."),
            new LoreField("LBIN_KEY", "LBIN Key", "Internal key used for the lowest bin lookup."),
            new LoreField("MEDIAN_KEY", "Median Key", "Internal key used for the median lookup."),
            new LoreField("ITEM_KEY", "Item Key", "The full item key the backend matched on."),
            new LoreField("EnchantCost", "Enchant Cost", "Value contributed by the item's enchantments."),
            new LoreField("GemValue", "Gem Value", "Value contributed by applied gemstones."),
            new LoreField("ModifierCost", "Modifier Cost", "Combined value of all applied modifiers."),
            new LoreField("ModifierCostList", "Modifier Cost List", "Per modifier breakdown of added value."),
            new LoreField("FinderEstimates", "Finder Estimates", "Per finder estimated value for the item."),
            new LoreField("SpentOnAhFees", "AH Fees Spent", "Auction house listing fees already paid."),
            new LoreField("KatUpgradeCost", "Kat Upgrade Cost", "Cost to upgrade the pet via Kat."),
            new LoreField("Volatility", "Volatility", "How much the price swings over time."),
            new LoreField("TimeToSell", "Time To Sell", "Estimated time for the item to sell."),
            new LoreField("InstaSellPrice", "Insta Sell Price", "Price you would get selling instantly."),
            new LoreField("PRICE_PAID", "Price Paid", "What you paid for the item (from history)."),
            new LoreField("LastSoldFor", "Last Sold For", "The price the item most recently sold for."),
            new LoreField("NpcSellPrice", "NPC Sell Price", "Coins from selling the item to an NPC."),
            new LoreField("AiEstimate", "AI Estimate", "Machine learning estimated value."),
            new LoreField("TAG", "Tag", "The internal item tag / id."),
            new LoreField("ColorCode", "Color Code", "The item's color code (for colored items)."),
            new LoreField("DefaultLore", "Default Lore", "The item's original vanilla lore lines.")
    );

    /** looks up a field by its backend key or null if unknown. */
    public static LoreField byKey(String key) {
        if (key == null) {
            return null;
        }
        for (LoreField f : ALL) {
            if (f.key.equalsIgnoreCase(key)) {
                return f;
            }
        }
        return null;
    }

    /** display name for a key falling back to the key itself never null . */
    public static String displayOf(String key) {
        LoreField f = byKey(key);
        if (f != null) {
            return f.display;
        }
        return key != null ? key : "?";
    }
}
