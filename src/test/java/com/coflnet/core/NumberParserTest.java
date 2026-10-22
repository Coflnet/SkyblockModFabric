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

    @Test void scalesDecimalAmountsWithoutBinaryRoundingLoss() {
        assertEquals(8_200_000L, NumberParser.parseAmount("8.2m"));
        assertEquals(4_100_000L, NumberParser.parseCoinNumber("4.1m"));
        assertEquals(1_100L, NumberParser.parseAmount("1.1k"));
        assertEquals(1L, NumberParser.parseAmount("1.9"));
        assertEquals(Long.MAX_VALUE, NumberParser.parseCoinNumber("99999999999999999999"));
    }
}
