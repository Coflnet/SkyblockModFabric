package com.coflnet.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BazaarChartTest {
    @Test void parsesNativeChartSamplesAndAxes() throws Exception {
        try (var stream = getClass().getResourceAsStream("/bazaar/native-charts.json")) {
            assertNotNull(stream);
            var fixtures = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray();
            assertEquals(3, fixtures.size());
            for (var fixture : fixtures) {
                var row = fixture.getAsJsonObject();
                List<String> lore = row.getAsJsonArray("lore").asList().stream().map(v -> v.getAsString()).toList();
                var chart = BazaarChart.parse(row.get("title").getAsString(), lore);
                assertNotNull(chart, row.get("title").getAsString());
                assertEquals(row.get("points").getAsInt(), chart.points().size());
                assertEquals(row.get("columns").getAsInt(), chart.columns());
                assertEquals(row.get("rows").getAsInt(), chart.rows());
                assertEquals(List.of("7d", "5d", "4d", "2d", "now"), chart.times());
                var expected = row.getAsJsonArray("values");
                assertEquals(expected.size(), chart.labels().size());
                for (int i = 0; i < expected.size(); i++) assertEquals(expected.get(i).getAsDouble(), chart.labels().get(i).value());
                assertTrue(chart.points().stream().allMatch(p -> p.x() >= 0 && p.x() <= 1 && p.y() >= 0 && p.y() <= 1));
                assertEquals(chart.points(), BazaarChart.parse("Sell Price", lore).points());
                var colored = lore.stream().map(s -> "§8" + s.replace("x", "§bx§8")).toList();
                assertEquals(chart, BazaarChart.parse(row.get("title").getAsString(), colored));
                assertThrows(UnsupportedOperationException.class, () -> chart.points().clear());
            }
        }
    }

    private static List<String> plot() {
        return List.of("Quantity", "│ 100+..x....... │", "│    |...x...... │", "│  25+---------- │", "│     24h 12h now │");
    }

    @Test void keepsNonzeroBaselineAndCurrentSpan() {
        var chart = BazaarChart.parse("Buy Orders Volume 24h", plot());
        assertNotNull(chart);
        assertEquals(25, chart.labels().getLast().value());
        assertEquals(List.of("24h", "12h", "now"), chart.times());
        assertEquals("100", chart.labels().getFirst().text());
        assertEquals(25900, BazaarChart.number("25│9k"));
        assertEquals(28, BazaarChart.number("28│00"));
        assertEquals(1_500_000, BazaarChart.number("1.5m"));
        assertEquals("100", BazaarChart.shortNumber(100));
    }

    @Test void leavesUnknownAndMalformedTooltipsUntouched() {
        assertNull(BazaarChart.parse("Ordinary item", plot()));
        assertNull(BazaarChart.parse("Buy Price", List.of("broken server graph")));
        assertNull(BazaarChart.parse("Buy Price", null));
        assertNull(BazaarChart.parse("Buy Price", java.util.Collections.nCopies(257, "too many lines")));
        var changed = new ArrayList<>(plot());
        changed.set(4, "│    24h 12h │");
        assertNull(BazaarChart.parse("Buy Price", changed));
        changed = new ArrayList<>(plot());
        changed.set(3, "│ 100+---------- │");
        assertNull(BazaarChart.parse("Buy Price", changed));
        changed = new ArrayList<>(plot());
        changed.set(0, "a".repeat(4097));
        assertNull(BazaarChart.parse("Buy Price", changed));
        changed = new ArrayList<>(plot());
        changed.set(1, "│ " + "9".repeat(400) + "+..x....... │");
        assertNull(BazaarChart.parse("Buy Price", changed));
    }
}
