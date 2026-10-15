package com.coflnet.core;

import java.time.Duration;
import java.util.Optional;

/** Decides when the booster cookie state deserves a local chat warning. */
public final class CookieWarningPolicy {
    private CookieWarningPolicy() {}

    public enum Level { UNKNOWN, ACTIVE, EXPIRING, INACTIVE }

    public static final Duration EXPIRING_THRESHOLD = Duration.ofDays(1);

    public static Level classify(Optional<CookieBuffParser.CookieBuff> buff) {
        if (buff.isEmpty()) return Level.UNKNOWN;
        CookieBuffParser.CookieBuff b = buff.get();
        if (!b.active()) return Level.INACTIVE;
        boolean known = !b.remaining().isZero();
        return known && b.remaining().compareTo(EXPIRING_THRESHOLD) < 0 ? Level.EXPIRING : Level.ACTIVE;
    }

    /** @param previousState the last known (non-UNKNOWN) level seen this session, or UNKNOWN/null */
    public static boolean shouldWarn(Level previousState, Level newState) {
        if (newState != Level.INACTIVE && newState != Level.EXPIRING) return false;
        return previousState != newState;
    }

    public static String message(Level level, Duration remaining) {
        if (level == Level.INACTIVE) {
            return "§c[SkyCofl] Your booster cookie is inactive — /bz won't work; use the bazaar NPC or bookmark searches";
        }
        return "§e[SkyCofl] Booster cookie expires in " + formatRemaining(remaining);
    }

    public static String formatRemaining(Duration d) {
        long h = d.toHours(), m = d.toMinutesPart();
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m";
        return d.toSecondsPart() + "s";
    }
}
