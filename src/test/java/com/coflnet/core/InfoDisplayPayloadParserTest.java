package com.coflnet.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InfoDisplayPayloadParserTest {
    @Test void parsesMinimalPayload() throws InfoDisplayPayloadParser.ParseException {
        InfoDisplayPayload payload = InfoDisplayPayloadParser.parse("{\"id\":1}");
        assertEquals(1, payload.id);
        assertNull(payload.title);
        assertTrue(payload.lines.isEmpty());
        assertEquals(0, payload.ttlSeconds);
        assertFalse(payload.clear);
    }

    @Test void parsesMixedPlainAndObjectLines() throws InfoDisplayPayloadParser.ParseException {
        InfoDisplayPayload payload = InfoDisplayPayloadParser.parse(
                "{\"id\":2,\"title\":\"§6Flips\",\"lines\":[\"§aplain line\","
                        + "{\"text\":\"clickable\",\"hover\":\"hover text\",\"onClick\":\"suggest:/foo\"}],\"ttl\":30}");
        assertEquals(2, payload.id);
        assertEquals("§6Flips", payload.title);
        assertEquals(2, payload.lines.size());
        assertEquals("§aplain line", payload.lines.get(0).text);
        assertNull(payload.lines.get(0).hover);
        assertEquals("clickable", payload.lines.get(1).text);
        assertEquals("hover text", payload.lines.get(1).hover);
        assertEquals("suggest:/foo", payload.lines.get(1).onClick);
        assertEquals(30, payload.ttlSeconds);
    }

    @Test void parsesClearFlag() throws InfoDisplayPayloadParser.ParseException {
        InfoDisplayPayload payload = InfoDisplayPayloadParser.parse("{\"id\":3,\"clear\":true}");
        assertEquals(3, payload.id);
        assertTrue(payload.clear);
    }

    @Test void rejectsIdOutOfRange() {
        InfoDisplayPayloadParser.ParseException ex = assertThrows(InfoDisplayPayloadParser.ParseException.class,
                () -> InfoDisplayPayloadParser.parse("{\"id\":4}"));
        assertTrue(ex.getMessage().contains("id"));

        assertThrows(InfoDisplayPayloadParser.ParseException.class,
                () -> InfoDisplayPayloadParser.parse("{\"id\":0}"));
    }

    @Test void rejectsMissingId() {
        assertThrows(InfoDisplayPayloadParser.ParseException.class,
                () -> InfoDisplayPayloadParser.parse("{\"title\":\"no id\"}"));
    }

    @Test void rejectsMalformedJson() {
        assertThrows(InfoDisplayPayloadParser.ParseException.class,
                () -> InfoDisplayPayloadParser.parse("{not json"));
        assertThrows(InfoDisplayPayloadParser.ParseException.class,
                () -> InfoDisplayPayloadParser.parse(""));
        assertThrows(InfoDisplayPayloadParser.ParseException.class,
                () -> InfoDisplayPayloadParser.parse(null));
        assertThrows(InfoDisplayPayloadParser.ParseException.class,
                () -> InfoDisplayPayloadParser.parse("[1,2,3]"));
    }

    @Test void ttlDefaultsToPermanentAndNegativeIsClamped() throws InfoDisplayPayloadParser.ParseException {
        assertEquals(0, InfoDisplayPayloadParser.parse("{\"id\":1}").ttlSeconds);
        assertEquals(0, InfoDisplayPayloadParser.parse("{\"id\":1,\"ttl\":-5}").ttlSeconds);
        assertEquals(60, InfoDisplayPayloadParser.parse("{\"id\":1,\"ttl\":60}").ttlSeconds);
    }

    @Test void capsLineCountAndLength() throws InfoDisplayPayloadParser.ParseException {
        StringBuilder linesJson = new StringBuilder("[");
        for (int i = 0; i < 40; i++) {
            if (i > 0) linesJson.append(",");
            linesJson.append("\"line").append(i).append("\"");
        }
        linesJson.append("]");
        InfoDisplayPayload payload = InfoDisplayPayloadParser.parse("{\"id\":1,\"lines\":" + linesJson + "}");
        assertEquals(InfoDisplayPayloadParser.MAX_LINES, payload.lines.size());

        String longText = "x".repeat(500);
        InfoDisplayPayload capped = InfoDisplayPayloadParser.parse("{\"id\":1,\"lines\":[\"" + longText + "\"]}");
        assertEquals(InfoDisplayPayloadParser.MAX_LINE_LENGTH, capped.lines.get(0).text.length());
    }

    @Test void rejectsNonArrayLines() {
        assertThrows(InfoDisplayPayloadParser.ParseException.class,
                () -> InfoDisplayPayloadParser.parse("{\"id\":1,\"lines\":\"not an array\"}"));
    }

    @Test void rejectsInvalidLineEntry() {
        // A nested array is neither a primitive (plain string) nor an object ({"text":...}).
        assertThrows(InfoDisplayPayloadParser.ParseException.class,
                () -> InfoDisplayPayloadParser.parse("{\"id\":1,\"lines\":[[1,2]]}"));
    }
}
