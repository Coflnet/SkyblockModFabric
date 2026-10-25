package com.coflnet.gui.bazaar;

import com.coflnet.core.BazaarHistory;
import com.coflnet.core.BazaarHistoryClient;

/** Remembers what the history screen last plotted so re-layouts and repeated loads rebuild the plot only when an input changed. */
final class BazaarPlotInputs {
    private BazaarHistory history;
    private BazaarHistory.Metric metric;
    private BazaarHistoryClient.Range range;
    private int zoom, width;
    private boolean compare;

    /** Records the inputs and returns whether they differ from the last plot; history is compared by identity. */
    boolean change(BazaarHistory history, BazaarHistory.Metric metric, BazaarHistoryClient.Range range,
                   int zoom, boolean compare, int width) {
        if (history == this.history && metric == this.metric && range == this.range
                && zoom == this.zoom && compare == this.compare && width == this.width) return false;
        this.history = history;
        this.metric = metric;
        this.range = range;
        this.zoom = zoom;
        this.compare = compare;
        this.width = width;
        return true;
    }

    void clear() { history = null; }
}
