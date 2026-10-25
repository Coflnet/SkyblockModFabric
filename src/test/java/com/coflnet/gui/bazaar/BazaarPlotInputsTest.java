package com.coflnet.gui.bazaar;

import com.coflnet.core.BazaarHistory;
import com.coflnet.core.BazaarHistoryClient.Range;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.coflnet.core.BazaarHistory.Metric.BUY_PRICE;
import static com.coflnet.core.BazaarHistory.Metric.SELL_PRICE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarPlotInputsTest {
    private static BazaarHistory history() {
        return new BazaarHistory(List.of(new BazaarHistory.Sample(Instant.EPOCH, 12, 10, 100, 200)));
    }

    @Test void historyScreenRebuildsEachChangedPlotOnce() {
        var plotted = new BazaarPlotInputs();
        var cached = history();
        // Opening: load() plots the cached history, a later init() layout pass must not plot it again.
        assertTrue(plotted.change(cached, BUY_PRICE, Range.DAY, 1, false, 400));
        assertFalse(plotted.change(cached, BUY_PRICE, Range.DAY, 1, false, 400));
        // Range button: load() then repositionElements() -> init().
        assertTrue(plotted.change(cached, BUY_PRICE, Range.WEEK, 1, false, 400));
        assertFalse(plotted.change(cached, BUY_PRICE, Range.WEEK, 1, false, 400));
        // Metric button and zoom: the direct rebuild, then the re-layout.
        assertTrue(plotted.change(cached, SELL_PRICE, Range.WEEK, 1, false, 400));
        assertFalse(plotted.change(cached, SELL_PRICE, Range.WEEK, 1, false, 400));
        assertTrue(plotted.change(cached, SELL_PRICE, Range.WEEK, 2, false, 400));
        assertFalse(plotted.change(cached, SELL_PRICE, Range.WEEK, 2, false, 400));
        // An unexpired reload resolves to the cached instance; fresh data, compare and resizing rebuild.
        assertFalse(plotted.change(cached, SELL_PRICE, Range.WEEK, 2, false, 400));
        assertTrue(plotted.change(history(), SELL_PRICE, Range.WEEK, 2, false, 400));
        assertTrue(plotted.change(cached, SELL_PRICE, Range.WEEK, 2, true, 400));
        assertTrue(plotted.change(cached, SELL_PRICE, Range.WEEK, 2, true, 500));
        // After the screen dropped its plot for missing data, the same history must be plotted again.
        plotted.clear();
        assertTrue(plotted.change(cached, SELL_PRICE, Range.WEEK, 2, true, 500));
    }
}
