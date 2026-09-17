package com.coflnet.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfoDisplayLayoutTest {
    private static final int REPRESENTATIVE_DISPLAY_WIDTH = 112;

    @Test
    void defaultBazaarInfoDisplayStaysInBoundsAndOutsideCenteredContainer() {
        for (String title : new String[]{"Co-op Bazaar Orders", "Bazaar ➜ Products"}) {
            assertSafeDefault(title, 1280, 720);
            assertSafeDefault(title, 640, 360);
            assertSafeDefault(title, 426, 240);
        }
    }

    private static void assertSafeDefault(String title, int viewportWidth, int viewportHeight) {
        int containerWidth = 176;
        int containerHeight = 222;
        var container = new InfoDisplayLayout.Rect(
                (viewportWidth - containerWidth) / 2,
                (viewportHeight - containerHeight) / 2,
                containerWidth,
                containerHeight);
        var display = InfoDisplayLayout.placeDefault(title, viewportWidth, viewportHeight,
                container, REPRESENTATIVE_DISPLAY_WIDTH, 45);

        assertTrue(display.isInside(viewportWidth, viewportHeight), title + " display must be in viewport");
        assertFalse(display.intersects(container), title + " display must not overlap container");
    }

    @Test void pixelFractionRoundTrip() {
        int screenWidth = 1920;
        int screenHeight = 1080;
        int px = InfoDisplayLayout.toPixelX(0.5, screenWidth);
        int py = InfoDisplayLayout.toPixelY(0.25, screenHeight);
        assertEquals(960, px);
        assertEquals(270, py);
        assertEquals(0.5, InfoDisplayLayout.toFractionX(px, screenWidth), 1e-9);
        assertEquals(0.25, InfoDisplayLayout.toFractionY(py, screenHeight), 1e-9);
    }

    @Test void fractionConversionGuardsZeroScreenSize() {
        assertEquals(0.0, InfoDisplayLayout.toFractionX(100, 0));
        assertEquals(0.0, InfoDisplayLayout.toFractionY(100, 0));
    }

    @Test void clampToScreenKeepsDisplayFullyVisible() {
        // Way off to the bottom-right: must clamp back onto the visible area.
        double[] clamped = InfoDisplayLayout.clampToScreen(1.5, 1.5, 200, 100, 1000, 500);
        int x = InfoDisplayLayout.toPixelX(clamped[0], 1000);
        int y = InfoDisplayLayout.toPixelY(clamped[1], 500);
        assertEquals(800, x); // 1000 - 200
        assertEquals(400, y); // 500 - 100

        // Negative: must clamp back to the top-left corner.
        double[] clampedNeg = InfoDisplayLayout.clampToScreen(-0.5, -0.5, 200, 100, 1000, 500);
        assertEquals(0, InfoDisplayLayout.toPixelX(clampedNeg[0], 1000));
        assertEquals(0, InfoDisplayLayout.toPixelY(clampedNeg[1], 500));

        // Already fully on screen: unchanged (within rounding).
        double[] unchanged = InfoDisplayLayout.clampToScreen(0.1, 0.1, 200, 100, 1000, 500);
        assertEquals(100, InfoDisplayLayout.toPixelX(unchanged[0], 1000));
        assertEquals(50, InfoDisplayLayout.toPixelY(unchanged[1], 500));
    }

    @Test void clampToScreenHandlesDisplayLargerThanScreen() {
        double[] clamped = InfoDisplayLayout.clampToScreen(0.5, 0.5, 2000, 2000, 1000, 500);
        assertEquals(0, InfoDisplayLayout.toPixelX(clamped[0], 1000));
        assertEquals(0, InfoDisplayLayout.toPixelY(clamped[1], 500));
    }

    // clampX/clampY are the allocation-free primitives the per-frame renderer path uses directly
    // (clampToScreen wraps them but allocates a double[] result, fine for the editor/tests, not the hot path).

    @Test void clampXYAgreeWithClampToScreen() {
        // Off-screen bottom-right.
        assertEquals(800, InfoDisplayLayout.clampX(1.5, 200, 1000));
        assertEquals(400, InfoDisplayLayout.clampY(1.5, 100, 500));
        // Negative, clamps to the top-left corner.
        assertEquals(0, InfoDisplayLayout.clampX(-0.5, 200, 1000));
        assertEquals(0, InfoDisplayLayout.clampY(-0.5, 100, 500));
        // Already fully on screen: unchanged.
        assertEquals(100, InfoDisplayLayout.clampX(0.1, 200, 1000));
        assertEquals(50, InfoDisplayLayout.clampY(0.1, 100, 500));
    }

    @Test void clampXYHandleDisplayLargerThanScreen() {
        assertEquals(0, InfoDisplayLayout.clampX(0.5, 2000, 1000));
        assertEquals(0, InfoDisplayLayout.clampY(0.5, 2000, 500));
    }

    @Test void clampsScale() {
        assertEquals(InfoDisplayLayout.MIN_SCALE, InfoDisplayLayout.clampScale(0.1));
        assertEquals(InfoDisplayLayout.MAX_SCALE, InfoDisplayLayout.clampScale(10));
        assertEquals(1.5, InfoDisplayLayout.clampScale(1.5));
    }

    @Test void clampsBackgroundAlpha() {
        assertEquals(InfoDisplayLayout.MIN_BACKGROUND_ALPHA, InfoDisplayLayout.clampBackgroundAlpha(-1));
        assertEquals(InfoDisplayLayout.MAX_BACKGROUND_ALPHA, InfoDisplayLayout.clampBackgroundAlpha(2));
        assertEquals(0.35, InfoDisplayLayout.clampBackgroundAlpha(0.35));
    }

    @Test void clampsTextAlpha() {
        assertEquals(InfoDisplayLayout.MIN_TEXT_ALPHA, InfoDisplayLayout.clampTextAlpha(0.0));
        assertEquals(InfoDisplayLayout.MAX_TEXT_ALPHA, InfoDisplayLayout.clampTextAlpha(2));
        assertEquals(0.8, InfoDisplayLayout.clampTextAlpha(0.8));
    }
}
