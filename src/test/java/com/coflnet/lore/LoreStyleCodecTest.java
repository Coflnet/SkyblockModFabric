package com.coflnet.lore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoreStyleCodecTest {
    @Test
    void preservesUnknownCustomFormatMembers() {
        List<LoreModule> modules = LoreModule.defaults();
        modules.add(new LoreModule("future", "future", "overwritten"));
        modules.stream()
                .filter(module -> "LBIN".equals(module.match))
                .findFirst()
                .orElseThrow()
                .template = "&a{lbin}";

        String merged = LoreStyleCodec.mergeInto(
                "{\"future\":\"kept\",\"LBIN\":\"old\"}",
                modules);
        JsonObject object = JsonParser.parseString(merged).getAsJsonObject();

        assertEquals("kept", object.get("future").getAsString());
        assertEquals("&a{lbin}", object.get("LBIN").getAsString());
        assertTrue(object.has("future"));
    }
}
