package com.coflnet.core;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BazaarHistoryTest {
    @Test void parsesUtcSnapshotsInTimeOrderAndIgnoresInvalidSamples() {
        var history = BazaarHistory.parse("""
                [{"timestamp":"2026-10-22T10:03:00","buy":12,"sell":10,"buyVolume":300,"sellVolume":400},
                 {"timestamp":"2026-10-22T10:00:00Z","buy":11,"sell":9,"buyVolume":100,"sellVolume":200},
                 {"timestamp":"2026-10-22T10:03:00Z","buy":13,"sell":10,"buyVolume":300,"sellVolume":400},
                 {"timestamp":"bad","buy":1,"sell":1,"buyVolume":1,"sellVolume":1},
                 {"timestamp":"2026-10-22T10:04:00","buy":-1,"sell":1,"buyVolume":1,"sellVolume":1}]
                """);
        assertEquals(2, history.samples().size());
        assertEquals(Instant.parse("2026-10-22T10:00:00Z"), history.samples().getFirst().time());
        assertEquals(13, history.samples().getLast().buy());
        assertThrows(UnsupportedOperationException.class, () -> history.samples().clear());
        assertThrows(RuntimeException.class, () -> BazaarHistory.parse("{}"));
    }

    @Test void plotsActualTimeSpacingAndKeepsPriceAndOrderVolumeSeparate() {
        var history = new BazaarHistory(List.of(
                new BazaarHistory.Sample(Instant.EPOCH, 10, 8, 100, 200, 1000, 2000),
                new BazaarHistory.Sample(Instant.EPOCH.plusSeconds(10), 12, 9, 120, 180, 1100, 2100),
                new BazaarHistory.Sample(Instant.EPOCH.plusSeconds(40), 11, 9, 110, 190, 1200, 2200)));
        var chart = history.chart(BazaarHistory.Metric.BUY_PRICE);
        assertEquals(0.25, chart.points().get(1).x());
        assertTrue(chart.points().get(1).y() < chart.points().getFirst().y());
        assertEquals(100, BazaarHistory.value(history.samples().getFirst(), BazaarHistory.Metric.SELL_VOLUME));
        assertEquals(200, BazaarHistory.value(history.samples().getFirst(), BazaarHistory.Metric.BUY_VOLUME));
        assertEquals(1000, BazaarHistory.value(history.samples().getFirst(), BazaarHistory.Metric.INSTANT_BUY_VOLUME));
        assertEquals(2000, BazaarHistory.value(history.samples().getFirst(), BazaarHistory.Metric.INSTANT_SELL_VOLUME));
        assertEquals(8, BazaarHistory.value(history.samples().getFirst(), BazaarHistory.Metric.SELL_PRICE));
        for (var metric : BazaarHistory.Metric.values()) {
            for (var point : history.chart(metric).points()) {
                assertTrue(point.x() >= 0 && point.x() <= 1 && point.y() >= 0 && point.y() <= 1);
            }
        }
    }

    @Test void usesSpecificTimeTicksAndSharedScaleForComparisonsAndZoom() {
        var first = Instant.parse("2026-10-22T10:00:00Z");
        var history = new BazaarHistory(List.of(
                new BazaarHistory.Sample(first, 12, 4, 100, 200),
                new BazaarHistory.Sample(first.plusSeconds(1800), 13, 5, 110, 220),
                new BazaarHistory.Sample(first.plusSeconds(3600), 14, 6, 120, 240)));
        var plot = history.plot(BazaarHistory.Metric.BUY_PRICE, first, first.plusSeconds(3600),
                5, true, java.time.ZoneId.of("America/New_York"));
        assertEquals(List.of("06:00", "06:15", "06:30", "06:45", "07:00"), plot.chart().times());
        assertEquals(3, plot.comparison().size());
        assertTrue(plot.comparison().getFirst().y() > plot.chart().points().getFirst().y());
        assertEquals(first.plusSeconds(1800), plot.nearest(0.49).time());
        var zoomed = history.plot(BazaarHistory.Metric.BUY_PRICE, first.plusSeconds(1800), first.plusSeconds(3600),
                3, false, java.time.ZoneOffset.UTC);
        assertEquals(2, zoomed.samples().size());
        assertEquals(0, zoomed.chart().points().getFirst().x());
        assertTrue(zoomed.comparison().isEmpty());
        assertNull(history.chart(BazaarHistory.Metric.INSTANT_BUY_VOLUME));
    }

    @Test void pairsEachCategoryInItsOwnColumnWithoutOverlappingButtons() {
        var cells = new java.util.HashSet<String>();
        for (var metric : BazaarHistory.Metric.values()) {
            assertEquals(metric.column(), metric.counterpart().column());
            assertNotEquals(metric.row(), metric.counterpart().row());
            assertTrue(metric.column() >= 0 && metric.column() < 3);
            assertTrue(metric.row() >= 0 && metric.row() < 2);
            assertTrue(cells.add(metric.column() + ":" + metric.row()));
        }
        assertEquals(0, BazaarHistory.Metric.BUY_PRICE.column());
        assertEquals(1, BazaarHistory.Metric.INSTANT_BUY_VOLUME.column());
        assertEquals(2, BazaarHistory.Metric.BUY_VOLUME.column());
    }

    @Test void handlesEmptySingleAndFlatHistoriesWithoutInvalidCoordinates() {
        assertNull(new BazaarHistory(List.of()).chart(BazaarHistory.Metric.BUY_PRICE));
        var history = new BazaarHistory(List.of(new BazaarHistory.Sample(Instant.EPOCH, 0, 0, 0, 0)));
        var point = history.chart(BazaarHistory.Metric.BUY_VOLUME).points().getFirst();
        assertEquals(0.5, point.x());
        assertTrue(Double.isFinite(point.y()));
    }
}
