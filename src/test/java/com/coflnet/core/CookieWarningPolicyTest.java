package com.coflnet.core;

import com.coflnet.core.CookieBuffParser.CookieBuff;
import com.coflnet.core.CookieWarningPolicy.Level;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CookieWarningPolicyTest {
    @Test void classify() {
        assertEquals(Level.UNKNOWN, CookieWarningPolicy.classify(Optional.empty()));
        assertEquals(Level.INACTIVE, CookieWarningPolicy.classify(Optional.of(new CookieBuff(false, Duration.ZERO))));
        assertEquals(Level.EXPIRING, CookieWarningPolicy.classify(Optional.of(new CookieBuff(true, Duration.ofHours(5)))));
        assertEquals(Level.ACTIVE, CookieWarningPolicy.classify(Optional.of(new CookieBuff(true, Duration.ofDays(1)))));
        assertEquals(Level.ACTIVE, CookieWarningPolicy.classify(Optional.of(new CookieBuff(true, Duration.ZERO))));
    }

    @Test void warnsOnlyOnTransitionIntoBadState() {
        assertTrue(CookieWarningPolicy.shouldWarn(Level.UNKNOWN, Level.INACTIVE));
        assertTrue(CookieWarningPolicy.shouldWarn(Level.ACTIVE, Level.INACTIVE));
        assertTrue(CookieWarningPolicy.shouldWarn(Level.ACTIVE, Level.EXPIRING));
        assertTrue(CookieWarningPolicy.shouldWarn(Level.EXPIRING, Level.INACTIVE));
        assertFalse(CookieWarningPolicy.shouldWarn(Level.INACTIVE, Level.INACTIVE));
        assertFalse(CookieWarningPolicy.shouldWarn(Level.EXPIRING, Level.EXPIRING));
        assertFalse(CookieWarningPolicy.shouldWarn(Level.INACTIVE, Level.ACTIVE));
        assertFalse(CookieWarningPolicy.shouldWarn(Level.ACTIVE, Level.UNKNOWN));
        assertTrue(CookieWarningPolicy.shouldWarn(null, Level.INACTIVE));
    }

    @Test void messages() {
        assertTrue(CookieWarningPolicy.message(Level.INACTIVE, Duration.ZERO).startsWith("§c[SkyCofl] Your booster cookie is inactive"));
        assertEquals("§e[SkyCofl] Booster cookie expires in 5h 12m",
                CookieWarningPolicy.message(Level.EXPIRING, Duration.ofHours(5).plusMinutes(12)));
        assertEquals("45m", CookieWarningPolicy.formatRemaining(Duration.ofMinutes(45)));
    }
}
