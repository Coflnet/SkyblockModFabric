package com.coflnet.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InfoDisplayVisibilityTest {
    @Test void hiddenInMainMenuEvenWhenShowInGuisIsOn() {
        assertFalse(InfoDisplayVisibility.isVisible(false, false, false, true));
        assertFalse(InfoDisplayVisibility.isVisible(false, false, true, true));
    }

    @Test void visibleInWorldWithoutScreenOrInChat() {
        assertTrue(InfoDisplayVisibility.isVisible(true, false, true, false));
    }

    @Test void otherGuisFollowSetting() {
        assertTrue(InfoDisplayVisibility.isVisible(true, false, false, true));
        assertFalse(InfoDisplayVisibility.isVisible(true, false, false, false));
    }

    @Test void hiddenOnEditScreen() {
        assertFalse(InfoDisplayVisibility.isVisible(true, true, false, true));
    }
}
