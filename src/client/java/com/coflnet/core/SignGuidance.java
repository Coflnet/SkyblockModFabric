package com.coflnet.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Decides what the sign-input guidance overlay shows for the value typed on the first sign line. */
public final class SignGuidance {
    private SignGuidance() {}

    public enum State { TYPING, DONE, MISMATCH, NONE }

    public record Guidance(State state, String typed, String remaining, String message) {}

    public static Guidance compute(String typedLine0, String expectedValue) {
        String typed = typedLine0 == null ? "" : typedLine0;
        if (expectedValue == null || expectedValue.isBlank()) {
            return new Guidance(State.NONE, typed, "", "");
        }
        String expected = expectedValue.trim();
        if (typed.trim().equals(expected)) {
            return new Guidance(State.DONE, typed, "", "done — press Done or Esc");
        }
        if (expected.startsWith(typed)) {
            return new Guidance(State.TYPING, typed, expected.substring(typed.length()), "");
        }
        return new Guidance(State.MISMATCH, typed, "", "wrong value — clear and retype " + expected);
    }

    /** {@code 1280} becomes {@code 1,280}; anything that is not a pure digit string is returned unchanged. */
    public static String formatAmount(String value) {
        if (value == null || value.isEmpty() || !value.chars().allMatch(c -> c >= '0' && c <= '9')) return value;
        StringBuilder out = new StringBuilder();
        int len = value.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) out.append(',');
            out.append(value.charAt(i));
        }
        return out.toString();
    }

    /**
     * Splits the formatted full expected value into the part already typed and the rest, so grouping
     * commas stay consistent (typed {@code 1280} of {@code 128000} shows {@code 128,0} + {@code 00}).
     * Non-digit values fall back to the raw typed text and remaining characters.
     */
    public static String[] displaySplit(Guidance g) {
        String full = g.typed() + g.remaining();
        boolean digits = !full.isEmpty() && full.chars().allMatch(c -> c >= '0' && c <= '9');
        if (!digits || g.state() != State.TYPING) {
            return new String[] {formatAmount(g.typed()), g.remaining()};
        }
        String formatted = formatAmount(full);
        int digitsSeen = 0, cut = 0;
        while (cut < formatted.length() && digitsSeen < g.typed().length()) {
            if (formatted.charAt(cut) != ',') digitsSeen++;
            cut++;
        }
        return new String[] {formatted.substring(0, cut), formatted.substring(cut)};
    }

    /**
     * Picks the expected sign value: a click-armed fill (only when its {@code line} equals the 4th
     * sign line), then a pending bazaar search, then the "&lt;4th line&gt;: &lt;value&gt;" suggestion.
     *
     * @return the expected value, or null when none applies
     */
    public static String expectedValue(String pendingSignFillJson, String pendingBazaarSearch,
                                       String priceSuggestion, String fourthLine) {
        return expectedValue(pendingSignFillJson, pendingBazaarSearch, priceSuggestion, fourthLine, null);
    }

    /** As above, with the local Bazaar guide's value as the last resort, so backend and goal fills win. */
    public static String expectedValue(String pendingSignFillJson, String pendingBazaarSearch,
                                       String priceSuggestion, String fourthLine, String localGuideValue) {
        if (pendingSignFillJson != null && !pendingSignFillJson.isBlank()) {
            try {
                JsonObject fill = JsonParser.parseString(pendingSignFillJson).getAsJsonObject();
                if (fill.has("line") && fill.has("value") && fill.get("line").getAsString().equals(fourthLine)) {
                    return fill.get("value").getAsString();
                }
            } catch (RuntimeException ignored) {
                // malformed payload: fall through to the other sources
            }
        }
        if (pendingBazaarSearch != null && !pendingBazaarSearch.isBlank()) return pendingBazaarSearch;
        if (priceSuggestion != null) {
            String[] parts = priceSuggestion.split(": ", 2);
            if (parts.length == 2 && parts[0].equals(fourthLine)) return parts[1].trim();
        }
        return localGuideValue;
    }
}
