package com.coflnet.core;

import java.util.Locale;

public final class CountdownFormatter {
    private CountdownFormatter() {
    }

    /**
     * Renders the remaining seconds cut to {@code maxPrecision} characters ("9.5s" for 9.512 at 3).
     * The precision comes from the backend and the result is drawn every frame, so an
     * out-of-range value is clamped to what the text holds rather than thrown on the render thread.
     */
    public static String format(double seconds, int maxPrecision) {
        if (seconds > 100) {
            return (int) seconds + "s";
        }
        String text = String.format(Locale.US, "%.3f", seconds);
        int precision = Math.max(1, Math.min(text.length(), maxPrecision));
        String render = text.substring(0, precision);
        if (render.endsWith(".")) {
            render = render.substring(0, precision - 1);
        }
        return render + "s";
    }
}
