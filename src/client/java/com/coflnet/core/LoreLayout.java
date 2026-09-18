package com.coflnet.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;

/** Editable copy of the server settings; unrelated (including future) settings survive uploads. */
public final class LoreLayout {
    public static final List<String> FIELDS = List.of("LBIN", "MEDIAN", "BazaarBuy", "BazaarSell",
            "VOLUME", "CRAFT_COST", "FullCraftCost", "LBIN_KEY", "MEDIAN_KEY", "ITEM_KEY", "TAG",
            "EnchantCost", "GemValue", "ModifierCost", "ModifierCostList", "SpentOnAhFees",
            "KatUpgradeCost", "InstaSellPrice", "PRICE_PAID", "FinderEstimates", "Volatility",
            "LastSoldFor", "TimeToSell", "NpcSellPrice", "ColorCode", "DefaultLore", "AiEstimate", "NONE");
    private final JsonObject original;
    public final List<List<String>> rows = new ArrayList<>();

    /** Response.Create serializes the already serialized settings as a JSON string. */
    public static JsonObject parseResponse(JsonElement data) {
        for (int i = 0; i < 2 && data.isJsonPrimitive(); i++)
            data = JsonParser.parseString(data.getAsString());
        return data.getAsJsonObject();
    }

    public LoreLayout(JsonObject settings) {
        original = settings.deepCopy();
        var fields = original.get(key("Fields"));
        if (fields == null || !fields.isJsonArray())
            throw new IllegalArgumentException("The server did not return a lore layout");
        for (var row : fields.getAsJsonArray()) {
            List<String> values = new ArrayList<>();
            for (var field : row.getAsJsonArray()) values.add(field.getAsString());
            rows.add(values);
        }
    }

    private String key(String name) {
        return original.keySet().stream().filter(k -> k.equalsIgnoreCase(name)).findFirst().orElse(name);
    }

    public JsonObject settings() {
        JsonObject result = original.deepCopy();
        JsonArray fields = new JsonArray();
        for (var row : rows) {
            JsonArray values = new JsonArray();
            row.forEach(values::add);
            fields.add(values);
        }
        result.add(key("Fields"), fields);
        return result;
    }

    public void add(String field, int row, int index) {
        if (row == rows.size()) rows.add(new ArrayList<>());
        rows.get(row).add(index, field);
    }

    /** Destination indexes refer to the layout before the source is removed. */
    public int move(int row, int index, int targetRow, int targetIndex) {
        String field = rows.get(row).remove(index);
        if (row == targetRow && index < targetIndex) targetIndex--;
        add(field, targetRow, targetIndex);
        if (rows.get(row).isEmpty()) {
            rows.remove(row);
            if (row < targetRow) targetRow--;
        }
        return targetRow;
    }

    public void remove(int row, int index) {
        rows.get(row).remove(index);
        if (rows.get(row).isEmpty()) rows.remove(row);
    }
}
