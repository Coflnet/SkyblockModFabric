package com.coflnet.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonNestingTest {
    @Test
    void ignoresStructuralCharactersInsideStrings() {
        assertTrue(JsonNesting.isWithinLimit(
                "{\"value\":\"[{\\\"nested\\\":true}]\"}",
                2));
    }

    @Test
    void rejectsExcessiveOrMalformedNesting() {
        assertTrue(JsonNesting.isWithinLimit(
                "[".repeat(64) + "0" + "]".repeat(64),
                64));
        assertFalse(JsonNesting.isWithinLimit(
                "[".repeat(65) + "0" + "]".repeat(65),
                64));
        assertFalse(JsonNesting.isWithinLimit("}", 64));
        assertFalse(JsonNesting.isWithinLimit("{\"value\":\"unterminated}", 64));
        assertFalse(JsonNesting.isWithinLimit(null, 64));
        assertFalse(JsonNesting.isWithinLimit("{}", 0));
    }
}
