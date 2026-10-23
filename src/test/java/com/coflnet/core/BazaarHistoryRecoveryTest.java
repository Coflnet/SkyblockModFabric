package com.coflnet.core;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BazaarHistoryRecoveryTest {
    private static final Instant END = Instant.parse("2026-10-23T00:32:00Z");

    private static BazaarHistory.Sample sample(Instant time) {
        return new BazaarHistory.Sample(time, 12, 10, 100, 200, 1000, 2000);
    }

    private static long cadence(BazaarHistoryClient.Range range) {
        return switch (range) { case HOUR -> 20; case DAY -> 300; case WEEK -> 7200; case MONTH -> 86400; };
    }

    private static BazaarHistory grid(Instant from, Instant to, long seconds) {
        var rows = new ArrayList<BazaarHistory.Sample>();
        for (Instant at = from; !at.isAfter(to); at = at.plusSeconds(seconds)) rows.add(sample(at));
        return new BazaarHistory(rows);
    }

    @Test void recoversMissingStartMiddleAndEndForEveryRangeAndAllSixGraphs() {
        for (var range : BazaarHistoryClient.Range.values()) {
            Instant from = END.minus(range.duration);
            var full = grid(from, END, cadence(range));
            long total = range.duration.toSeconds();
            var initial = new BazaarHistory(full.samples().stream().filter(row -> {
                long offset = Duration.between(from, row.time()).toSeconds();
                return offset >= total / 5 && offset < total * 4 / 5 && (offset < total * 2 / 5 || offset > total * 3 / 5);
            }).toList());
            var calls = new AtomicInteger();
            var recovered = BazaarHistoryRecovery.recover(initial, range, END, (left, right) -> {
                calls.incrementAndGet();
                long length = Duration.between(left, right).toSeconds();
                long maximum = switch (range) { case HOUR, DAY -> 3600; case WEEK -> 86400; case MONTH -> 604800; };
                assertTrue(length > 0 && length < maximum);
                long spacing = length < 3600 ? 20 : length < 86400 ? 300 : 7200;
                return CompletableFuture.completedFuture(grid(left, right, spacing));
            }).join();
            assertFalse(BazaarHistoryRecovery.incomplete(recovered, range, END), range.name());
            assertEquals(END, recovered.requestedEnd());
            assertTrue(calls.get() > 0 && calls.get() <= BazaarHistoryRecovery.MAX_REQUESTS);
            assertTrue(recovered.samples().stream().allMatch(row -> !row.time().isBefore(from) && !row.time().isAfter(END)));
            for (var metric : BazaarHistory.Metric.values()) {
                var plot = recovered.plot(metric, from, END, 7, true, java.time.ZoneOffset.UTC);
                assertNotNull(plot, range + " " + metric);
                assertEquals(from.toEpochMilli(), plot.from());
                assertEquals(END.toEpochMilli(), plot.to());
                assertEquals(recovered.samples().size(), plot.chart().points().size());
            }
        }
    }

    @Test void fillsDelayedWeeklyAndMonthlyTailsThroughSuccessivelyFinerHistory() {
        for (var range : List.of(BazaarHistoryClient.Range.WEEK, BazaarHistoryClient.Range.MONTH)) {
            var initial = grid(END.minus(range.duration), END.minus(Duration.ofDays(2)), cadence(range));
            var lengths = new ArrayList<Long>();
            var recovered = BazaarHistoryRecovery.recover(initial, range, END, (from, to) -> {
                long length = Duration.between(from, to).toSeconds();
                lengths.add(length);
                long spacing = length < 3600 ? 20 : length < 86400 ? 300 : 7200;
                Instant latest = length < 3600 ? END : length < 86400 ? END.minusSeconds(1500) : END.minusSeconds(21600);
                return CompletableFuture.completedFuture(grid(from, to.isBefore(latest) ? to : latest, spacing));
            }).join();
            assertFalse(BazaarHistoryRecovery.incomplete(recovered, range, END));
            assertTrue(lengths.stream().anyMatch(length -> length < 3600));
            assertTrue(Duration.between(recovered.samples().getLast().time(), END).toSeconds() <= 300);
        }
    }

    @Test void splitsAnEmptyDetailedReplyAgainInsteadOfTreatingItAsMissingSourceData() {
        for (var range : List.of(BazaarHistoryClient.Range.HOUR, BazaarHistoryClient.Range.DAY)) {
            var initial = grid(END.minus(range.duration), END, cadence(range));
            var sparse = new BazaarHistory(initial.samples().stream().filter(row -> row.time().isBefore(END.minusSeconds(3300))
                    || row.time().isAfter(END.minusSeconds(600))).toList());
            var emptyReplies = new AtomicInteger();
            var smallerReplies = new AtomicInteger();
            var recovered = BazaarHistoryRecovery.recover(sparse, range, END, (from, to) -> {
                if (Duration.between(from, to).toMinutes() > 30) {
                    emptyReplies.incrementAndGet();
                    return CompletableFuture.completedFuture(new BazaarHistory(List.of()));
                }
                smallerReplies.incrementAndGet();
                return CompletableFuture.completedFuture(grid(from, to, 20));
            }).join();
            assertTrue(emptyReplies.get() > 0);
            assertTrue(smallerReplies.get() > 0);
            assertFalse(BazaarHistoryRecovery.incomplete(recovered, range, END));
        }
    }

    @Test void leavesCompleteHistoriesAlone() {
        for (var range : BazaarHistoryClient.Range.values()) {
            var full = grid(END.minus(range.duration), END, cadence(range));
            var recovered = BazaarHistoryRecovery.recover(full, range, END, (from, to) -> {
                fail("Complete history must not issue extra requests");
                return null;
            }).join();
            assertEquals(full.samples(), recovered.samples());
        }
    }

    @Test void unavailableHistoriesStayHonestlyIncompleteAndRequestsAreBounded() {
        for (var range : BazaarHistoryClient.Range.values()) {
            var calls = new AtomicInteger();
            var recovered = BazaarHistoryRecovery.recover(new BazaarHistory(List.of()), range, END, (from, to) -> {
                calls.incrementAndGet();
                return CompletableFuture.completedFuture(new BazaarHistory(List.of()));
            }).join();
            assertTrue(recovered.samples().isEmpty());
            assertTrue(BazaarHistoryRecovery.incomplete(recovered, range, END));
            assertTrue(calls.get() <= BazaarHistoryRecovery.MAX_REQUESTS);
        }
    }

    @Test void limitsConcurrentRequestsEvenWhenEveryRecoveryWindowIsEmpty() {
        var pending = new ArrayList<CompletableFuture<BazaarHistory>>();
        var peak = new AtomicInteger();
        var load = BazaarHistoryRecovery.recover(new BazaarHistory(List.of()), BazaarHistoryClient.Range.MONTH, END, (from, to) -> {
            var future = new CompletableFuture<BazaarHistory>();
            pending.add(future);
            peak.accumulateAndGet((int) pending.stream().filter(request -> !request.isDone()).count(), Math::max);
            return future;
        });
        while (!load.isDone()) {
            var next = pending.stream().filter(future -> !future.isDone()).findFirst().orElseThrow();
            next.complete(new BazaarHistory(List.of()));
        }
        assertTrue(load.join().samples().isEmpty());
        assertEquals(BazaarHistoryRecovery.PARALLEL_REQUESTS, peak.get());
        assertEquals(BazaarHistoryRecovery.MAX_REQUESTS, pending.size());
    }

    @Test void overlappingRepliesAreSortedDeduplicatedAndRetainKnownTradeCounters() {
        var initial = new BazaarHistory(List.of(sample(END)));
        var recovered = BazaarHistoryRecovery.recover(initial, BazaarHistoryClient.Range.HOUR, END, (from, to) -> {
            var rows = grid(from, to, 20).samples().stream()
                    .map(row -> new BazaarHistory.Sample(row.time(), 13, 11, 110, 210)).toList();
            return CompletableFuture.completedFuture(new BazaarHistory(rows));
        }).join();
        assertEquals(recovered.samples().size(), recovered.samples().stream().map(BazaarHistory.Sample::time).distinct().count());
        for (int i = 1; i < recovered.samples().size(); i++)
            assertTrue(recovered.samples().get(i).time().isAfter(recovered.samples().get(i - 1).time()));
        assertEquals(1000, recovered.samples().getLast().buyMovingWeek());
        assertEquals(2000, recovered.samples().getLast().sellMovingWeek());
    }
}
