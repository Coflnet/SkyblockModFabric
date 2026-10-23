package com.coflnet.core;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BazaarGraphCatalogTest {
    @Test void acceptsOnlyBazaarIdsAndExactUnambiguousContextualNames() {
        var catalog = BazaarGraphCatalog.parse("""
                [{"tag":"COAL","name":"Coal","flags":"BAZAAR"},
                 {"tag":"ENCHANTED_COAL","name":"Enchanted Coal","flags":"BAZAAR, GLOWING"},
                 {"tag":"HYPERION","name":"Hyperion","flags":"AUCTION"},
                 {"tag":"NUMERIC","name":"Numeric","flags":1},
                 {"tag":"NOT_BAZAAR","name":"Coal","flags":4}]
                """);
        assertEquals("COAL", catalog.resolve(new BazaarGraphTarget("COAL", "Coal")).id());
        assertEquals("COAL", catalog.resolve(new BazaarGraphTarget("", "Coal")).id());
        assertEquals("NUMERIC", catalog.resolve(new BazaarGraphTarget("NUMERIC", "Numeric")).id());
        assertNull(catalog.resolve(new BazaarGraphTarget("HYPERION", "Hyperion")));
        assertNull(catalog.resolve(new BazaarGraphTarget("HYPERION", "Coal")), "Never replace a known non-Bazaar ID by its name");
        assertNull(catalog.resolve(new BazaarGraphTarget("", "Coa")));
        assertNull(catalog.resolve(null));
    }

    @Test void resolvesEnchantmentMenuNamesAndKeepsLevelsSeparate() {
        var catalog = BazaarGraphCatalog.parse("""
                [{"tag":"ENCHANTMENT_ULTIMATE_REND_4","name":"rend 4 enchant","flags":"BAZAAR"},
                 {"tag":"ENCHANTMENT_ULTIMATE_REND_5","name":"rend 5 enchant","flags":"BAZAAR"}]
                """);
        var menu = BazaarGraphTarget.from("UNKNOWN_MAP", "Rend IV", Map.of());
        assertEquals("ENCHANTMENT_ULTIMATE_REND_4", catalog.resolve(menu).id());
        assertNull(catalog.resolve(BazaarGraphTarget.from("UNKNOWN_MAP", "Rend III", Map.of())));
        assertEquals("ENCHANTMENT_ULTIMATE_REND_5", catalog.resolve(BazaarGraphTarget.from("ENCHANTED_BOOK", "Enchanted Book", Map.of("ultimate_rend",5))).id());
    }
}
