package com.coflnet.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GhostTextTest {
    @Test void prefixGivesSuffix() {
        var r = GhostText.compute("/bz Ench", "/bz Enchanted Diamond");
        assertEquals("anted Diamond", r.suggestionSuffix());
        assertEquals(-1, r.mismatchIndex());
    }

    @Test void divergenceGivesIndexAndNoSuffix() {
        var r = GhostText.compute("/bz Enx", "/bz Enchanted Diamond");
        assertEquals("", r.suggestionSuffix());
        assertEquals(6, r.mismatchIndex());
    }

    @Test void typedLongerThanExpectedMismatchesAtEnd() {
        assertEquals(3, GhostText.compute("/bzz", "/bz").mismatchIndex());
    }

    @Test void emptyOrMissingIsIgnored() {
        assertEquals(-1, GhostText.compute("", "/bz x").mismatchIndex());
        assertEquals("", GhostText.compute("", "/bz x").suggestionSuffix());
        assertEquals(-1, GhostText.compute("/bz", null).mismatchIndex());
    }

    @Test void exactMatchHasNothingLeft() {
        var r = GhostText.compute("/bz x", "/bz x");
        assertEquals("", r.suggestionSuffix());
        assertEquals(-1, r.mismatchIndex());
    }
}
