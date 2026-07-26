package com.coflnet.lore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PetInfoParserTest {
    @Test
    void parsesValidPetType() {
        assertEquals("BLUE_WHALE", PetInfoParser.type("{\"type\":\"BLUE_WHALE\"}"));
    }

    @Test
    void rejectsOversizedPetInfo() {
        String value = "{\"type\":\"" + "A".repeat(PetInfoParser.MAX_PET_INFO_LENGTH) + "\"}";

        assertNull(PetInfoParser.type(value));
    }

    @Test
    void rejectsDeepPetInfo() {
        String value = "{\"next\":".repeat(80) + "{\"type\":\"SHEEP\"}" + "}".repeat(80);

        assertNull(PetInfoParser.type(value));
    }

    @Test
    void rejectsNonStringAndUnsafePetTypes() {
        assertNull(PetInfoParser.type("{\"type\":12}"));
        assertNull(PetInfoParser.type("{\"type\":\"../SHEEP\"}"));
    }
}
