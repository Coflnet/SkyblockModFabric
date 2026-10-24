package com.coflnet.core;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BazaarGraphTargetTest {
    @Test void resolvesHoveredItemsAndMenuNamesWithoutPickingSimilarProducts() {
        var coal = BazaarGraphTarget.from("COAL", "§fCoal", Map.of());
        assertEquals("Coal", coal.query());
        assertTrue(coal.matches(BazaarGraphTarget.from("", "Coal", Map.of())));
        assertFalse(coal.matches(BazaarGraphTarget.from("ENCHANTED_COAL", "Enchanted Coal", Map.of())));
        assertEquals("Coal", BazaarGraphTarget.from("COAL", "§aBUY §fCoal", Map.of()).query());
    }

    @Test void resolvesBookLevelsAndRejectsAmbiguousBooksAndMenuControls() {
        var rend = BazaarGraphTarget.from("ENCHANTED_BOOK", "Enchanted Book", Map.of("rend", 4));
        assertEquals("Rend IV", rend.query());
        assertEquals("ENCHANTMENT_REND_4", rend.id());
        assertTrue(rend.matches(BazaarGraphTarget.from("UNKNOWN_MAP", "Rend IV", Map.of())));
        assertFalse(rend.matches(BazaarGraphTarget.from("UNKNOWN_MAP", "Rend V", Map.of())));
        assertEquals("Soul Eater V", BazaarGraphTarget.from("ENCHANTED_BOOK", "Enchanted Book", Map.of("ultimate_soul_eater", 5)).query());
        assertNull(BazaarGraphTarget.from("ENCHANTED_BOOK", "Enchanted Book", Map.of("rend", 4, "sharpness", 5)));
        assertNull(BazaarGraphTarget.from("", "View Graphs", Map.of()));
        assertNull(BazaarGraphTarget.from("", "/bad", Map.of()));
    }
}
