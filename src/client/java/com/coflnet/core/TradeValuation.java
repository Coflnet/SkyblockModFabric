package com.coflnet.core;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TradeValuation {
    public enum WorthBasis { LBIN, MEDIAN, AI_ESTIMATE }

    private static final String NUMBER = "([\\d,]+(?:\\.\\d+)?(?:\\s*[kmb]\\b)?)";
    private static final Pattern QUOTE = Pattern.compile(
            "\\b(lbin|lowest\\s*bin|med(?:ian)?|ai\\s*estimate|buy|sell)\\s*:?\\s*(~?)\\s*"
                    + NUMBER + "((?:\\s*\\([^)]*\\))*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EACH = Pattern.compile("\\(" + NUMBER + "\\s*each\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COINS = Pattern.compile(
            "^([\\d,]*\\.?\\d+\\s*[kmb]?)\\s+coins$", Pattern.CASE_INSENSITIVE);

    private TradeValuation() {
    }

    public static Long parseWorthFromTips(String[] tips, WorthBasis basis) {
        return parseWorthFromTips(tips, basis, 1);
    }

    /** AI mode requires an AI quote; market modes prefer exact quotes, then AI/approximate quotes. */
    public static Long parseWorthFromTips(String[] tips, WorthBasis basis, int stackCount) {
        if (tips == null || stackCount <= 0) return null;
        Long best = null;
        int bestRank = Integer.MAX_VALUE;
        for (String tip : tips) {
            if (tip == null) continue;
            Matcher quote = QUOTE.matcher(FormattingCodes.strip(tip));
            while (quote.find()) {
                String label = quote.group(1).toLowerCase(java.util.Locale.ROOT);
                if (basis == WorthBasis.AI_ESTIMATE && !label.startsWith("ai")) continue;
                String suffix = quote.group(4).toLowerCase(java.util.Locale.ROOT);
                // A base-item LBIN with no matching upgrades is not a usable trade quote.
                if (suffix.contains("no match")) continue;
                boolean approximate = !quote.group(2).isEmpty() || suffix.contains("higher value");
                boolean median = label.startsWith("med");
                boolean bazaar = label.equals("buy") || label.equals("sell");
                boolean preferred = bazaar ? label.equals(basis == WorthBasis.LBIN ? "buy" : "sell")
                        : median == (basis == WorthBasis.MEDIAN);
                int rank = label.startsWith("ai") ? 2 : approximate ? (median ? 3 : 4) : preferred ? 0 : 1;
                Matcher each = EACH.matcher(suffix);
                boolean perItem = each.find();
                Long value = NumberParser.parseCoinNumber(perItem ? each.group(1) : quote.group(3));
                // Auction/AI and Bazaar headline prices are stack totals. Prefer explicit per-item values.
                if (value != null && !perItem) value /= stackCount;
                if (value != null && value > 0 && rank < bestRank) {
                    best = value;
                    bestRank = rank;
                }
            }
        }
        return best;
    }

    public static Long parseCoinOffer(String displayName) {
        if (displayName == null) return null;
        Matcher matcher = COINS.matcher(FormattingCodes.strip(displayName).trim());
        return matcher.matches() ? NumberParser.parseCoinNumber(matcher.group(1)) : null;
    }

    public static SideValue sum(List<Long> offeredValues) {
        long total = 0L;
        int unpriced = 0;
        for (Long value : offeredValues) {
            if (value == null) unpriced++;
            else total += value;
        }
        return new SideValue(total, unpriced);
    }

    public record SideValue(long total, int unpriced) {
    }
}
