package com.coflnet.lore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
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
    void rejectsInvalidBackendMemberTypes() {
        for (String member : List.of(
                "HighlightFilterMatch",
                "DisableHighlighting",
                "DisableSuggestions",
                "Disabled",
                "PreferLbinInSuggestions",
                "SuggestQuicksell",
                "NoCookie",
                "BuyOrderPrices",
                "DisableAuctionStartedTime",
                "LowballHideBreakdown",
                "LowballHideWorstCase")) {
            assertInvalidMember(member, new JsonObject());
        }
        assertInvalidMember("MinProfitForHighlight", new JsonObject());
        assertInvalidMember("MinProfitForHighlight", new JsonPrimitive(1.5));
        for (String member : List.of(
                "LowballMedUndercut",
                "LowballLbinUndercut",
                "LowballNonExactExtraPct",
                "LowballWorstCaseExtraPct")) {
            assertInvalidMember(member, new JsonObject());
            assertInvalidMember(member, new JsonPrimitive(-1));
            assertInvalidMember(member, new JsonPrimitive(256));
        }
        for (String member : List.of(
                "ReplaceGrayWith",
                "ReplaceAquaWith",
                "ReplaceYellowWith",
                "ReplaceGoldWith",
                "ReplaceWhiteWith",
                "CustomFormat")) {
            assertInvalidMember(member, new JsonObject());
        }
        for (String member : List.of("DisableInfoIn", "BazaarBookmarks")) {
            assertInvalidMember(member, new JsonObject());
            assertInvalidMember(member, JsonParser.parseString("[1]"));
        }
        assertInvalidMember("HighlightInfo", new JsonPrimitive("invalid"));
        assertInvalidMember("HighlightInfo", new JsonObject());
    }

    @Test
    void acceptsAValidPopulatedHighlightInfo() {
        JsonObject snapshot = completeSnapshot();
        snapshot.add("HighlightInfo", JsonParser.parseString("""
                {
                  "Position": {"X": 1, "Y": 2, "Z": 3},
                  "HexColor": "#00FF00",
                  "SlotId": -1,
                  "Chestname": "Highlight"
                }
                """));

        DescriptionSettings settings = DescriptionSettings.parse(snapshot.toString());

        assertNotNull(settings);
        assertTrue(settings.isCompleteSnapshot());
    }

    @Test
    void rejectsInvalidHighlightPositionsAndSlotIds() {
        for (String position : List.of(
                "{}",
                "{\"X\":1,\"Y\":2}",
                "{\"X\":1,\"x\":2,\"Y\":2,\"Z\":3}",
                "{\"X\":{},\"Y\":2,\"Z\":3}",
                "{\"X\":1,\"Y\":false,\"Z\":3}",
                "{\"X\":1,\"Y\":2,\"Z\":\"bad\"}",
                "{\"X\":1e309,\"Y\":2,\"Z\":3}")) {
            assertInvalidMember(
                    "HighlightInfo",
                    highlightInfo(JsonParser.parseString(position), new JsonPrimitive(-1)));
        }
        for (com.google.gson.JsonElement slot : List.of(
                new JsonPrimitive(1.5),
                new JsonPrimitive((long) Integer.MIN_VALUE - 1),
                new JsonPrimitive((long) Integer.MAX_VALUE + 1))) {
            assertInvalidMember(
                    "HighlightInfo",
                    highlightInfo(JsonParser.parseString("{\"X\":1,\"Y\":2,\"Z\":3}"), slot));
        }
    }

    @Test
    void acceptsFiniteFractionalAndBoundaryHighlightPositions() {
        for (String position : List.of(
                "{\"X\":1.5,\"Y\":-2.25,\"Z\":0}",
                "{\"X\":1.7976931348623157e308,\"Y\":-1.7976931348623157e308,\"Z\":0}")) {
            JsonObject snapshot = completeSnapshot();
            snapshot.add(
                    "HighlightInfo",
                    highlightInfo(JsonParser.parseString(position), new JsonPrimitive(-1)));
            DescriptionSettings settings = DescriptionSettings.parse(snapshot.toString());

            assertNotNull(settings);
            assertTrue(settings.isCompleteSnapshot());
        }
        for (int slot : List.of(Integer.MIN_VALUE, Integer.MAX_VALUE)) {
            JsonObject snapshot = completeSnapshot();
            snapshot.add(
                    "HighlightInfo",
                    highlightInfo(
                            JsonParser.parseString("{\"X\":1,\"Y\":2,\"Z\":3}"),
                            new JsonPrimitive(slot)));
            DescriptionSettings settings = DescriptionSettings.parse(snapshot.toString());

            assertNotNull(settings);
            assertTrue(settings.isCompleteSnapshot());
        }
        JsonObject nullablePosition = completeSnapshot();
        nullablePosition.add(
                "HighlightInfo",
                highlightInfo(com.google.gson.JsonNull.INSTANCE, new JsonPrimitive(-1)));
        DescriptionSettings nullable = DescriptionSettings.parse(nullablePosition.toString());

        assertNotNull(nullable);
        assertTrue(nullable.isCompleteSnapshot());
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
    void acceptsEveryCurrentDescriptionFieldAndRejectsUnknownOnes() {
        JsonObject validSnapshot = completeSnapshot();
        validSnapshot.add("Fields", JsonParser.parseString("""
                [[
                  "NONE", "LBIN", "LBIN_KEY", "MEDIAN", "MEDIAN_KEY", "VOLUME",
                  "TAG", "CRAFT_COST", "BazaarBuy", "BazaarSell", "PRICE_PAID",
                  "ITEM_KEY", "EnchantCost", "GemValue", "SpentOnAhFees",
                  "KatUpgradeCost", "InstaSellPrice", "ModifierCost",
                  "FullCraftCost", "ModifierCostList", "FinderEstimates",
                  "Volatility", "LastSoldFor", "TimeToSell", "NpcSellPrice",
                  "ColorCode", "DefaultLore", "AiEstimate", "BAZAAR_COST"
                ]]
                """));
        DescriptionSettings valid = DescriptionSettings.parse(validSnapshot.toString());

        JsonObject unknownSnapshot = completeSnapshot();
        unknownSnapshot.add("Fields", JsonParser.parseString("[[\"NOT_A_DESCRIPTION_FIELD\"]]"));
        DescriptionSettings unknown = DescriptionSettings.parse(unknownSnapshot.toString());

        assertNotNull(valid);
        assertTrue(valid.isCompleteSnapshot());
        assertNotNull(unknown);
        assertFalse(unknown.isCompleteSnapshot());
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

    private static void assertInvalidMember(String name, com.google.gson.JsonElement value) {
        JsonObject snapshot = completeSnapshot();
        snapshot.add(name, value);
        DescriptionSettings settings = DescriptionSettings.parse(snapshot.toString());

        assertNotNull(settings);
        assertFalse(settings.isCompleteSnapshot(), name);
    }

    private static JsonObject highlightInfo(
            com.google.gson.JsonElement position,
            com.google.gson.JsonElement slot) {
        JsonObject info = new JsonObject();
        info.add("Position", position);
        info.addProperty("HexColor", "#00FF00");
        info.add("SlotId", slot);
        info.addProperty("Chestname", "Highlight");
        return info;
    }
}
