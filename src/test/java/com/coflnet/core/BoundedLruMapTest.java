package com.coflnet.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedLruMapTest {

    @Test
    void staysBoundedAndEvictsLeastRecentlyUsed() {
        BoundedLruMap<String, Integer> map = new BoundedLruMap<>(3);
        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);
        assertEquals(3, map.size());

        // Adding a 4th entry evicts the least-recently-used one ("a").
        map.put("d", 4);
        assertEquals(3, map.size());
        assertFalse(map.containsKey("a"));
        assertTrue(map.containsKey("b"));
        assertTrue(map.containsKey("c"));
        assertTrue(map.containsKey("d"));
    }

    @Test
    void getRefreshesRecencyAndProtectsFromEviction() {
        BoundedLruMap<String, Integer> map = new BoundedLruMap<>(2);
        map.put("a", 1);
        map.put("b", 2);
        // Touch "a" so it becomes the most-recently-used entry.
        assertEquals(1, map.get("a"));
        // Inserting a third entry should now evict "b", not "a".
        map.put("c", 3);
        assertTrue(map.containsKey("a"));
        assertFalse(map.containsKey("b"));
        assertTrue(map.containsKey("c"));
    }

    @Test
    void removeAndClearWork() {
        BoundedLruMap<String, Integer> map = new BoundedLruMap<>(5);
        map.put("a", 1);
        map.put("b", 2);
        assertEquals(1, map.remove("a"));
        assertNull(map.get("a"));
        assertEquals(1, map.size());
        map.clear();
        assertTrue(map.isEmpty());
    }

    @Test
    void getOrDefaultReturnsFallbackForMissingKey() {
        BoundedLruMap<String, Integer> map = new BoundedLruMap<>(2);
        assertEquals(42, map.getOrDefault("missing", 42));
    }

    @Test
    void rejectsNonPositiveMaxSize() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedLruMap<String, Integer>(0));
    }
}
