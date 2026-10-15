package com.coflnet.core;

import com.coflnet.core.HighlightStaleness.Key;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HighlightStalenessTest {
    @Test void sameTitleAndNameIsFresh() {
        assertFalse(HighlightStaleness.isStale(new Key("Bazaar", "Diamond"), "Bazaar", "Diamond"));
    }

    @Test void differentTitleOrNameIsStale() {
        assertTrue(HighlightStaleness.isStale(new Key("Bazaar", "Diamond"), "Chest", "Diamond"));
        assertTrue(HighlightStaleness.isStale(new Key("Bazaar", "Diamond"), "Bazaar", "Coal"));
    }

    @Test void nullsAreSafe() {
        assertTrue(HighlightStaleness.isStale(null, "Bazaar", "Diamond"));
        assertFalse(HighlightStaleness.isStale(new Key(null, null), null, null));
        assertTrue(HighlightStaleness.isStale(new Key("a", "b"), null, "b"));
    }
}
