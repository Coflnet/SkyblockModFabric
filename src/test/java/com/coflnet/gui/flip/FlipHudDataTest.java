package com.coflnet.gui.flip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlipHudDataTest {
    @Test
    void parsesBackendFlipPayload() {
        FlipHudData data = FlipHudData.parse("""
                {
                  "messages": [{"text": "§aFallback name"}],
                  "id": "auction-id",
                  "worth": 19000000,
                  "auction": {
                    "itemName": "Aspect of the End",
                    "count": 2,
                    "startingBid": 12500000
                  },
                  "target": 17500000,
                  "finder": "SNIPER",
                  "render": "diamond"
                }
                """);

        assertEquals("auction-id", data.id());
        assertEquals("Aspect of the End", data.itemName());
        assertEquals(2, data.count());
        assertEquals(12_500_000L, data.cost());
        assertEquals(17_500_000L, data.target());
        assertEquals(19_000_000L, data.worth());
        assertEquals("SNIPER", data.finder());
        assertEquals("diamond", data.render());
    }

    @Test
    void usesSafeFallbacksForMissingAuctionFields() {
        FlipHudData data = FlipHudData.parse("""
                {
                  "messages": [
                    {"text": "§6Withered Hyperion"},
                    {"text": "§7Extra details"}
                  ],
                  "cost": 800000000
                }
                """);

        assertEquals("Withered Hyperion Extra details", data.itemName());
        assertEquals(1, data.count());
        assertEquals(800_000_000L, data.cost());
    }
}
