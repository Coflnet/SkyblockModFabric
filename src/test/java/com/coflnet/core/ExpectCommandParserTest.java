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

    @Test void parsesTheWireTypeTheBackendActuallySends() {
        var r = ExpectCommandParser.parse("{\"type\":\"expect\",\"data\":\"{\\\"command\\\":\\\"/viewauction 70e3559483ce4da8b\\\",\\\"ttl\\\":120}\"}");
        assertTrue(r.isPresent());
        assertEquals("/viewauction 70e3559483ce4da8b", r.get().command());
        assertEquals(120, r.get().ttlSeconds());
    }

    @Test void expectInsideOtherTypeStillIgnored() {
        assertTrue(ExpectCommandParser.parse("{\"type\":\"expected\",\"data\":\"{\\\"command\\\":\\\"/a\\\"}\"}").isEmpty());
    }

    @Test
    void parsesTheCurrentWireMessage() {
        var r = ExpectCommandParser.parse("{\"type\":\"expect\",\"data\":\"{\\\"command\\\":\\\"/recipe Necron's Leggings\\\",\\\"ttl\\\":120}\"}");
        assertEquals("/recipe Necron's Leggings", r.orElseThrow().command());
        assertEquals(120, r.orElseThrow().ttlSeconds());
    }
}
