package com.coflnet.core;

import java.util.Objects;

/** Pure placement and scaling math for container-side and permanent HUD info displays. */
public final class InfoDisplayLayout {
    private static final int GAP = 5;

    public static final double MIN_SCALE = 0.5;
    public static final double MAX_SCALE = 3.0;
    public static final double MIN_BACKGROUND_ALPHA = 0.0;
    public static final double MAX_BACKGROUND_ALPHA = 1.0;
    public static final double MIN_TEXT_ALPHA = 0.2;
    public static final double MAX_TEXT_ALPHA = 1.0;

    private InfoDisplayLayout() {
    }

    public static Rect placeDefault(String menuTitle, int viewportWidth, int viewportHeight,
                                    Rect container, int displayWidth, int displayHeight) {
        Objects.requireNonNull(menuTitle, "menuTitle");
        if (viewportWidth <= 0 || viewportHeight <= 0 || displayWidth <= 0 || displayHeight <= 0) {
            throw new IllegalArgumentException("viewport and display dimensions must be positive");
        }

        int y = clampInt(container.y() + GAP, 0, viewportHeight - displayHeight);
        int leftX = container.x() - GAP - displayWidth;
        if (leftX >= 0) {
            return new Rect(leftX, y, displayWidth, displayHeight);
        }

        int rightX = container.right() + GAP;
        if (rightX + displayWidth <= viewportWidth) {
            return new Rect(rightX, y, displayWidth, displayHeight);
        }

        int x = clampInt(container.x(), 0, viewportWidth - displayWidth);
        int aboveY = container.y() - GAP - displayHeight;
        if (aboveY >= 0) {
            return new Rect(x, aboveY, displayWidth, displayHeight);
        }
        return new Rect(x, clampInt(container.bottom() + GAP, 0, viewportHeight - displayHeight),
                displayWidth, displayHeight);
    }

    public record Rect(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean intersects(Rect other) {
            return x < other.right() && right() > other.x()
                    && y < other.bottom() && bottom() > other.y();
        }

        public boolean isInside(int viewportWidth, int viewportHeight) {
            return x >= 0 && y >= 0 && right() <= viewportWidth && bottom() <= viewportHeight;
        }
    }

    public static int toPixelX(double xFraction, int screenWidth) {
        return (int) Math.round(xFraction * screenWidth);
    }

    public static int toPixelY(double yFraction, int screenHeight) {
        return (int) Math.round(yFraction * screenHeight);
    }

    public static double toFractionX(int pixelX, int screenWidth) {
        return screenWidth <= 0 ? 0.0 : (double) pixelX / screenWidth;
    }

    public static double toFractionY(int pixelY, int screenHeight) {
        return screenHeight <= 0 ? 0.0 : (double) pixelY / screenHeight;
    }

    /**
     * Clamps a fractional position so a display of the given pixel size stays
     * fully on screen. Returns {@code {xFraction, yFraction}}. Allocates a
     * result array, so it's meant for callers that need fractions back (the
     * editor, tests) - the hot per-frame render path should use
     * {@link #clampX} / {@link #clampY} directly instead.
     */
    public static double[] clampToScreen(double xFraction, double yFraction, int displayWidthPx, int displayHeightPx,
                                          int screenWidth, int screenHeight) {
        int px = clampX(xFraction, displayWidthPx, screenWidth);
        int py = clampY(yFraction, displayHeightPx, screenHeight);
        return new double[]{toFractionX(px, screenWidth), toFractionY(py, screenHeight)};
    }

    /** Clamped on-screen pixel X for a display of the given width; allocation-free. */
    public static int clampX(double xFraction, int displayWidthPx, int screenWidth) {
        int maxX = Math.max(0, screenWidth - displayWidthPx);
        return clampInt(toPixelX(xFraction, screenWidth), 0, maxX);
    }

    /** Clamped on-screen pixel Y for a display of the given height; allocation-free. */
    public static int clampY(double yFraction, int displayHeightPx, int screenHeight) {
        int maxY = Math.max(0, screenHeight - displayHeightPx);
        return clampInt(toPixelY(yFraction, screenHeight), 0, maxY);
    }

    public static double clampScale(double scale) {
        return clampDouble(scale, MIN_SCALE, MAX_SCALE);
    }

    public static double clampBackgroundAlpha(double alpha) {
        return clampDouble(alpha, MIN_BACKGROUND_ALPHA, MAX_BACKGROUND_ALPHA);
    }

    public static double clampTextAlpha(double alpha) {
        return clampDouble(alpha, MIN_TEXT_ALPHA, MAX_TEXT_ALPHA);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
