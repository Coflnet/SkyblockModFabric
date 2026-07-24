package com.coflnet.lore;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the Coflnet backend lore strings for one item into a {@link LoreData}.
 *
 * input is the list of stripped no section codes lore line values the backend
 * appended for an item for example
 *
 *  lbin 690 000
 *  med 550 000 vol 37
 *  full craft cost 500k
 *
 * the parser recognises every known field format observed in real dumps
 * lbin and median with optional n each per unit and estimate marker
 * auction volume full craft cost obtain cost with not craftable bazaar
 * buy and sell with per unit and bazaar volume buyvol sellvol.
 */
public class LoreParser {

    private static final String NUMBER = "[0-9][0-9,]*\\.?[0-9]*";
    private static final String NUM = "~?\\s*(" + NUMBER + ")(?:\\s*([kmbKMB])\\b)?";

    private static final Pattern LBIN = Pattern.compile("\\blbin:\\s*" + NUM, Pattern.CASE_INSENSITIVE);
    private static final Pattern MED = Pattern.compile("\\bmed:\\s*" + NUM, Pattern.CASE_INSENSITIVE);
    private static final Pattern LBIN_EACH = Pattern.compile("\\blbin:[^\\r\\n]*?\\(\\s*" + NUM + "\\s*each\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MED_EACH = Pattern.compile("\\bmed:[^\\r\\n]*?\\(\\s*" + NUM + "\\s*each\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VOL_SINGLE = Pattern.compile("\\bvol:\\s*" + NUM, Pattern.CASE_INSENSITIVE);
    private static final Pattern VOL_PAIR = Pattern.compile("\\bvol:\\s*" + NUM + "\\s*/\\s*" + NUM, Pattern.CASE_INSENSITIVE);
    private static final Pattern CRAFT = Pattern.compile("\\bfull craft cost:\\s*" + NUM, Pattern.CASE_INSENSITIVE);
    private static final Pattern CLEAN_CRAFT = Pattern.compile("\\bclean craft:\\s*" + NUM, Pattern.CASE_INSENSITIVE);
    private static final Pattern MODIFIER_COST = Pattern.compile("\\bmodifier cost:\\s*" + NUM, Pattern.CASE_INSENSITIVE);
    private static final Pattern OBTAIN = Pattern.compile("\\bobtain cost:\\s*" + NUM, Pattern.CASE_INSENSITIVE);
    private static final Pattern PAID = Pattern.compile("\\bpaid:\\s*" + NUM, Pattern.CASE_INSENSITIVE);
    private static final Pattern BUY = Pattern.compile("\\bbuy:\\s*" + NUM, Pattern.CASE_INSENSITIVE);
    private static final Pattern SELL = Pattern.compile("\\bsell:\\s*" + NUM, Pattern.CASE_INSENSITIVE);
    private static final Pattern BUY_EACH = Pattern.compile("\\bbuy:[^\\r\\n]*?\\(\\s*" + NUM + "\\s*each\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELL_EACH = Pattern.compile("\\bsell:[^\\r\\n]*?\\(\\s*" + NUM + "\\s*each\\)", Pattern.CASE_INSENSITIVE);

    /** parses all of an items stripped lore values into one loredata. */
    public static LoreData parse(List<String> strippedValues) {
        LoreData d = new LoreData();
        if (strippedValues == null) {
            return d;
        }
        for (String raw : strippedValues) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String line = raw.trim();
            String lower = line.toLowerCase(Locale.ROOT);

            Matcher buy = BUY.matcher(line);
            if (buy.find()) {
                d.buy = num(buy.group(1), buy.group(2));
            }
            Matcher buyEach = BUY_EACH.matcher(line);
            if (buyEach.find()) {
                d.buyEach = num(buyEach.group(1), buyEach.group(2));
            }
            Matcher sell = SELL.matcher(line);
            if (sell.find()) {
                d.sell = num(sell.group(1), sell.group(2));
            }
            Matcher sellEach = SELL_EACH.matcher(line);
            if (sellEach.find()) {
                d.sellEach = num(sellEach.group(1), sellEach.group(2));
            }

            Matcher volumePair = VOL_PAIR.matcher(line);
            if (volumePair.find() && !lower.contains("med:")) {
                d.buyVol = num(volumePair.group(1), volumePair.group(2));
                d.sellVol = num(volumePair.group(3), volumePair.group(4));
            } else {
                Matcher volume = VOL_SINGLE.matcher(line);
                if (volume.find()) {
                    d.volume = num(volume.group(1), volume.group(2));
                }
            }

            Matcher lbin = LBIN.matcher(line);
            if (lbin.find()) {
                d.lbin = num(lbin.group(1), lbin.group(2));
                d.lbinEstimate = lbin.group().contains("~") || lower.contains("estimate");
            }
            Matcher lbinEach = LBIN_EACH.matcher(line);
            if (lbinEach.find()) {
                d.lbinEach = num(lbinEach.group(1), lbinEach.group(2));
            }

            Matcher median = MED.matcher(line);
            if (median.find()) {
                d.median = num(median.group(1), median.group(2));
                d.medianEstimate = median.group().contains("~") || lower.contains("estimate");
            }
            Matcher medianEach = MED_EACH.matcher(line);
            if (medianEach.find()) {
                d.medianEach = num(medianEach.group(1), medianEach.group(2));
            }

            Matcher craft = CRAFT.matcher(line);
            if (craft.find()) {
                d.craftCost = num(craft.group(1), craft.group(2));
            }
            Matcher cleanCraft = CLEAN_CRAFT.matcher(line);
            if (cleanCraft.find()) {
                d.cleanCraft = num(cleanCraft.group(1), cleanCraft.group(2));
            }
            Matcher modifier = MODIFIER_COST.matcher(line);
            if (modifier.find()) {
                d.modifierCost = num(modifier.group(1), modifier.group(2));
            }
            Matcher obtain = OBTAIN.matcher(line);
            if (obtain.find()) {
                d.obtainCost = num(obtain.group(1), obtain.group(2));
                d.notCraftable = lower.contains("not craftable");
            }

            Matcher paid = PAID.matcher(line);
            if (paid.find()) {
                d.purchasedFor = num(paid.group(1), paid.group(2));
            }
        }
        return d;
    }

    /** parses a number token with optional comma grouping and k m b suffix. */
    static Double num(String digits, String suffix) {
        if (digits == null || digits.isBlank()) {
            return null;
        }
        try {
            double base = Double.parseDouble(digits.replace(",", "").trim());
            if (!Double.isFinite(base)) {
                return null;
            }
            if (suffix != null && !suffix.isBlank()) {
                switch (Character.toLowerCase(suffix.charAt(0))) {
                    case 'k' -> base *= 1_000d;
                    case 'm' -> base *= 1_000_000d;
                    case 'b' -> base *= 1_000_000_000d;
                    default -> { }
                }
            }
            return base;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
