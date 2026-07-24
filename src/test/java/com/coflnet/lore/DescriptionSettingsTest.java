package com.coflnet.lore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        DescriptionSettings settings = DescriptionSettings.parse("""
                {
                  "Fields": [],
                  "CustomFormat": null,
                  "Disabled": true,
                  "LowballLbinUndercut": 12
                }
                """);

        assertNotNull(settings);
        assertTrue(settings.isCompleteSnapshot());
    }

    @Test
    void rejectsPartialOrInvalidBackendSnapshots() {
        DescriptionSettings missing = DescriptionSettings.parse("""
                {"Fields": [], "CustomFormat": null, "Disabled": true}
                """);
        DescriptionSettings fractional = DescriptionSettings.parse("""
                {
                  "Fields": [],
                  "CustomFormat": null,
                  "Disabled": true,
                  "LowballLbinUndercut": 1.5
                }
                """);
        DescriptionSettings outOfRange = DescriptionSettings.parse("""
                {
                  "Fields": [],
                  "CustomFormat": null,
                  "Disabled": true,
                  "LowballLbinUndercut": 256
                }
                """);
        DescriptionSettings duplicate = DescriptionSettings.parse("""
                {
                  "Fields": [],
                  "fields": [["LBIN"]],
                  "CustomFormat": null,
                  "Disabled": true,
                  "LowballLbinUndercut": 1
                }
                """);

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
}
