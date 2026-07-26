package com.coflnet.lore;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class PetInfoParser {
    static final int MAX_PET_INFO_LENGTH = 4_096;
    private static final int MAX_TYPE_LENGTH = 128;

    private PetInfoParser() {
    }

    public static String type(String petInfo) {
        JsonObject object = BoundedJson.parseObject(petInfo, MAX_PET_INFO_LENGTH);
        if (object == null) {
            return null;
        }
        JsonElement type = object.get("type");
        if (type == null
                || !type.isJsonPrimitive()
                || !type.getAsJsonPrimitive().isString()) {
            return null;
        }
        String value = type.getAsString().strip();
        if (value.isBlank() || value.length() > MAX_TYPE_LENGTH) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (!(character >= 'A' && character <= 'Z')
                    && !(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '_'
                    && character != '-') {
                return null;
            }
        }
        return value;
    }
}
