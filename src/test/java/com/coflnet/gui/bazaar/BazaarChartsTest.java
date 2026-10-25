package com.coflnet.gui.bazaar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarChartsTest {
    @Test
    void graphLockFollowsThePhysicalKeyOrButtonThatOpenedIt() {
        // A scancode binding still opens through a key event, so its keysym is polled like a keysym binding.
        assertFalse(BazaarCharts.released(71, -1, key -> key == 71, button -> false));
        assertTrue(BazaarCharts.released(71, -1, key -> false, button -> false));
        assertFalse(BazaarCharts.released(-1, 3, key -> false, button -> button == 3));
        assertTrue(BazaarCharts.released(-1, 3, key -> false, button -> false));
        assertFalse(BazaarCharts.released(-1, -1, key -> false, button -> false));
    }
}
