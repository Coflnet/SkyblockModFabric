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

    // a number token optional digits with commas and decimals optional k m b.
    private static final String NUM = "~?\\s*([0-9][0-9,]*\\.?[0-9]*)\\s*([kmbKMB]?)";

    private static final Pattern LBIN = Pattern.compile("lbin:\\s*" + NUM, Pattern.CASE_INSENSITIVE);
    private static final Pattern MED = Pattern.compile("med:\\s*" + NUM, Pattern.CASE_INSENSITIVE);
    private static final Pattern EACH = Pattern.compile("\\(\\s*([0-9][0-9,]*\\.?[0-9]*)\\s*([kmbKMB]?)\\s*each\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VOL_SINGLE = Pattern.compile("vol:\\s*([0-9][0-9,]*\\.?[0-9]*)\\s*([kmbKMB]?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VOL_PAIR = Pattern.compile("vol:\\s*([0-9][0-9,]*\\.?[0-9]*)\\s*([kmbKMB]?)\\s*/\\s*([0-9][0-9,]*\\.?[0-9]*)\\s*([kmbKMB]?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CRAFT = Pattern.compile("full craft cost:\\s*" + NUM, Pattern.CASE_INSENSITIVE);
    private static final Pattern CLEAN_CRAFT = Pattern.compile("clean craft:\\s*" + NUM, Pattern.CASE_INSENSITIVE);
    private static final Pattern MODIFIER_COST = Pattern.compile("modifier cost:\\s*" + NUM, Pattern.CASE_INSENSITIVE);
    private static final Pattern OBTAIN = Pattern.compile("obtain cost:\\s*([0-9][0-9,]*\\.?[0-9]*)\\s*([kmbKMB]?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAID = Pattern.compile("paid:\\s*" + NUM, Pattern.CASE_INSENSITIVE);
    private static final Pattern BUY = Pattern.compile("buy:\\s*([0-9][0-9,]*\\.?[0-9]*)\\s*([kmbKMB]?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELL = Pattern.compile("sell:\\s*([0-9][0-9,]*\\.?[0-9]*)\\s*([kmbKMB]?)", Pattern.CASE_INSENSITIVE);

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

            // bazaar buy sell line also carries per unit each values .
            if (lower.contains("buy:") && lower.contains("sell:")) {
                Matcher b = BUY.matcher(line);
                if (b.find()) {
                    d.buy = num(b.group(1), b.group(2));
                }
                Matcher s = SELL.matcher(line);
                if (s.find()) {
                    d.sell = num(s.group(1), s.group(2));
                }
                Matcher e = EACH.matcher(line);
                if (e.find()) {
                    d.buyEach = num(e.group(1), e.group(2));
                    if (e.find()) {
                        d.sellEach = num(e.group(1), e.group(2));
                    }
                }
                continue;
            }

            // bazaar volume pair vol x y slash distinguishes from auction vol .
            if (lower.contains("vol:") && lower.contains("/") && !lower.contains("med:")) {
                Matcher v = VOL_PAIR.matcher(line);
                if (v.find()) {
                    d.buyVol = num(v.group(1), v.group(2));
                    d.sellVol = num(v.group(3), v.group(4));
                    continue;
                }
            }

            // lbin line.
            if (lower.contains("lbin:")) {
                Matcher m = LBIN.matcher(line);
                if (m.find()) {
                    d.lbin = num(m.group(1), m.group(2));
                    d.lbinEstimate = line.contains("~") || lower.contains("estimate");
                }
                Matcher e = EACH.matcher(line);
                if (e.find()) {
                    d.lbinEach = num(e.group(1), e.group(2));
                }
                continue;
            }

            // median auction volume on the same line .
            if (lower.contains("med:")) {
                Matcher m = MED.matcher(line);
                if (m.find()) {
                    d.median = num(m.group(1), m.group(2));
                    d.medianEstimate = line.contains("~") || lower.contains("estimate");
                }
                Matcher e = EACH.matcher(line);
                if (e.find()) {
                    d.medianEach = num(e.group(1), e.group(2));
                }
                Matcher v = VOL_SINGLE.matcher(line);
                if (v.find()) {
                    d.volume = num(v.group(1), v.group(2));
                }
                continue;
            }

            // craft cost line also carries the optional clean craft value .
            if (lower.contains("full craft cost:")) {
                Matcher m = CRAFT.matcher(line);
                if (m.find()) {
                    d.craftCost = num(m.group(1), m.group(2));
                }
                Matcher c = CLEAN_CRAFT.matcher(line);
                if (c.find()) {
                    d.cleanCraft = num(c.group(1), c.group(2));
                }
                continue;
            }

            // modifier cost line.
            if (lower.contains("modifier cost:")) {
                Matcher m = MODIFIER_COST.matcher(line);
                if (m.find()) {
                    d.modifierCost = num(m.group(1), m.group(2));
                }
                continue;
            }

            // obtain cost uncraftable items .
            if (lower.contains("obtain cost:")) {
                Matcher m = OBTAIN.matcher(line);
                if (m.find()) {
                    d.obtainCost = num(m.group(1), m.group(2));
                }
                d.notCraftable = lower.contains("not craftable");
                continue;
            }

            // price you paid paid 13 900 000 48.2d ago .
            if (lower.contains("paid:")) {
                Matcher m = PAID.matcher(line);
                if (m.find()) {
                    d.purchasedFor = num(m.group(1), m.group(2));
                }
                continue;
            }
            // no craft cost found and unknown lines nothing to extract.
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
