package com.coflnet.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScoreboardParserTest {
    @Test void extractsPurseAndModernAreaGlyph() {
        var values = ScoreboardParser.getRelevantLines(new String[]{
                "SKYBLOCK", "Purse: 12,345,678", "  The Garden", "www.hypixel.net"});
        assertEquals("Purse: 12,345,678", values.left());
        assertEquals("  The Garden", values.right());
    }

    @Test void recognizesFormattedHypixelFooter() {
        assertTrue(ScoreboardParser.containsHypixelFooter(
                new String[]{"§ewww.§6hypixel§e.net "}));
        assertFalse(ScoreboardParser.containsHypixelFooter(new String[]{"www.hypixel.net§x"}));
        assertFalse(ScoreboardParser.containsHypixelFooter(new String[]{"www.example.net"}));
    }
}
