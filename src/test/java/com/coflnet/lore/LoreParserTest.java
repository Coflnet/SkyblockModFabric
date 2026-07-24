package com.coflnet.lore;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoreParserTest {
    @Test
    void parsesEveryFieldOnSharedLines() {
        LoreData data = LoreParser.parse(List.of(
                "lbin: 100 med: 200 vol: 3",
                "clean craft: 400 full craft cost: 500"));

        assertEquals(100.0, data.lbin);
        assertEquals(200.0, data.median);
        assertEquals(3.0, data.volume);
        assertEquals(400.0, data.cleanCraft);
        assertEquals(500.0, data.craftCost);
    }

    @Test
    void parsesSeparateBazaarSidesAndEachValues() {
        LoreData data = LoreParser.parse(List.of(
                "buy: 37.49k (585.8 each)",
                "sell: 33.38k (521.6 each)",
                "vol: 2.94k / 1.67k"));

        assertEquals(37_490.0, data.buy);
        assertEquals(585.8, data.buyEach);
        assertEquals(33_380.0, data.sell);
        assertEquals(521.6, data.sellEach);
        assertEquals(2_940.0, data.buyVol);
        assertEquals(1_670.0, data.sellVol);
    }

    @Test
    void suffixDoesNotConsumeTheNextLabel() {
        LoreData data = LoreParser.parse(List.of("lbin: 100 med: 200"));

        assertEquals(100.0, data.lbin);
        assertEquals(200.0, data.median);
    }
}
