package com.coflnet.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NumberParserTest {
    @Test void parsesHypixelNumberForms() {
        assertEquals(200_000L, NumberParser.parseCoinNumber("200k"));
        assertEquals(1_234L, NumberParser.parseCoinNumber("1,234"));
        assertEquals(1_500_000_000L, NumberParser.parseAmount("1.5b"));
        assertThrows(NumberFormatException.class, () -> NumberParser.parseAmount("12 coins"));
    }
}
