package com.coflnet.core;

import java.util.List;
import java.util.Set;

/** Selects only the native graph control on a product page, never a buy/sell control. */
public final class BazaarGraphButton {
    private static final Set<String> BUY = Set.of("Buy Instantly", "Instant Buy", "Create Buy Order");
    private static final Set<String> SELL = Set.of("Sell Instantly", "Instant Sell", "Create Sell Offer", "Create Sell Order");

    private BazaarGraphButton() {
    }

    public static int find(int containerSize, List<String> names) {
        if ((containerSize != 36 && containerSize != 54) || names.size() != containerSize) return -1;
        boolean buy = false, sell = false;
        int graph = -1;
        for (int slot = 0; slot < names.size(); slot++) {
            String name = BazaarChart.clean(names.get(slot)).trim();
            buy |= BUY.contains(name);
            sell |= SELL.contains(name);
            if (name.equals("View Graphs") || name.equals("Graphs")) {
                if (graph >= 0) return -1;
                graph = slot;
            }
        }
        return buy && sell ? graph : -1;
    }
}
