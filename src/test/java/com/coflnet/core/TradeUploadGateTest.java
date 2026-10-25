package com.coflnet.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class TradeUploadGateTest {
    private static final String BOOTS = "Ancient Maxor's Boots";
    private static final String COINS = "§6110M coins";

    @Test
    void everyChangedTradeStateIsUploadedOnceAndUnchangedStatesAreNot() {
        var gate = new TradeUploadGate<String>(Objects::equals, UnaryOperator.identity());

        assertTrue(gate.shouldUpload(List.of(BOOTS, ""), 0), "window opened with own boots");
        assertFalse(gate.shouldUpload(List.of(BOOTS, ""), 10), "unchanged window");
        // regression: the coins needed no price, so this state was never uploaded
        assertTrue(gate.shouldUpload(List.of(BOOTS, COINS), 20), "other player adds 110M coins");
        assertFalse(gate.shouldUpload(List.of(BOOTS, COINS), 30), "trade completes, nothing changed");
        assertEquals(List.of(BOOTS, COINS), gate.lastOffered());
        assertTrue(gate.shouldUpload(List.of(BOOTS, ""), 40), "other player removes the coins");
    }

    @Test
    void failedUploadIsRetriedAfterTheDelay() {
        var gate = new TradeUploadGate<String>(Objects::equals, UnaryOperator.identity());
        assertTrue(gate.shouldUpload(List.of(BOOTS, COINS), 0));
        gate.failed(100);

        assertFalse(gate.shouldUpload(List.of(BOOTS, COINS), 100 + TradeUploadGate.RETRY_DELAY_MS - 1));
        assertTrue(gate.isRetryDue(100 + TradeUploadGate.RETRY_DELAY_MS));
        assertTrue(gate.shouldUpload(List.of(BOOTS, COINS), 100 + TradeUploadGate.RETRY_DELAY_MS));
        assertFalse(gate.isRetryDue(Long.MAX_VALUE));
    }
}
