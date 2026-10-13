package com.coflnet.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExpectCommandParserTest {
    @Test void parsesStringEncodedData() {
        var r = ExpectCommandParser.parse("{\"type\":\"expectCommand\",\"data\":\"{\\\"command\\\":\\\"/bz Enchanted Diamond\\\",\\\"ttl\\\":60}\"}");
        assertTrue(r.isPresent());
        assertEquals("/bz Enchanted Diamond", r.get().command());
        assertEquals(60, r.get().ttlSeconds());
    }

    @Test void parsesInlineObjectAndDefaultsTtl() {
        var r = ExpectCommandParser.parse("{\"type\":\"expectCommand\",\"data\":{\"command\":\"/bz x\"}}");
        assertEquals(ExpectCommandParser.DEFAULT_TTL_SECONDS, r.orElseThrow().ttlSeconds());
    }

    @Test void ignoresOtherTypesAndGarbage() {
        assertTrue(ExpectCommandParser.parse("{\"type\":\"flip\",\"data\":\"expectCommand\"}").isEmpty());
        assertTrue(ExpectCommandParser.parse("not json expectCommand").isEmpty());
        assertTrue(ExpectCommandParser.parse(null).isEmpty());
        assertTrue(ExpectCommandParser.parse("{\"type\":\"expectCommand\",\"data\":\"{\\\"ttl\\\":5}\"}").isEmpty());
        assertTrue(ExpectCommandParser.parse("{\"type\":\"expectCommand\",\"data\":\"{\\\"command\\\":\\\"/a\\\",\\\"ttl\\\":0}\"}").isEmpty());
    }
}
