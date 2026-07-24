package com.coflnet.lore;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LoreSettingsPayloadTest {
    @Test
    void decodesTheBackendDoubleEncodedString() {
        String payload = LoreSettingsPayload.decode(
                new JsonPrimitive("\"{\\\"Fields\\\":[]}\""));

        assertEquals("{\"Fields\":[]}", payload);
    }

    @Test
    void acceptsDirectStringAndObjectPayloads() {
        assertEquals("{\"Fields\":[]}",
                LoreSettingsPayload.decode(new JsonPrimitive("{\"Fields\":[]}")));
        assertEquals("{\"Fields\":[]}",
                LoreSettingsPayload.decode(JsonParser.parseString("{\"Fields\":[]}")));
    }

    @Test
    void rejectsOversizedPayloads() {
        assertNull(LoreSettingsPayload.decode(
                new JsonPrimitive("x".repeat(
                        LoreSettingsPayload.MAX_PAYLOAD_LENGTH + 1))));
    }
}
