package com.coflnet.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class CookieBuffParserTest {
    private static final String HEAD = "Players: 12\n\nActive Effects\nNo effects active. Drink Potions or splash them on the ground to buff yourself!\n\n";

    @Test void inactive() {
        var r = CookieBuffParser.parse(HEAD + "Cookie Buff\nNot active! Obtain booster cookies from the community shop in the hub.\n\nUpgrades\n");
        assertTrue(r.isPresent());
        assertFalse(r.get().active());
        assertEquals(Duration.ZERO, r.get().remaining());
    }

    @Test void activeWithRemainingTime() {
        var r = CookieBuffParser.parse(HEAD + "§d§lCookie Buff\n§f3d 4h 12m\n\nUpgrades\n1d 2h");
        assertTrue(r.orElseThrow().active());
        assertEquals(Duration.ofDays(3).plusHours(4).plusMinutes(12), r.get().remaining());
    }

    @Test void secondsAndFooterEndingAfterSection() {
        var r = CookieBuffParser.parse("Cookie Buff\n12m 5s");
        assertEquals(Duration.ofMinutes(12).plusSeconds(5), r.orElseThrow().remaining());
    }

    @Test void nextHeadingWithoutBlankLineIsNotConsumed() {
        var r = CookieBuffParser.parse("Cookie Buff\nNot active!\nUpgrades\n5d 3h");
        assertFalse(r.orElseThrow().active());
        assertEquals(Duration.ZERO, r.get().remaining());
    }

    @Test void noSectionIsEmpty() {
        assertTrue(CookieBuffParser.parse(HEAD).isEmpty());
        assertTrue(CookieBuffParser.parse(null).isEmpty());
        assertTrue(CookieBuffParser.parse("Cookie Buff\n\nUpgrades\n3d").isEmpty());
    }
}
