package com.coflnet.core;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the "Cookie Buff" section of Hypixel's tab-list footer. */
public final class CookieBuffParser {
    private CookieBuffParser() {}

    public record CookieBuff(boolean active, Duration remaining) {}

    private static final Pattern FORMATTING = Pattern.compile("§.");
    private static final Pattern HEADING = Pattern.compile("cookie buff", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN = Pattern.compile("(\\d+)\\s*([dhms])\\b");

    public static Optional<CookieBuff> parse(String footerPlainText) {
        if (footerPlainText == null) return Optional.empty();
        String[] lines = FORMATTING.matcher(footerPlainText).replaceAll("").split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher heading = HEADING.matcher(lines[i]);
            if (!heading.find()) continue;
            StringBuilder body = new StringBuilder(lines[i].substring(heading.end()));
            for (int j = i + 1; j < lines.length; j++) {
                String line = lines[j].trim();
                if (line.isEmpty()) break;
                if (!isSectionBody(line)) break; // next heading
                body.append('\n').append(line);
            }
            return interpret(body.toString());
        }
        return Optional.empty();
    }

    private static boolean isSectionBody(String line) {
        String lower = line.toLowerCase();
        return lower.contains("active") || TOKEN.matcher(line).find();
    }

    private static Optional<CookieBuff> interpret(String body) {
        if (body.toLowerCase().contains("not active")) return Optional.of(new CookieBuff(false, Duration.ZERO));
        Matcher m = TOKEN.matcher(body);
        Duration total = Duration.ZERO;
        boolean any = false;
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            total = total.plus(switch (m.group(2)) {
                case "d" -> Duration.ofDays(n);
                case "h" -> Duration.ofHours(n);
                case "m" -> Duration.ofMinutes(n);
                default -> Duration.ofSeconds(n);
            });
            any = true;
        }
        if (any) return Optional.of(new CookieBuff(true, total));
        if (body.toLowerCase().contains("active")) return Optional.of(new CookieBuff(true, Duration.ZERO));
        return Optional.empty();
    }
}
