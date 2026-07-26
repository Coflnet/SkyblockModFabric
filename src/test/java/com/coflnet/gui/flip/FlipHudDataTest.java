package com.coflnet.gui.flip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlipHudDataTest {
    @Test
    void parsesBackendFlipPayload() {
        FlipHudData data = FlipHudData.parse("""
                {
                  "messages": [{"text": "§aFallback name"}],
                  "id": " auction-id ",
                  "auction": {
                    "itemName": "§6Aspect of the End",
                    "count": 2,
                    "startingBid": 12500000
                  },
                  "target": 17500000,
                  "worth": 19000000,
                  "finder": "SNIPER",
                  "render": "diamond"
                }
                """);

        assertEquals("auction-id", data.id());
        assertEquals("Aspect of the End", data.itemName());
        assertEquals(2, data.count());
        assertEquals(12_500_000L, data.cost());
        assertEquals(17_500_000L, data.target());
        assertEquals("SNIPER", data.finder());
        assertEquals("diamond", data.render());
    }

    @Test
    void rejectsUnsafeAuctionIdsAndCleansBackendText() {
        FlipHudData data = FlipHudData.parse("""
                {
                  "id": "auction id",
                  "auction": {"itemName": "§6Aspect §bof the End\\u0007"},
                  "finder": "§cSNIPER"
                }
                """);

        assertEquals("", data.id());
        assertEquals("Aspect of the End", data.itemName());
        assertEquals("SNIPER", data.finder());
    }

    @Test
    void usesSafeFallbacksForMissingAuctionFields() {
        FlipHudData data = FlipHudData.parse("""
                {
                  "messages": [
                    {"text": "§6Withered Hyperion"},
                    {"text": "§7Extra details"}
                  ],
                  "cost": 800000000,
                  "worth": 999999999
                }
                """);

        assertEquals("Withered Hyperion Extra details", data.itemName());
        assertEquals(1, data.count());
        assertEquals(800_000_000L, data.cost());
        assertEquals(0L, data.target());
    }

    @Test
    void doesNotUseWorthOrNegativeTargetsAsPrices() {
        FlipHudData data = FlipHudData.parse("""
                {
                  "worth": 999999999,
                  "target": -1,
                  "auction": {"startingBid": 12500000}
                }
                """);

        assertEquals(12_500_000L, data.cost());
        assertEquals(0L, data.target());
    }

    @Test
    void rejectsUnsafeNumericValues() {
        FlipHudData data = FlipHudData.parse("""
                {
                  "target": 1.5,
                  "auction": {
                    "startingBid": 999999999999999999999999999999,
                    "count": 2.5
                  }
                }
                """);

        assertEquals(0L, data.cost());
        assertEquals(0L, data.target());
        assertEquals(1, data.count());
    }

    @Test
    void boundsPayloadAndDisplayFields() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> FlipHudData.parse("x".repeat(FlipHudData.MAX_PAYLOAD_LENGTH + 1)));
        String stackOverflowDepth = "[".repeat(20_000)
                + "0"
                + "]".repeat(20_000);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> FlipHudData.parse(stackOverflowDepth));

        FlipHudData data = FlipHudData.parse("""
                {
                  "id": "%s",
                  "auction": {"itemName": "%s"},
                  "finder": "%s",
                  "render": "%s"
                }
                """.formatted("i".repeat(100), "n".repeat(300), "f".repeat(100), "r".repeat(300)));

        assertEquals("", data.id());
        assertEquals(160, data.itemName().length());
        assertEquals(32, data.finder().length());
        assertEquals(128, data.render().length());
    }

    @Test
    void normalizesInvalidJsonFailures() {
        assertThrowsExactly(IllegalArgumentException.class,
                () -> FlipHudData.parse("[]"));
        assertThrowsExactly(IllegalArgumentException.class,
                () -> FlipHudData.parse("null"));
        assertThrowsExactly(IllegalArgumentException.class,
                () -> FlipHudData.parse("\"flip\""));
        assertThrowsExactly(IllegalArgumentException.class,
                () -> FlipHudData.parse("{\"auction\":}"));
    }

    @Test
    void promotesRoundedPriceSuffixes() {
        assertEquals("999.9k", FlipHudData.formatCoins(999_949L));
        assertEquals("1.0m", FlipHudData.formatCoins(999_950L));
        assertEquals("999.9m", FlipHudData.formatCoins(999_949_999L));
        assertEquals("1.0b", FlipHudData.formatCoins(999_950_000L));
    }

    @Test
    void parsesOnlyExactViewAuctionCommands() {
        assertEquals("auction-id", FlipHudData.auctionIdFromCommand("viewauction auction-id"));
        assertEquals("auction-id", FlipHudData.auctionIdFromCommand("VIEWAUCTION   auction-id"));
        assertEquals("", FlipHudData.auctionIdFromCommand("cofl viewauction auction-id"));
        assertEquals("", FlipHudData.auctionIdFromCommand("viewauction auction-id extra"));
        assertEquals("", FlipHudData.auctionIdFromCommand("viewauction auction\u007fid"));
    }

    @Test
    void recognizesBackendSessionCommands() {
        assertTrue(FlipHudData.changesBackendSession("cofl stop"));
        assertTrue(FlipHudData.changesBackendSession("COFL reset"));
        assertTrue(FlipHudData.changesBackendSession("cl start"));
        assertTrue(FlipHudData.changesBackendSession("CL reset"));
        assertFalse(FlipHudData.changesBackendSession("cofl connect destination"));
        assertFalse(FlipHudData.changesBackendSession("cofl status"));
        assertFalse(FlipHudData.changesBackendSession("stop"));
    }
}
