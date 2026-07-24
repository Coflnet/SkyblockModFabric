package com.coflnet.lore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PurchaseMessageTest {
    @Test
    void parsesExactPurchaseAndClaimMessages() {
        assertEquals(
                new PurchaseMessage("Hyperion", 1_234_567_890L),
                PurchaseMessage.parse("You purchased Hyperion for 1,234,567,890 coins!"));
        assertEquals(
                new PurchaseMessage("Terminator", 900_000_000L),
                PurchaseMessage.parse("You claimed 2x Terminator from Player's auction for 900,000,000 coins."));
    }

    @Test
    void rejectsOrdinaryChatAndOversizedInput() {
        assertNull(PurchaseMessage.parse(
                "Player says You purchased Hyperion for 1,234 coins"));
        assertNull(PurchaseMessage.parse("You purchased " + "x".repeat(300) + " for 1 coins"));
    }
}
