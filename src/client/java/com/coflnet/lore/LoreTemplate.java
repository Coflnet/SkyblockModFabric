package com.coflnet.lore;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * renders a user lore template into a finished minecraft component string.
 *  
 * a template mixes literal text ampersand colour codes and token 
 * placeholders for example 
 *  
 *  l clowest ebin r lbin 
 *  
 * Tokens resolve against a parsed {@link LoreData}. Each numeric token has two
 * forms the bare name renders full with comma grouping lbin 690 000 
 * and a short suffix renders abbreviated lbin short 690k . ampersand
 * codes are converted to the section sign the game expects. if a token has no
 * value for the item, {@link #render} returns null so the caller can skip the
 * whole line this is how empty modules hide themselves .
 */
public class LoreTemplate {

    private static final Pattern TOKEN = Pattern.compile("\\{([a-zA-Z_]+)\\}");

    /**
     * renders the template against the data. returns the finished string with
     * section codes or null when a referenced price token has no value so the
     * line should be skipped .
     */
    public static String render(String template, LoreData data) {
        if (template == null || template.isEmpty()) {
            return null;
        }
        Matcher m = TOKEN.matcher(template);
        StringBuilder out = new StringBuilder();
        boolean sawToken = false;
        while (m.find()) {
            sawToken = true;
            String token = m.group(1);
            String value = resolve(token, data);
            if (value == null) {
                // a referenced price token is missing for this item hide the line.
                return null;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        String result = colorize(out.toString());
        // templates that are pure literal text no tokens still render so a
        // user can add a custom static line.
        return (!sawToken && result.isBlank()) ? null : result;
    }

    /** resolves one token to its display string or null if it has no value. */
    private static String resolve(String token, LoreData data) {
        String lower = token.toLowerCase(Locale.ROOT);
        boolean shortForm = lower.endsWith("_short");
        String base = shortForm ? lower.substring(0, lower.length() - "_short".length()) : lower;

        // non numeric flag tokens.
        switch (base) {
            case "estimate" -> {
                return (data.lbinEstimate || data.medianEstimate) ? "(estimate)" : "";
            }
            case "notcraftable" -> {
                return data.notCraftable ? "(not craftable)" : "";
            }
            default -> { }
        }

        Double v = data.get(base);
        if (v == null) {
            return null;
        }
        return shortForm ? formatShort(v) : formatFull(v);
    }

    /** full grouping format 690000 690 000 keeps up to 2 decimals if present. */
    static String formatFull(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) {
            return String.format(Locale.US, "%,d", (long) v);
        }
        return String.format(Locale.US, "%,.2f", v);
    }

    /** abbreviated format 690000 690k 4420000 4.42m 913980000 913.98m. */
    static String formatShort(double v) {
        double abs = Math.abs(v);
        if (abs >= 1_000_000_000d) {
            return trim(v / 1_000_000_000d) + "B";
        }
        if (abs >= 1_000_000d) {
            return trim(v / 1_000_000d) + "M";
        }
        if (abs >= 1_000d) {
            return trim(v / 1_000d) + "K";
        }
        return trim(v);
    }

    private static String trim(double v) {
        String s = String.format(Locale.US, "%.2f", v);
        // drop trailing zeros and a dangling dot
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    /** converts ampersand colour codes to the section sign codes the game uses. */
    static String colorize(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length() && isCode(s.charAt(i + 1))) {
                b.append('\u00A7').append(Character.toLowerCase(s.charAt(i + 1)));
                i++;
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static boolean isCode(char c) {
        return "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(c) >= 0;
    }
}
