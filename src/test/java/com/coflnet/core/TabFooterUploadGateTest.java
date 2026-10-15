package com.coflnet.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TabFooterUploadGateTest {
    @Test void uploadsFirstThenOnlyOnChangeAfterDebounce() {
        var gate = new TabFooterUploadGate(2000);
        assertTrue(gate.shouldUpload("a", 0));
        assertFalse(gate.shouldUpload("a", 5000));   // unchanged
        assertFalse(gate.shouldUpload("b", 1000));   // changed but inside the debounce window
        assertTrue(gate.shouldUpload("b", 2000));    // retried by a later packet
        assertFalse(gate.shouldUpload("b", 9000));
    }

    @Test void resetAllowsUploadAgain() {
        var gate = new TabFooterUploadGate(2000);
        gate.shouldUpload("a", 0);
        gate.reset();
        assertTrue(gate.shouldUpload("a", 1));
    }
}
