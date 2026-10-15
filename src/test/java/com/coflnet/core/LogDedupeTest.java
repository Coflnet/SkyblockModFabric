package com.coflnet.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LogDedupeTest {
    @Test
    void sameMessageLogsOnce() {
        var d = new LogDedupe(4);
        String m = "Invalid line index: 22 for tooltip: Started 6.1m ago";
        assertTrue(d.shouldLog(m));
        for (int i = 0; i < 279; i++) assertFalse(d.shouldLog(m));
        assertTrue(d.shouldLog("Invalid line index: 22 for tooltip: Started 6.2m ago"));
    }
}
