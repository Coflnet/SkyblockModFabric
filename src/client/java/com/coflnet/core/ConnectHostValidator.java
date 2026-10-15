package com.coflnet.core;

import java.net.URI;
import java.util.Locale;

public final class ConnectHostValidator {
    private ConnectHostValidator() {
    }

    public static String extractHost(String destination) {
        String normalized = normalize(destination);
        if (normalized == null) return null;
        try {
            URI uri = URI.create(normalized.contains("://") ? normalized : "wss://" + normalized);
            String parsed = normalize(uri.getHost());
            if (parsed != null) return parsed;
        } catch (IllegalArgumentException ignored) {
        }
        String candidate = normalized;
        for (char separator : new char[]{'/', '?', '#'}) {
            int index = candidate.indexOf(separator);
            if (index >= 0) candidate = candidate.substring(0, index);
        }
        int credentials = candidate.lastIndexOf('@');
        if (credentials >= 0) candidate = candidate.substring(credentials + 1);
        if (candidate.startsWith("[")) {
            int closingBracket = candidate.indexOf(']');
            if (closingBracket > 0) candidate = candidate.substring(1, closingBracket);
        } else {
            int port = candidate.indexOf(':');
            if (port >= 0) candidate = candidate.substring(0, port);
        }
        return normalize(candidate);
    }

    public static boolean isTrusted(String host) {
        String normalized = normalize(host);
        return normalized != null && (normalized.equals("coflnet.com") || normalized.endsWith(".coflnet.com"));
    }

    public static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized.isEmpty() ? null : normalized;
    }
}
