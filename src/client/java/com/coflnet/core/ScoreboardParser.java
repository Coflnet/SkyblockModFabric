package com.coflnet.core;

import java.util.Locale;

public final class ScoreboardParser {
    private ScoreboardParser() {
    }

    public static Values getRelevantLines(String[] scores) {
        String left = "";
        String right = "null";
        for (String score : scores) {
            if (score.startsWith("Purse: ") || score.startsWith("Piggy: ")) {
                left = score;
            }
            if (score.startsWith(" ⏣ ") || score.startsWith("  ")) {
                right = score;
            }
        }
        return new Values(left, right);
    }

    public static boolean containsHypixelFooter(String[] scores) {
        if (scores == null) {
            return false;
        }
        for (String score : scores) {
            if (normalizeLine(score).endsWith("hypixel.net")) {
                return true;
            }
        }
        return false;
    }

    static String normalizeLine(String score) {
        if (score == null) {
            return "";
        }
        return FormattingCodes.strip(score).replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT);
    }

    public record Values(String left, String right) {
    }
}
