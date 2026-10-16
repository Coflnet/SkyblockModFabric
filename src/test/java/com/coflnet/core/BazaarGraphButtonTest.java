package com.coflnet.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BazaarGraphButtonTest {
    private static List<String> product() {
        var names = new ArrayList<>(Collections.nCopies(54, ""));
        names.set(10, "Buy Instantly");
        names.set(12, "Sell Instantly");
        names.set(16, "§aView Graphs");
        return names;
    }

    @Test void selectsTheNamedGraphControlInsteadOfAnOrderOrFixedSlot() {
        var nativePage = new ArrayList<>(Collections.nCopies(36, ""));
        nativePage.set(10, "Buy Instantly");
        nativePage.set(11, "Sell Instantly");
        nativePage.set(33, "§aView Graphs");
        assertEquals(33, BazaarGraphButton.find(36, nativePage), "Observed live Hypixel product page");
        assertEquals(16, BazaarGraphButton.find(54, product()));
        var names = product();
        names.set(16, "");
        names.set(40, "Graphs");
        names.set(10, "Create Buy Order");
        names.set(12, "Create Sell Offer");
        assertEquals(40, BazaarGraphButton.find(54, names));
        ignoresOrdinaryItemsPartialMenusAndAmbiguousButtons();
    }

    private static void ignoresOrdinaryItemsPartialMenusAndAmbiguousButtons() {
        assertEquals(-1, BazaarGraphButton.find(27, product()));
        assertEquals(-1, BazaarGraphButton.find(54, List.of("View Graphs")));
        var names = product();
        names.set(12, "");
        assertEquals(-1, BazaarGraphButton.find(54, names));
        names = product();
        names.set(10, "");
        assertEquals(-1, BazaarGraphButton.find(54, names));
        names = product();
        names.set(16, "Buy graphs for 1,000 coins");
        assertEquals(-1, BazaarGraphButton.find(54, names));
        names = product();
        names.set(20, "Graphs");
        assertEquals(-1, BazaarGraphButton.find(54, names));
        assertEquals(-1, BazaarGraphButton.find(54, Collections.nCopies(54, "View Graphs")));
    }
}
