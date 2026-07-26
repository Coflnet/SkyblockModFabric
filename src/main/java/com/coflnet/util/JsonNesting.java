package com.coflnet.util;

public final class JsonNesting {
    private JsonNesting() {
    }

    public static boolean isWithinLimit(String json, int maximumDepth) {
        if (json == null || maximumDepth < 1) {
            return false;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char character = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }
            if (character == '"') {
                inString = true;
            } else if (character == '{' || character == '[') {
                if (++depth > maximumDepth) {
                    return false;
                }
            } else if ((character == '}' || character == ']') && --depth < 0) {
                return false;
            }
        }
        return depth == 0 && !inString;
    }
}
