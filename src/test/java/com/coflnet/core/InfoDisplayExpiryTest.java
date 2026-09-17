package com.coflnet.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InfoDisplayExpiryTest {
    @Test void zeroExpiresAtNeverExpires() {
        assertFalse(InfoDisplayExpiry.isExpired(Long.MAX_VALUE, 0));
        assertFalse(InfoDisplayExpiry.isExpired(0, 0));
    }

    @Test void expiresOnceNowReachesExpiresAt() {
        assertFalse(InfoDisplayExpiry.isExpired(999, 1000));
        assertTrue(InfoDisplayExpiry.isExpired(1000, 1000));
        assertTrue(InfoDisplayExpiry.isExpired(1001, 1000));
    }

    @Test void computeExpiresAtIsPermanentForNonPositiveTtl() {
        assertEquals(0L, InfoDisplayExpiry.computeExpiresAt(5000, 0));
        assertEquals(0L, InfoDisplayExpiry.computeExpiresAt(5000, -1));
    }

    @Test void computeExpiresAtAddsSecondsAsMillis() {
        assertEquals(5000L + 30_000L, InfoDisplayExpiry.computeExpiresAt(5000, 30));
    }
}
