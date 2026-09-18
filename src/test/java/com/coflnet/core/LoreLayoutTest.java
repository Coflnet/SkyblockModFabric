package com.coflnet.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LoreLayoutTest {
    @Test void decodesTheLoreCommandsNestedJsonResponse() {
        var gson = new com.google.gson.Gson();
        var original = layout().settings();
        var wireData = new com.google.gson.JsonPrimitive(gson.toJson(original.toString()));
        assertEquals(original, LoreLayout.parseResponse(wireData));
        assertEquals(original, LoreLayout.parseResponse(new com.google.gson.JsonPrimitive(original.toString())));
        assertEquals(original, LoreLayout.parseResponse(original));
    }

    private LoreLayout layout() {
        return new LoreLayout(JsonParser.parseString("""
                {"Fields":[["LBIN","MEDIAN","VOLUME"],["BazaarBuy"]],
                 "Disabled":true,"BazaarBookmarks":["SHARD_HIDEONWALL"],"futureSetting":{"enabled":true}}
                """).getAsJsonObject());
    }

    @Test void reordersInBothDirectionsWithinARow() {
        var draft = layout();
        draft.move(0, 0, 0, 3);
        assertEquals(List.of("MEDIAN", "VOLUME", "LBIN"), draft.rows.getFirst());
        draft.move(0, 2, 0, 0);
        assertEquals(List.of("LBIN", "MEDIAN", "VOLUME"), draft.rows.getFirst());
    }

    @Test void movesAcrossRowsAndToNewRowWithoutDroppingFields() {
        var draft = layout();
        draft.move(1, 0, 0, 1);
        assertEquals(List.of(List.of("LBIN", "BazaarBuy", "MEDIAN", "VOLUME")), draft.rows);
        draft.move(0, 1, 1, 0);
        assertEquals(List.of(List.of("LBIN", "MEDIAN", "VOLUME"), List.of("BazaarBuy")), draft.rows);
        draft.move(1, 0, 2, 0);
        assertEquals(2, draft.rows.size());
        assertEquals(List.of("BazaarBuy"), draft.rows.getLast());
    }

    @Test void clearPreservesUnrelatedSettingsAndDoesNotMutateSource() {
        var original = layout().settings();
        var draft = new LoreLayout(original);
        draft.rows.clear();
        var uploaded = draft.settings();
        assertTrue(uploaded.getAsJsonArray("Fields").isEmpty());
        assertEquals(2, original.getAsJsonArray("Fields").size());
        assertEquals(original.get("Disabled"), uploaded.get("Disabled"));
        assertEquals(original.get("BazaarBookmarks"), uploaded.get("BazaarBookmarks"));
        assertEquals(original.get("futureSetting"), uploaded.get("futureSetting"));
    }

    @Test void supportsCamelCaseAndUnknownFieldsWithoutReplacingSettings() {
        var draft = new LoreLayout(JsonParser.parseString("{\"fields\":[[\"FutureField\"]],\"disabled\":false}").getAsJsonObject());
        draft.add("DefaultLore", 1, 0);
        assertEquals("FutureField", draft.settings().getAsJsonArray("fields").get(0).getAsJsonArray().get(0).getAsString());
        assertFalse(draft.settings().has("Fields"));
        draft.remove(0, 0);
        assertEquals(List.of(List.of("DefaultLore")), draft.rows);
    }

    @Test void malformedServerDataCannotBecomeAnEmptyUpload() {
        assertThrows(IllegalArgumentException.class, () -> new LoreLayout(JsonParser.parseString("{}").getAsJsonObject()));
    }
}
