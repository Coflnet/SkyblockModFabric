package com.coflnet.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/** Compressed, on-demand public history. Graph types share the same recovered samples. */
public final class BazaarHistoryClient {
    public enum Range {
        HOUR(Duration.ofHours(1)), DAY(Duration.ofDays(1)), WEEK(Duration.ofDays(7)), MONTH(Duration.ofDays(31));
        public final Duration duration;
        Range(Duration duration) { this.duration = duration; }
        String path(Instant end) {
            if (this != MONTH) return "/history/" + name().toLowerCase(java.util.Locale.ROOT);
            end = end.truncatedTo(ChronoUnit.HOURS);
            return "/history?start=" + end.minus(duration) + "&end=" + end;
        }
    }
    private record Response(String body, int cacheSeconds) { }
    private static final class HttpFailure extends IOException {
        final int status;
        HttpFailure(int status) { super("HTTP " + status); this.status = status; }
    }
    static final class Cached {
        volatile long expires = Long.MAX_VALUE;
        volatile BazaarHistory history;
        volatile long updated;
        volatile int failures;
        final CompletableFuture<BazaarHistory> result;

        Cached(Cached previous, CompletableFuture<BazaarHistory> request) {
            if (previous != null) {
                history = previous.history;
                updated = previous.updated;
                failures = previous.failures;
            }
            result = request.handle((value, error) -> {
                if (error != null) {
                    failures++;
                    throw new CompletionException(error);
                }
                history = value;
                updated = System.nanoTime();
                failures = 0;
                return value;
            });
        }

        boolean stale(long now) { return history != null && now - updated > Duration.ofHours(1).toNanos(); }
        boolean warn(long now) { return failures >= 2 && stale(now); }
    }
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final LinkedHashMap<String, Cached> CACHE = new LinkedHashMap<>(32, 0.75f, true);
    private static volatile BazaarGraphCatalog catalog;
    private static Path catalogFile;
    private static long nextCatalogAttempt;
    private static volatile Instant retryAt = Instant.MIN;

    private BazaarHistoryClient() { }

    /** Load a saved product catalog immediately in the background; refresh at most hourly. */
    public static synchronized void warmup(Path directory) {
        if (catalogFile != null) return;
        catalogFile = directory.resolve("bazaar-graph-items.json");
        CompletableFuture.runAsync(() -> {
            try {
                if (Files.isRegularFile(catalogFile) && Files.size(catalogFile) < 4 * 1024 * 1024) {
                    catalog = BazaarGraphCatalog.parse(Files.readString(catalogFile));
                    long modified = Files.getLastModifiedTime(catalogFile).toMillis();
                    if (System.currentTimeMillis() - modified < 3_600_000) {
                        synchronized (BazaarHistoryClient.class) { nextCatalogAttempt = modified + 3_600_000; }
                        return;
                    }
                }
            } catch (IOException | RuntimeException ignored) { }
            refreshCatalog();
        });
    }

    private static synchronized void refreshCatalog() {
        long now = System.currentTimeMillis();
        if (now < nextCatalogAttempt) return;
        nextCatalogAttempt = now + 60_000;
        get("/items").thenAcceptAsync(response -> {
            var parsed = BazaarGraphCatalog.parse(response.body());
            if (parsed.ids().isEmpty()) return;
            catalog = parsed;
            synchronized (BazaarHistoryClient.class) { nextCatalogAttempt = System.currentTimeMillis() + 3_600_000; }
            try {
                Files.createDirectories(catalogFile.getParent());
                Path temp = catalogFile.resolveSibling(catalogFile.getFileName() + ".tmp");
                Files.writeString(temp, response.body());
                Files.move(temp, catalogFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) { }
        }).exceptionally(error -> null);
    }

    /** Constant-time local lookup; an unresolved G press never opens a delayed screen. */
    public static BazaarGraphTarget resolve(BazaarGraphTarget candidate) {
        if (candidate == null) return null;
        refreshCatalog();
        var current = catalog;
        return current == null ? null : current.resolve(candidate);
    }

    private static String key(BazaarGraphTarget target, Range range) { return target.id() + ":" + range; }

    public static synchronized BazaarHistory cached(BazaarGraphTarget target, Range range) {
        var entry = CACHE.get(key(target, range));
        return entry == null ? null : entry.history;
    }

    public static synchronized boolean stale(BazaarGraphTarget target, Range range) {
        var entry = CACHE.get(key(target, range));
        return entry != null && entry.stale(System.nanoTime());
    }

    public static synchronized boolean refreshFailed(BazaarGraphTarget target, Range range) {
        var entry = CACHE.get(key(target, range));
        return entry != null && entry.warn(System.nanoTime());
    }

    public static boolean retryable(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
        if (error instanceof HttpFailure failure) return failure.status >= 500;
        return error instanceof IOException || error instanceof java.util.concurrent.TimeoutException;
    }

    public static synchronized CompletableFuture<BazaarHistory> load(BazaarGraphTarget target, Range range) {
        String key = key(target, range);
        var previous = CACHE.get(key);
        if (previous != null && previous.expires > System.nanoTime() && !previous.result.isCompletedExceptionally()) return previous.result;
        var current = catalog;
        if (current == null || current.resolve(target) == null) return CompletableFuture.failedFuture(new IOException("Not a Bazaar product"));
        Instant end = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        String prefix = "/bazaar/" + target.id();
        var response = get(prefix + range.path(end));
        var entry = new Cached(previous, response.thenComposeAsync(value -> BazaarHistoryRecovery.recover(
                BazaarHistory.parse(value.body()), range, end,
                (from, to) -> recoveryGet(prefix + "/history?start=" + from + "&end=" + to))));
        if (previous == null && CACHE.size() >= 32) CACHE.remove(CACHE.keySet().iterator().next());
        CACHE.put(key, entry);
        response.thenAccept(value -> entry.result.whenComplete((history, error) -> {
            // Recovered recent tails must refresh even when the older month response is cached for an hour.
            int seconds = Math.min(value.cacheSeconds(), 300);
            if (history != null && BazaarHistoryRecovery.incomplete(history, range, end)) seconds = Math.min(seconds, 60);
            entry.expires = System.nanoTime() + seconds * 1_000_000_000L;
        }));
        return entry.result;
    }

    private static CompletableFuture<BazaarHistory> recoveryGet(String path) {
        return get(path).thenApplyAsync(value -> BazaarHistory.parse(value.body())).exceptionally(error -> {
            Throwable cause = error;
            while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof HttpFailure failure && (failure.status == 401 || failure.status == 403 || failure.status == 429))
                throw new CompletionException(cause);
            // Keep genuine samples if a recovery interval is unavailable; the screen reports partial coverage.
            return new BazaarHistory(java.util.List.of());
        });
    }

    static long retryDelayMillis(String header, Instant now) {
        try {
            return Math.max(0, Math.multiplyExact(Long.parseLong(header), 1000));
        } catch (RuntimeException ignored) {
            try {
                var until = java.time.ZonedDateTime.parse(header, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                return Math.max(0, Duration.between(now, until).toMillis());
            } catch (RuntimeException invalid) { return 60_000; }
        }
    }

    private static synchronized void pauseRequests(String header) {
        Instant now = Instant.now();
        Instant until = now.plusMillis(retryDelayMillis(header, now));
        if (until.isAfter(retryAt)) retryAt = until;
    }

    private static CompletableFuture<Response> get(String path) {
        if (Instant.now().isBefore(retryAt)) return CompletableFuture.failedFuture(new HttpFailure(429));
        var request = HttpRequest.newBuilder(URI.create("https://sky.coflnet.com/api" + path))
                .timeout(Duration.ofSeconds(10)).header("Accept", "application/json")
                .header("Accept-Encoding", "gzip").header("User-Agent", "SkyCofl/2.0.0").GET().build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).orTimeout(12, TimeUnit.SECONDS).thenApplyAsync(response -> {
            if (response.statusCode() == 429) pauseRequests(response.headers().firstValue("Retry-After").orElse(null));
            if (response.statusCode() != 200) throw new CompletionException(new HttpFailure(response.statusCode()));
            try {
                byte[] bytes = response.body();
                if (response.headers().firstValue("Content-Encoding").orElse("").equalsIgnoreCase("gzip")) {
                    try (var gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) { bytes = gzip.readNBytes(4 * 1024 * 1024 + 1); }
                }
                if (bytes.length > 4 * 1024 * 1024) throw new IOException("History response too large");
                int seconds = 300;
                var match = java.util.regex.Pattern.compile("max-age=(\\d+)").matcher(response.headers().firstValue("Cache-Control").orElse(""));
                if (match.find()) seconds = Math.clamp(Long.parseLong(match.group(1)), 0, 3600);
                return new Response(new String(bytes, StandardCharsets.UTF_8), seconds);
            } catch (IOException exception) { throw new CompletionException(exception); }
        });
    }
}
