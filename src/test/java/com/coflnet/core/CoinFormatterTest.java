package com.coflnet.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CoinFormatterTest {
    @Test void formatsTradeTotalsWithStableUsDecimals() {
        assertEquals("999", CoinFormatter.format(999));
        assertEquals("12.5K", CoinFormatter.format(12_500));
        assertEquals("611.8M", CoinFormatter.format(611_800_000));
        assertEquals("1.5B", CoinFormatter.format(1_500_000_000));
    }
}
