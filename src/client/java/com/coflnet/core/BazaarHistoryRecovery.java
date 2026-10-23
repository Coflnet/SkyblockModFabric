package com.coflnet.core;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/** Repairs sparse public histories without inventing samples or downloading fine data for an entire month. */
public final class BazaarHistoryRecovery {
    static final int MAX_REQUESTS = 20;
    static final int PARALLEL_REQUESTS = 3;
    private static final Duration TAIL_TOLERANCE = Duration.ofMinutes(5);

    @FunctionalInterface
    public interface Fetcher {
        CompletableFuture<BazaarHistory> fetch(Instant from, Instant to);
    }

    record Interval(Instant from, Instant to) { }

    private BazaarHistoryRecovery() { }

    public static CompletableFuture<BazaarHistory> recover(BazaarHistory initial, BazaarHistoryClient.Range range,
                                                          Instant end, Fetcher fetcher) {
        var state = new State(range, end, fetcher);
        state.merge(initial);
        return state.pass(Math.max(1, range.ordinal())).thenApply(ignored -> state.history());
    }

    public static boolean incomplete(BazaarHistory history, BazaarHistoryClient.Range range, Instant end) {
        return !missing(history, range, end).isEmpty();
    }

    static List<Interval> missing(BazaarHistory history, BazaarHistoryClient.Range range, Instant end) {
        Instant from = end.minus(range.duration);
        var samples = history.samples().stream()
                .filter(sample -> !sample.time().isBefore(from) && !sample.time().isAfter(end)).toList();
        if (samples.isEmpty()) return List.of(new Interval(from, end));
        Duration tolerance = gapTolerance(range);
        var gaps = new ArrayList<Interval>();
        Instant first = samples.getFirst().time();
        if (Duration.between(from, first).compareTo(tolerance) > 0) gaps.add(new Interval(from, first));
        for (int i = 1; i < samples.size(); i++) {
            Instant left = samples.get(i - 1).time(), right = samples.get(i).time();
            if (Duration.between(left, right).compareTo(tolerance) > 0) gaps.add(new Interval(left, right));
        }
        Instant last = samples.getLast().time();
        if (Duration.between(last, end).compareTo(TAIL_TOLERANCE) > 0) gaps.add(new Interval(last, end));
        return gaps;
    }

    public static Duration gapTolerance(BazaarHistoryClient.Range range) {
        return switch (range) {
            case HOUR -> Duration.ofMinutes(2);
            case DAY -> Duration.ofMinutes(15);
            case WEEK -> Duration.ofHours(3);
            case MONTH -> Duration.ofHours(36);
        };
    }

    private static Duration maximumWindow(int level) {
        // Stay below SkyBazaar's one-hour / one-day / seven-day storage boundaries,
        // even after the public API rounds custom dates to whole minutes.
        return switch (level) {
            case 3 -> Duration.ofDays(6);
            case 2 -> Duration.ofHours(22);
            case 0 -> Duration.ofMinutes(25);
            default -> Duration.ofMinutes(50);
        };
    }

    private static final class State {
        final BazaarHistoryClient.Range range;
        final Instant from, end;
        final Fetcher fetcher;
        final TreeMap<Instant, BazaarHistory.Sample> samples = new TreeMap<>();
        int remaining = MAX_REQUESTS;

        State(BazaarHistoryClient.Range range, Instant end, Fetcher fetcher) {
            this.range = range;
            this.from = end.minus(range.duration);
            this.end = end;
            this.fetcher = fetcher;
        }

        BazaarHistory history() { return new BazaarHistory(new ArrayList<>(samples.values()), end); }

        void merge(BazaarHistory history) {
            for (var sample : history.samples()) {
                if (!sample.time().isBefore(from) && !sample.time().isAfter(end)) samples.merge(sample.time(), sample,
                        (old, fresh) -> new BazaarHistory.Sample(fresh.time(), fresh.buy(), fresh.sell(),
                                fresh.buyVolume(), fresh.sellVolume(),
                                Double.isFinite(fresh.buyMovingWeek()) ? fresh.buyMovingWeek() : old.buyMovingWeek(),
                                Double.isFinite(fresh.sellMovingWeek()) ? fresh.sellMovingWeek() : old.sellMovingWeek()));
            }
        }

        CompletableFuture<Void> pass(int level) {
            if (level < 0 || remaining == 0) return CompletableFuture.completedFuture(null);
            var gaps = missing(history(), range, end);
            if (gaps.isEmpty()) return CompletableFuture.completedFuture(null);
            var windows = new ArrayList<Interval>();
            Duration maximum = maximumWindow(level);
            // Recover recent data first when an item has little or no older history.
            for (int i = gaps.size() - 1; i >= 0 && windows.size() < remaining; i--) {
                var gap = gaps.get(i);
                for (Instant right = gap.to(); right.isAfter(gap.from()) && windows.size() < remaining;) {
                    Instant left = right.minus(maximum);
                    if (left.isBefore(gap.from())) left = gap.from();
                    // Include adjacent minute boundaries so server-side rounding cannot leave seams.
                    Instant requestFrom = left.truncatedTo(ChronoUnit.MINUTES);
                    Instant requestTo = right.truncatedTo(ChronoUnit.MINUTES);
                    if (requestTo.isBefore(right)) requestTo = requestTo.plus(1, ChronoUnit.MINUTES);
                    windows.add(new Interval(requestFrom, requestTo));
                    right = left;
                }
            }
            return batch(windows, 0).thenCompose(ignored -> pass(level - 1));
        }

        CompletableFuture<Void> batch(List<Interval> windows, int offset) {
            if (offset >= windows.size()) return CompletableFuture.completedFuture(null);
            int limit = Math.min(windows.size(), offset + PARALLEL_REQUESTS);
            var requests = new ArrayList<CompletableFuture<BazaarHistory>>();
            for (int i = offset; i < limit; i++) {
                var window = windows.get(i);
                remaining--;
                requests.add(fetcher.fetch(window.from(), window.to()));
            }
            return CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).thenCompose(ignored -> {
                for (var request : requests) merge(request.join());
                return batch(windows, limit);
            });
        }
    }
}
