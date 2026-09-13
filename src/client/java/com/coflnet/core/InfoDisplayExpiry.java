package com.coflnet.core;

/** Trivial expiry math for info-display content, extracted so it is unit tested in isolation. */
public final class InfoDisplayExpiry {
    private InfoDisplayExpiry() {
    }

    /** {@code expiresAtMillis <= 0} means "never expires". */
    public static boolean isExpired(long nowMillis, long expiresAtMillis) {
        return expiresAtMillis > 0 && nowMillis >= expiresAtMillis;
    }

    /** {@code ttlSeconds <= 0} means "never expires" (returns 0). */
    public static long computeExpiresAt(long nowMillis, int ttlSeconds) {
        return ttlSeconds > 0 ? nowMillis + ttlSeconds * 1000L : 0L;
    }
}
