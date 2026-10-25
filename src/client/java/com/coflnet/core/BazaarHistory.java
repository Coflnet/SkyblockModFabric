package com.coflnet.core;

import com.google.gson.JsonParser;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Cofl snapshots retain prices, outstanding orders and Hypixel's rolling seven-day trade counters. */
public record BazaarHistory(List<Sample> samples, Instant requestedEnd) {
    public BazaarHistory(List<Sample> samples) { this(samples, null); }
    public record Sample(Instant time, double buy, double sell, double buyVolume, double sellVolume,
                         double buyMovingWeek, double sellMovingWeek) {
        public Sample(Instant time, double buy, double sell, double buyVolume, double sellVolume) {
            this(time, buy, sell, buyVolume, sellVolume, Double.NaN, Double.NaN);
        }
    }
    public enum Metric {
        BUY_PRICE, SELL_PRICE, INSTANT_BUY_VOLUME, INSTANT_SELL_VOLUME, BUY_VOLUME, SELL_VOLUME;
        public boolean buy() { return ordinal() % 2 == 0; }
        public boolean volume() { return ordinal() >= 2; }
        public boolean moving() { return this == INSTANT_BUY_VOLUME || this == INSTANT_SELL_VOLUME; }
        public Metric counterpart() { return values()[ordinal() ^ 1]; }
        public int column() { return ordinal() / 2; }
        public int row() { return ordinal() % 2; }
    }
    public record Plot(BazaarChart chart, List<BazaarChart.Point> comparison, List<Sample> samples,
                       long from, long to) {
        public Sample nearest(double fraction) {
            long at = from + Math.round(Math.clamp(fraction, 0, 1) * (to - from));
            int low = 0, high = samples.size() - 1;
            while (low < high) {
                int mid = (low + high) / 2;
                if (samples.get(mid).time().toEpochMilli() < at) low = mid + 1; else high = mid;
            }
            if (low > 0 && at - samples.get(low - 1).time().toEpochMilli() < samples.get(low).time().toEpochMilli() - at) low--;
            return samples.get(low);
        }
    }

    public BazaarHistory { samples = List.copyOf(samples); }

    public static BazaarHistory parse(String json) {
        var rows = JsonParser.parseString(json).getAsJsonArray();
        if (rows.size() > 10000) throw new IllegalArgumentException("Too many history samples");
        var sorted = new TreeMap<Instant, Sample>();
        for (var row : rows) {
            try {
                var value = row.getAsJsonObject();
                String timestamp = value.get("timestamp").getAsString();
                Instant time = timestamp.endsWith("Z") ? Instant.parse(timestamp)
                        : LocalDateTime.parse(timestamp).toInstant(ZoneOffset.UTC);
                double buy = value.get("buy").getAsDouble(), sell = value.get("sell").getAsDouble();
                double buyVolume = value.get("buyVolume").getAsDouble(), sellVolume = value.get("sellVolume").getAsDouble();
                if (!valid(buy) || !valid(sell) || !valid(buyVolume) || !valid(sellVolume)) continue;
                sorted.put(time, new Sample(time, buy, sell, buyVolume, sellVolume,
                        optional(value, "buyMovingWeek"), optional(value, "sellMovingWeek")));
            } catch (RuntimeException ignored) {
                // A malformed sample must not discard the rest of the requested interval.
            }
        }
        return new BazaarHistory(new ArrayList<>(sorted.values()));
    }

    private static double optional(com.google.gson.JsonObject value, String key) {
        if (!value.has(key) || value.get(key).isJsonNull()) return Double.NaN;
        double number = value.get(key).getAsDouble();
        return valid(number) ? number : Double.NaN;
    }

    private static boolean valid(double value) { return Double.isFinite(value) && value >= 0; }

    public BazaarChart chart(Metric metric) {
        if (samples.isEmpty()) return null;
        var plot = plot(metric, samples.getFirst().time(), samples.getLast().time(), 5, false, ZoneOffset.UTC);
        return plot == null ? null : plot.chart();
    }

    public Plot plot(Metric metric, Instant from, Instant to, int ticks, boolean compare, ZoneId zone) {
        var visible = samples.stream().filter(sample -> !sample.time().isBefore(from) && !sample.time().isAfter(to)
                && valid(value(sample, metric))).toList();
        if (visible.isEmpty()) return null;
        double min = Double.POSITIVE_INFINITY, max = 0;
        for (var sample : visible) {
            double value = value(sample, metric);
            min = Math.min(min, value); max = Math.max(max, value);
            double other = value(sample, metric.counterpart());
            if (compare && valid(other)) { min = Math.min(min, other); max = Math.max(max, other); }
        }
        double padding = Math.max((max - min) * 0.05, Math.max(0.01, max * 0.001));
        double low = Math.max(0, min - padding), high = max + padding;
        long first = from.toEpochMilli(), last = to.toEpochMilli();
        var points = points(visible, metric, first, last, low, high);
        var secondary = compare ? points(visible, metric.counterpart(), first, last, low, high) : List.<BazaarChart.Point>of();
        var labels = new ArrayList<BazaarChart.Label>();
        for (int i = 0; i <= 4; i++) {
            double value = high - (high - low) * i / 4;
            labels.add(new BazaarChart.Label(i / 4.0, BazaarChart.shortNumber(value), value));
        }
        var times = new ArrayList<String>();
        var format = DateTimeFormatter.ofPattern(last - first <= 86_400_000 ? "HH:mm" : "MM/dd HH:mm").withZone(zone);
        ticks = Math.clamp(ticks, 2, 7);
        for (int i = 0; i < ticks; i++) times.add(format.format(Instant.ofEpochMilli(first + (last - first) * i / (ticks - 1))));
        return new Plot(new BazaarChart(points, labels, List.copyOf(times), visible.size(), 5), secondary, visible, first, last);
    }

    private static List<BazaarChart.Point> points(List<Sample> samples, Metric metric, long first, long last, double low, double high) {
        return samples.stream().filter(sample -> valid(value(sample, metric))).map(sample -> new BazaarChart.Point(
                last == first ? 0.5 : (double) (sample.time().toEpochMilli() - first) / (last - first),
                (high - value(sample, metric)) / (high - low))).toList();
    }

    public static double value(Sample sample, Metric metric) {
        return switch (metric) {
            case BUY_PRICE -> sample.buy();
            case SELL_PRICE -> sample.sell();
            // Hypixel's buy_summary is the ask side used for instant buys; sell_summary is the bid side.
            case BUY_VOLUME -> sample.sellVolume();
            case SELL_VOLUME -> sample.buyVolume();
            case INSTANT_BUY_VOLUME -> sample.buyMovingWeek();
            case INSTANT_SELL_VOLUME -> sample.sellMovingWeek();
        };
    }
}
