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

    @Test
    void malformedOwnedValuesLeaveTemplatesUntouched() {
        List<LoreModule> modules = LoreModule.defaults();
        LoreModule lbin = module(modules, "LBIN");
        lbin.template = "&a{lbin}";

        LoreStyleCodec.applyToModules("{\"LBIN\":{}}", modules);

        assertEquals("&a{lbin}", lbin.template);
    }

    @Test
    void duplicateCaseVariantsLeaveTemplatesUntouched() {
        List<LoreModule> modules = LoreModule.defaults();
        LoreModule lbin = module(modules, "LBIN");
        lbin.template = "&a{lbin}";

        LoreStyleCodec.applyToModules(
                "{\"LBIN\":\"&b{lbin}\",\"lbin\":\"&c{lbin}\"}",
                modules);

        assertEquals("&a{lbin}", lbin.template);
    }

    @Test
    void mergeRemovesEveryCaseVariant() {
        List<LoreModule> modules = LoreModule.defaults();
        module(modules, "LBIN").template = "&a{lbin}";

        String merged = LoreStyleCodec.mergeInto(
                "{\"LBIN\":\"old\",\"lbin\":\"duplicate\",\"future\":1}",
                modules);
        JsonObject object = JsonParser.parseString(merged).getAsJsonObject();

        assertEquals(1,
                object.keySet().stream().filter(key -> key.equalsIgnoreCase("LBIN")).count());
        assertEquals("&a{lbin}", object.get("LBIN").getAsString());
        assertTrue(object.has("future"));
    }

    @Test
    void deepStyleBlobLeavesTemplatesUntouched() {
        List<LoreModule> modules = LoreModule.defaults();
        LoreModule lbin = module(modules, "LBIN");
        lbin.template = "&a{lbin}";
        String deep = "{\"next\":".repeat(80) + "0" + "}".repeat(80);

        LoreStyleCodec.applyToModules(
                "{\"LBIN\":\"&b{lbin}\",\"future\":" + deep + "}",
                modules);

        assertEquals("&a{lbin}", lbin.template);
    }

    private static LoreModule module(List<LoreModule> modules, String key) {
        return modules.stream()
                .filter(module -> key.equals(module.match))
                .findFirst()
                .orElseThrow();
    }
}
