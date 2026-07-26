package com.coflnet.lore;

import java.util.List;
import java.util.regex.Pattern;

/**
 * one restylable field segment within a backend lore line.
 *
 * the coflnet backend packs several fields into a single appended line for
 * example {@code "§7Med: §b46,894,511 §7Vol: §e0.2"} carries MEDIAN and VOLUME
 * together and the craft line carries both clean craft and full craft cost .
 * the old engine classified each line as one class and replaced the whole line
 * so editing one field on a shared line dropped the others.
 *
 * A segment instead matches just its own {@link #pattern} (the label plus its
 * value inside a line so the engine can substitute each field independently and
 * leave everything else other fields freeform suffixes like
 *  estimate no match found and any unknown text exactly as the backend
 * sent it. this is inherently passthrough safe a segment that does not match or
 * whose template renders nothing leaves the line untouched.
 *
 * The patterns were verified against real {@code /cofl} dumps:
 *
 *   <li>the value is an "atomic" number — {@code (?![\d,.])} stops it matching a
 *  truncated prefix of a longer number
 *  trailing whitespace is only consumed when a k m b suffix follows so the
 *  space between adjacent fields is preserved
 *  volume excludes the bazaar vol x y pair via a negative lookahead.
 *
 */
public final class LoreSegment {

    // a coloured optionally prefixed comma decimal number with optional
    // k m b. the trailing s kmbkmb only eats the space when a suffix is
    // actually present so 46 894 511 keeps its trailing space.
    private static final String VAL =
            "(?:\u00A7.)?\\s*~?\\s*[\\d,]+(?:\\.\\d+)?(?![\\d,.])(?:\\s*[kmbKMB]\\b)?";
    // volume must not match the bazaar volume pair vol 2 940 1 670.
    private static final String VAL_VOL =
            "(?>" + VAL + ")(?!\\s*(?:\u00A7.)?\\s*/)";

    /** field key matches the layout field key where one exists . */
    public final String key;
    /** human display name for the gui. */
    public final String display;
    /** pattern that selects this fields segment inside a backend line. */
    public final Pattern pattern;
    /** default template fragment reproducing the stock look. */
    public final String defaultTemplate;
    /** hint of tokens this segments template can use. */
    public final String tokens;

    private LoreSegment(String key, String display, String regex, String defaultTemplate, String tokens) {
        this.key = key;
        this.display = display;
        this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        this.defaultTemplate = defaultTemplate;
        this.tokens = tokens;
    }

    /**
     * the full catalog of restylable segments in render priority order. each
     * maps to the backend field label it appears under.
     */
    public static final List<LoreSegment> ALL = List.of(
            new LoreSegment("LBIN", "Lowest BIN", "\u00A7.lbin:\\s*" + VAL,
                    "&7lbin: &e{lbin}", "{lbin} {lbin_short}"),
            new LoreSegment("MEDIAN", "Median", "\u00A7.Med:\\s*" + VAL,
                    "&7Med: &b{median}", "{median} {median_short}"),
            new LoreSegment("VOLUME", "Volume", "\u00A7.Vol:\\s*" + VAL_VOL,
                    "&7Vol: &e{volume}", "{volume}"),
            new LoreSegment("BazaarBuy", "Bazaar Buy", "\u00A7.Buy:\\s*" + VAL,
                    "&7Buy: &6{buy}", "{buy} {buy_short}"),
            new LoreSegment("BazaarSell", "Bazaar Sell", "\u00A7.Sell:\\s*" + VAL,
                    "&7Sell: &6{sell}", "{sell} {sell_short}"),
            new LoreSegment("CRAFT_COST", "Clean Craft", "\u00A7.clean craft:\\s*" + VAL,
                    "&7clean craft: &e{cleancraft}", "{cleancraft}"),
            new LoreSegment("FullCraftCost", "Full Craft Cost", "\u00A7.Full Craft Cost:\\s*" + VAL,
                    "&7Full Craft Cost: &e{craftcost}", "{craftcost}"),
            new LoreSegment("ModifierCost", "Modifier Cost", "\u00A7.Modifier Cost:\\s*" + VAL,
                    "&7Modifier Cost: &e{modifiercost}", "{modifiercost}"),
            new LoreSegment("ObtainCost", "Obtain Cost", "\u00A7.Obtain cost:\\s*" + VAL,
                    "&7Obtain cost: &f{obtaincost}", "{obtaincost}"),
            new LoreSegment("PRICE_PAID", "Price Paid", "\u00A7.Paid:\\s*" + VAL,
                    "&7Paid: &e{paid}", "{paid}")
    );

    /** looks up a segment by backend field key case insensitive or null. */
    public static LoreSegment byKey(String key) {
        if (key == null) {
            return null;
        }
        for (LoreSegment s : ALL) {
            if (s.key.equalsIgnoreCase(key)) {
                return s;
            }
        }
        return null;
    }
}
