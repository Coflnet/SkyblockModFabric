package com.coflnet.lore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescriptionSettingsTest {
    @Test
    void acceptsAnExplicitlyEmptyLayout() {
        DescriptionSettings settings = DescriptionSettings.parse("""
                {
                  "Fields": [],
                  "CustomFormat": null,
                  "Disabled": true
                }
                """);

        assertNotNull(settings);
        assertTrue(settings.hasFields());
        assertEquals(List.of(), settings.getFields());
    }

    @Test
    void acceptsACompleteBackendSnapshot() {
        DescriptionSettings settings = DescriptionSettings.parse(completeSnapshot().toString());

        assertNotNull(settings);
        assertTrue(settings.isCompleteSnapshot());
    }

    @Test
    void rejectsPartialOrInvalidBackendSnapshots() {
        JsonObject missingJson = completeSnapshot();
        missingJson.remove("HighlightInfo");
        JsonObject fractionalJson = completeSnapshot();
        fractionalJson.addProperty("LowballLbinUndercut", 1.5);
        JsonObject outOfRangeJson = completeSnapshot();
        outOfRangeJson.addProperty("LowballLbinUndercut", 256);
        JsonObject duplicateJson = completeSnapshot();
        duplicateJson.add("fields", JsonParser.parseString("[[\"LBIN\"]]"));
        DescriptionSettings missing = DescriptionSettings.parse(missingJson.toString());
        DescriptionSettings fractional = DescriptionSettings.parse(fractionalJson.toString());
        DescriptionSettings outOfRange = DescriptionSettings.parse(outOfRangeJson.toString());
        DescriptionSettings duplicate = DescriptionSettings.parse(duplicateJson.toString());

        assertNotNull(missing);
        assertNotNull(fractional);
        assertNotNull(outOfRange);
        assertNotNull(duplicate);
        assertFalse(missing.isCompleteSnapshot());
        assertFalse(fractional.isCompleteSnapshot());
        assertFalse(outOfRange.isCompleteSnapshot());
        assertFalse(duplicate.isCompleteSnapshot());
    }

    @Test
    void rejectsTheOldFourFieldPartialSnapshot() {
        DescriptionSettings settings = DescriptionSettings.parse("""
                {
                  "Fields": [],
                  "CustomFormat": null,
                  "Disabled": false,
                  "LowballLbinUndercut": 10
                }
                """);

        assertNotNull(settings);
        assertFalse(settings.isCompleteSnapshot());
    }

    @Test
    void rejectsDeepOrOversizedJsonBeforeItCanBeCopied() {
        String deep = "{\"value\":".repeat(80) + "0" + "}".repeat(80);
        assertNull(DescriptionSettings.parse("{\"future\":" + deep + "}"));
        assertNull(DescriptionSettings.parse(
                "{\"future\":\"" + "x".repeat(LoreSettingsPayload.MAX_PAYLOAD_LENGTH) + "\"}"));
    }

    @Test
    void rejectsMalformedFieldArraysWithoutTreatingThemAsEmpty() {
        DescriptionSettings settings = DescriptionSettings.parse("""
                {
                  "Fields": [1, ["LBIN"]]
                }
                """);

        assertNotNull(settings);
        assertFalse(settings.hasFields());
    }

    @Test
    void rejectsOversizedFieldNames() {
        DescriptionSettings settings = DescriptionSettings.parse("""
                {
                  "Fields": [["%s"]]
                }
                """.formatted("x".repeat(129)));

        assertNotNull(settings);
        assertFalse(settings.hasFields());
    }

    @Test
    void preservesUnrelatedBackendSettingsDuringSave() {
        DescriptionSettings settings = DescriptionSettings.parse("""
                {
                  "fields": [["LBIN"]],
                  "customFormat": null,
                  "disabled": true,
                  "futureSetting": {"value": 7}
                }
                """);
        assertNotNull(settings);
        Map<String, String> before = settings.otherMembers();

        DescriptionSettings copy = settings.copy();
        copy.setFields(List.of(List.of("MEDIAN", "VOLUME")));
        copy.setCustomFormat("{\"MEDIAN\":\"&b{median}\"}");

        assertEquals(before, copy.otherMembers());
        assertEquals(List.of(List.of("MEDIAN", "VOLUME")), copy.getFields());
        assertEquals("{\"MEDIAN\":\"&b{median}\"}", copy.getCustomFormat());
    }

    @Test
    void removesDuplicateCaseVariantsWhenWritingOwnedMembers() {
        DescriptionSettings settings = DescriptionSettings.parse("""
                {
                  "Fields": [["LBIN"]],
                  "fields": [["MEDIAN"]],
                  "CustomFormat": null,
                  "customFormat": "old"
                }
                """);
        assertNotNull(settings);

        settings.setFields(List.of());
        settings.setCustomFormat("new");
        JsonObject json = JsonParser.parseString(settings.toJson()).getAsJsonObject();

        assertEquals(1, json.keySet().stream().filter(key -> key.equalsIgnoreCase("fields")).count());
        assertEquals(1, json.keySet().stream().filter(key -> key.equalsIgnoreCase("customFormat")).count());
    }

    private static JsonObject completeSnapshot() {
        return JsonParser.parseString("""
                {
                  "Fields": [],
                  "HighlightFilterMatch": false,
                  "MinProfitForHighlight": 0,
                  "DisableHighlighting": false,
                  "DisableSuggestions": false,
                  "DisableInfoIn": [],
                  "BazaarBookmarks": [],
                  "Disabled": false,
                  "LowballMedUndercut": 0,
                  "LowballLbinUndercut": 10,
                  "PreferLbinInSuggestions": false,
                  "SuggestQuicksell": false,
                  "ReplaceGrayWith": null,
                  "ReplaceAquaWith": null,
                  "ReplaceYellowWith": null,
                  "ReplaceGoldWith": null,
                  "ReplaceWhiteWith": null,
                  "NoCookie": false,
                  "BuyOrderPrices": false,
                  "DisableAuctionStartedTime": false,
                  "LowballNonExactExtraPct": 2,
                  "LowballWorstCaseExtraPct": 5,
                  "LowballHideBreakdown": false,
                  "LowballHideWorstCase": false,
                  "CustomFormat": null,
                  "HighlightInfo": null
                }
                """).getAsJsonObject();
    }
}
