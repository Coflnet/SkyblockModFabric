package com.coflnet.core;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HighlightColorTest {
    @Test void parsesHexWithAlpha() {
        assertEquals(OptionalInt.of(0xFF00FF00), HighlightColor.parse("00FF00"));
        assertEquals(OptionalInt.of(0xFFAABBCC), HighlightColor.parse("#aabbcc"));
        assertEquals(OptionalInt.of(0xFF123456), HighlightColor.parse(" 123456 "));
    }

    @Test void malformedIsEmpty() {
        assertEquals(OptionalInt.empty(), HighlightColor.parse(null));
        assertEquals(OptionalInt.empty(), HighlightColor.parse(""));
        assertEquals(OptionalInt.empty(), HighlightColor.parse("#"));
        assertEquals(OptionalInt.empty(), HighlightColor.parse("zzzzzz"));
        assertEquals(OptionalInt.empty(), HighlightColor.parse("123456789"));
    }
}
