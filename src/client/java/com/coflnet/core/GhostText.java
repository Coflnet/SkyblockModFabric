package com.coflnet.core;

/** Computes the chat ghost text for an expected command and where the typed text diverges from it. */
public final class GhostText {
    private GhostText() {}

    public record Result(String suggestionSuffix, int mismatchIndex) {}

    private static final Result NOTHING = new Result("", -1);

    public static Result compute(String typed, String expected) {
        if (typed == null || typed.isEmpty() || expected == null || expected.isEmpty()) return NOTHING;
        if (expected.startsWith(typed)) return new Result(expected.substring(typed.length()), -1);
        int i = 0, max = Math.min(typed.length(), expected.length());
        while (i < max && typed.charAt(i) == expected.charAt(i)) i++;
        return new Result("", i);
    }
}
