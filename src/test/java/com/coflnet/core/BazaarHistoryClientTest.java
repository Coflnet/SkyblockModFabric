package com.coflnet.core;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import static org.junit.jupiter.api.Assertions.*;

class BazaarHistoryClientTest {
    private static BazaarHistory history(int price) {
        return new BazaarHistory(List.of(new BazaarHistory.Sample(Instant.EPOCH, price, 10, 100, 200)));
    }

    @Test void retainsLastSuccessfulGraphWhileRefreshingAndAfterBothAttemptsFail() {
        var history = history(12);
        var saved = new BazaarHistoryClient.Cached(null, CompletableFuture.completedFuture(history));
        var pending = new CompletableFuture<BazaarHistory>();
        var refresh = new BazaarHistoryClient.Cached(saved, pending);
        assertSame(history, refresh.history);
        assertFalse(refresh.result.isDone());
        pending.completeExceptionally(new IOException("Offline"));
        assertThrows(CompletionException.class, refresh.result::join);
        var retry = new BazaarHistoryClient.Cached(refresh, CompletableFuture.failedFuture(new IOException("Still offline")));
        assertThrows(CompletionException.class, retry.result::join);
        assertSame(history, retry.history);
        assertEquals(saved.updated, retry.updated);
        assertEquals(2, retry.failures);
    }

    @Test void warnsOnlyForDataOverOneHourOldAfterTwoFailedRefreshes() {
        var saved = new BazaarHistoryClient.Cached(null, CompletableFuture.completedFuture(history(12)));
        long hour = saved.updated + Duration.ofHours(1).toNanos();
        var refresh = new BazaarHistoryClient.Cached(saved, CompletableFuture.failedFuture(new IOException("Offline")));
        assertFalse(refresh.warn(hour + 1), "One failed attempt must stay quiet even for old data");
        var retry = new BazaarHistoryClient.Cached(refresh, CompletableFuture.failedFuture(new IOException("Still offline")));
        assertFalse(retry.warn(saved.updated + Duration.ofSeconds(10).toNanos()));
        assertFalse(retry.warn(saved.updated + Duration.ofMinutes(10).toNanos()));
        assertFalse(retry.warn(hour));
        assertTrue(retry.warn(hour + 1));
        var cold = new BazaarHistoryClient.Cached(null, CompletableFuture.failedFuture(new IOException("Offline")));
        var coldRetry = new BazaarHistoryClient.Cached(cold, CompletableFuture.failedFuture(new IOException("Offline")));
        assertFalse(coldRetry.warn(hour + 1), "No cache uses the existing unavailable state");
    }

    @Test void rateLimitDelayHonorsSecondsAndHttpDatesWithABoundedFallback() {
        var now = Instant.parse("2026-10-24T15:00:00Z");
        assertEquals(120_000, BazaarHistoryClient.retryDelayMillis("120", now));
        assertEquals(300_000, BazaarHistoryClient.retryDelayMillis("Sat, 24 Oct 2026 15:05:00 GMT", now));
        assertEquals(0, BazaarHistoryClient.retryDelayMillis("Sat, 24 Oct 2026 14:59:00 GMT", now));
        assertEquals(60_000, BazaarHistoryClient.retryDelayMillis(null, now));
        assertEquals(60_000, BazaarHistoryClient.retryDelayMillis("invalid", now));
    }

    @Test void unexpiredReloadResolvesToTheInstanceAlreadyShownFromTheCache() {
        // The history screen skips rebuilding its plot when the reload returns the cached instance.
        var saved = new BazaarHistoryClient.Cached(null, CompletableFuture.completedFuture(history(12)));
        assertSame(saved.history, saved.result.join());
    }

    @Test void successfulRefreshReplacesOldDataAndClearsFailures() {
        var saved = new BazaarHistoryClient.Cached(null, CompletableFuture.completedFuture(history(12)));
        var failed = new BazaarHistoryClient.Cached(saved, CompletableFuture.failedFuture(new IOException("Offline")));
        var replacement = history(14);
        var recovered = new BazaarHistoryClient.Cached(failed, CompletableFuture.completedFuture(replacement));
        assertSame(replacement, recovered.result.join());
        assertSame(replacement, recovered.history);
        assertEquals(0, recovered.failures);
        assertFalse(recovered.warn(recovered.updated + Duration.ofHours(2).toNanos()));
        assertTrue(BazaarHistoryClient.retryable(new CompletionException(new IOException("Offline"))));
        assertTrue(BazaarHistoryClient.retryable(new TimeoutException()));
        assertFalse(BazaarHistoryClient.retryable(new IllegalArgumentException("Malformed data")));
    }
}
