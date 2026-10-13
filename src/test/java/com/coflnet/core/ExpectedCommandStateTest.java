package com.coflnet.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpectedCommandStateTest {
    @AfterEach void reset() { ExpectedCommandState.clear(); }

    @Test void validUntilTtlThenExpires() {
        ExpectedCommandState.set(new ExpectCommandParser.ExpectedCommand("/bz x", 60), 1_000);
        assertEquals(Optional.of("/bz x"), ExpectedCommandState.current(1_000));
        assertEquals(Optional.of("/bz x"), ExpectedCommandState.current(60_999));
        assertEquals(Optional.empty(), ExpectedCommandState.current(61_000));
        assertEquals(Optional.empty(), ExpectedCommandState.current(1_000)); // expiry drops it for good
    }

    @Test void clearAndReplace() {
        ExpectedCommandState.set(new ExpectCommandParser.ExpectedCommand("/a", 60), 0);
        ExpectedCommandState.set(new ExpectCommandParser.ExpectedCommand("/b", 60), 0);
        assertEquals(Optional.of("/b"), ExpectedCommandState.current(1));
        ExpectedCommandState.clear();
        assertEquals(Optional.empty(), ExpectedCommandState.current(1));
    }
}
