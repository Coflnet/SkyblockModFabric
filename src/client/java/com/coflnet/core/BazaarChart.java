package com.coflnet.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Decodes the rounded cells already supplied by the server, without inventing extra samples. */
public record BazaarChart(List<Point> points, List<Label> labels, List<String> times, int columns, int rows) {
    public record Point(double x, double y) { }
    public record Segment(Point from, Point to) { }
    public record Label(double y, String text, double value) { }

    /** Connect only adjacent, ordered observations; leave unavailable time spans visibly empty. */
    public static List<Segment> segments(List<Point> points, double maximumGap) {
        if (!Double.isFinite(maximumGap) || maximumGap <= 0) return List.of();
        var segments = new ArrayList<Segment>();
        for (int i = 1; i < points.size(); i++) {
            var left = points.get(i - 1);
            var right = points.get(i);
            if (!validPoint(left) || !validPoint(right)) continue;
            double gap = right.x() - left.x();
            if (gap >= 0 && gap <= maximumGap) segments.add(new Segment(left, right));
        }
        return List.copyOf(segments);
    }

    private static boolean validPoint(Point point) {
        return Double.isFinite(point.x()) && Double.isFinite(point.y())
                && point.x() >= 0 && point.x() <= 1 && point.y() >= 0 && point.y() <= 1;
    }

    private static final Pattern VALUE = Pattern.compile(
            "^[\\s·│┃║|]*([0-9][0-9, .│┃║|]*(?:[kKmMbB])?)\\s*[+┼├╋]",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern TIME = Pattern.compile("(?i)(?:\\d+(?:\\.\\d+)?\\s*[dhm]|now)");
    private static final Pattern FORMAT = Pattern.compile("§[0-9a-fk-orA-FK-OR]");
    private static final List<String> TITLES = List.of(
            "Instant Buy Volume", "Instant Sell Volume", "Buy Orders Volume", "Sell Offers Volume",
            "Instant Buy Moving Coins", "Instant Sell Moving Coins", "Buy Price", "Sell Price");

    public static String clean(String text) {
        if (text == null) return "";
        return text.indexOf('§') < 0 ? text : FORMAT.matcher(text).replaceAll("");
    }

    public static boolean isChart(String title) {
        String text = clean(title);
        return TITLES.stream().anyMatch(text::startsWith);
    }

    /** Returns null for unrecognized data so the original tooltip remains available. */
    public static BazaarChart parse(String title, List<String> raw) {
        if (!isChart(title) || raw == null || raw.size() > 256) return null;
        List<String> lines = raw.stream().map(BazaarChart::clean).toList();
        var axes = new TreeMap<Integer, Matcher>();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).length() > 4096) return null;
            Matcher match = VALUE.matcher(lines.get(i));
            if (match.find()) axes.put(i, match);
        }
        if (axes.size() < 2) return null;
        int first = axes.firstKey(), last = axes.lastKey(), height = last - first;
        if (height < 1 || height > 128) return null;
        int axis = axes.get(first).end();
        int width = stripRight(lines.get(last)).length() - axes.get(last).end();
        if (width < 8 || width > 2048) return null;

        var labels = new ArrayList<Label>();
        for (var entry : axes.entrySet()) {
            double value;
            try {
                value = number(entry.getValue().group(1));
            } catch (NumberFormatException exception) {
                return null;
            }
            if (!Double.isFinite(value)) return null;
            labels.add(new Label((double) (entry.getKey() - first) / height, shortNumber(value), value));
        }
        if (labels.getFirst().value() <= labels.getLast().value()) return null;

        var points = new ArrayList<Point>();
        for (int row = first; row <= last; row++) {
            String line = stripRight(lines.get(row));
            Matcher value = axes.get(row);
            int start = value == null ? axis : value.end();
            for (int column = start; column < line.length(); column++) {
                if ("xX×✕✖●*".indexOf(line.charAt(column)) < 0) continue;
                double x = (double) (column - start) / (width - 1);
                if (x >= 0 && x <= 1) points.add(new Point(x, (double) (row - first) / height));
            }
        }
        if (points.isEmpty() || points.size() > 16384) return null;

        List<String> times = List.of();
        for (int i = last + 1; i < lines.size(); i++) {
            Matcher time = TIME.matcher(lines.get(i));
            var found = new ArrayList<String>();
            while (time.find()) found.add(time.group().replace(" ", ""));
            if (found.size() >= 2 && found.stream().anyMatch(s -> s.equalsIgnoreCase("now"))) {
                times = found;
                break;
            }
        }
        if (times.size() < 2 || times.size() > 12) return null;
        return new BazaarChart(List.copyOf(points), List.copyOf(labels), List.copyOf(times), width, height);
    }

    private static String stripRight(String text) {
        return text.replaceFirst("[\\s│┃║|]+$", "");
    }

    static double number(String text) {
        String value = text.trim().replace(",", "").replace(" ", "")
                .replace('│', '.').replace('┃', '.').replace('║', '.').replace('|', '.');
        double multiplier = switch (Character.toLowerCase(value.charAt(value.length() - 1))) {
            case 'k' -> 1e3;
            case 'm' -> 1e6;
            case 'b' -> 1e9;
            default -> 1;
        };
        if (multiplier != 1) value = value.substring(0, value.length() - 1);
        return Double.parseDouble(value) * multiplier;
    }

    static String shortNumber(double value) {
        if (value >= 1e9) return trim(value / 1e9) + "b";
        if (value >= 1e6) return trim(value / 1e6) + "m";
        if (value >= 1e3) return trim(value / 1e3) + "k";
        return trim(value);
    }

    private static String trim(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceFirst("\\.?0+$", "");
    }
}
