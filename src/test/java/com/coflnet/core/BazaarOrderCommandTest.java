package com.coflnet.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BazaarOrderCommandTest {
    @Test
    void plain() {
        var p = BazaarOrderCommand.parse(new String[] {"64", "Enchanted", "Diamond"});
        assertFalse(p.search());
        assertEquals(64, p.amount());
        assertEquals("Enchanted Diamond", p.itemName());
    }

    @Test
    void searchPrefix() {
        var p = BazaarOrderCommand.parse(new String[] {"search", "10", "Iron", "Ingot"});
        assertTrue(p.search());
        assertEquals(10, p.amount());
        assertEquals("Iron Ingot", p.itemName());
    }

    @Test
    void suffixes() {
        assertEquals(1500, BazaarOrderCommand.parse(new String[] {"1.5k", "x"}).amount());
        assertEquals(2_000_000, BazaarOrderCommand.parse(new String[] {"2m", "x"}).amount());
    }

    @Test
    void missingAmount() {
        assertThrows(IllegalArgumentException.class, () -> BazaarOrderCommand.parse(new String[] {}));
        assertThrows(IllegalArgumentException.class, () -> BazaarOrderCommand.parse(new String[] {"search"}));
        assertThrows(IllegalArgumentException.class, () -> BazaarOrderCommand.parse(new String[] {"Diamond"}));
    }

    @Test
    void missingName() {
        assertThrows(IllegalArgumentException.class, () -> BazaarOrderCommand.parse(new String[] {"64"}));
        assertThrows(IllegalArgumentException.class, () -> BazaarOrderCommand.parse(new String[] {"search", "64"}));
    }

    @Test
    void expiry() {
        assertFalse(BazaarOrderCommand.isExpired(0, 5_000));
        assertFalse(BazaarOrderCommand.isExpired(10_000, 5_000));
        assertTrue(BazaarOrderCommand.isExpired(10_000, 10_001));
    }
}
