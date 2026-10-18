package com.coflnet.core;

import com.coflnet.core.SignGuidance.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SignGuidanceTest {
    @Test void noExpectedValueMeansNone() {
        assertEquals(State.NONE, SignGuidance.compute("12", null).state());
        assertEquals(State.NONE, SignGuidance.compute("12", "  ").state());
    }

    @Test void prefixIsTypingWithRemainder() {
        var g = SignGuidance.compute("12", "1280");
        assertEquals(State.TYPING, g.state());
        assertEquals("80", g.remaining());
        assertEquals(State.TYPING, SignGuidance.compute("", "1280").state());
        assertEquals("1280", SignGuidance.compute(null, "1280").remaining());
    }

    @Test void equalTrimmedIsDone() {
        var g = SignGuidance.compute("1280", " 1280 ");
        assertEquals(State.DONE, g.state());
        assertTrue(g.message().contains("press Done or Esc"));
    }

    @Test void divergingIsMismatch() {
        var g = SignGuidance.compute("13", "1280");
        assertEquals(State.MISMATCH, g.state());
        assertTrue(g.message().endsWith("retype 1280"));
        assertEquals(State.MISMATCH, SignGuidance.compute("12800", "1280").state());
    }

    @Test void formatsDigitGrouping() {
        assertEquals("1,280", SignGuidance.formatAmount("1280"));
        assertEquals("999", SignGuidance.formatAmount("999"));
        assertEquals("1,000,000", SignGuidance.formatAmount("1000000"));
        assertEquals("1,280", SignGuidance.formatAmount("1,280"));
        assertEquals("Enchanted Diamond", SignGuidance.formatAmount("Enchanted Diamond"));
        assertEquals("", SignGuidance.formatAmount(""));
        assertNull(SignGuidance.formatAmount(null));
    }

    @Test void displaySplitKeepsGroupingConsistent() {
        var split = SignGuidance.displaySplit(SignGuidance.compute("1280", "128000"));
        assertEquals("128,0", split[0]);
        assertEquals("00", split[1]);
        var text = SignGuidance.displaySplit(SignGuidance.compute("Ench", "Enchanted Diamond"));
        assertEquals("Ench", text[0]);
        assertEquals("anted Diamond", text[1]);
    }

    @Test void expectedValuePriority() {
        String fill = "{\"line\":\"to order\",\"value\":\"640\"}";
        assertEquals("640", SignGuidance.expectedValue(fill, "diamond", "to order: 99", "to order"));
        assertEquals("diamond", SignGuidance.expectedValue(fill, "diamond", "to order: 99", "Enter query"));
        assertEquals("99", SignGuidance.expectedValue(null, null, "to order: 99", "to order"));
        assertNull(SignGuidance.expectedValue(null, null, "to order: 99", "other"));
        assertNull(SignGuidance.expectedValue("not json", null, "", "x"));
        assertNull(SignGuidance.expectedValue(null, null, null, "x"));
    }

    @Test void localGuideValueOnlyWhenNoGoalOrBackendValue() {
        String goalFill = "{\"line\":\"to order\",\"value\":\"640\"}";
        assertEquals("640", SignGuidance.expectedValue(goalFill, null, null, "to order", "13"));
        assertEquals("99", SignGuidance.expectedValue(null, null, "to order: 99", "to order", "13"));
        assertEquals("13", SignGuidance.expectedValue(null, null, null, "to order", "13"));
    }
}
