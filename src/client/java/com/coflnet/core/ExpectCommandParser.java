package com.coflnet.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Optional;

/** Parses the websocket message {@code {"type":"expectCommand","data":"{\"command\":...,\"ttl\":60}"}}. */
public final class ExpectCommandParser {
    public static final long DEFAULT_TTL_SECONDS = 60;

    private ExpectCommandParser() {}

    public record ExpectedCommand(String command, long ttlSeconds) {}

    public static Optional<ExpectedCommand> parse(String rawJson) {
        if (rawJson == null || !rawJson.contains("expectCommand")) return Optional.empty();
        try {
            JsonObject message = JsonParser.parseString(rawJson).getAsJsonObject();
            JsonElement type = message.get("type");
            if (type == null || !type.isJsonPrimitive() || !"expectCommand".equals(type.getAsString())) return Optional.empty();
            JsonElement data = message.get("data");
            if (data == null || data.isJsonNull()) return Optional.empty();
            // data is a JSON string like other commands, but tolerate an inline object too
            JsonObject body = data.isJsonPrimitive()
                    ? JsonParser.parseString(data.getAsString()).getAsJsonObject() : data.getAsJsonObject();
            JsonElement command = body.get("command");
            if (command == null || !command.isJsonPrimitive() || command.getAsString().isBlank()) return Optional.empty();
            long ttl = DEFAULT_TTL_SECONDS;
            JsonElement ttlElement = body.get("ttl");
            if (ttlElement != null && ttlElement.isJsonPrimitive()) ttl = ttlElement.getAsLong();
            if (ttl <= 0) return Optional.empty();
            return Optional.of(new ExpectedCommand(command.getAsString(), ttl));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
