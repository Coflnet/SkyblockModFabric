package com.coflnet.core;

import CoflCore.configuration.Config;

import java.net.URI;

/** Explicit loopback-only endpoint seam for deterministic client rehearsals and tests. */
public final class DescriptionEndpointOverride {
    public static final String PROPERTY = "coflnet.description.base-url";

    private DescriptionEndpointOverride() {
    }

    public static void applySystemProperty() {
        String configured = System.getProperty(PROPERTY);
        if (configured == null || configured.isBlank()) {
            return;
        }
        URI endpoint = URI.create(configured);
        String host = endpoint.getHost();
        boolean loopback = "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host);
        String path = endpoint.getPath();
        if (!"http".equalsIgnoreCase(endpoint.getScheme()) || !loopback || endpoint.getPort() < 1
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                || (path != null && !path.isEmpty() && !path.equals("/"))) {
            throw new IllegalArgumentException(PROPERTY + " must be an HTTP loopback origin with an explicit port");
        }
        Config.BaseUrl = configured.endsWith("/")
                ? configured.substring(0, configured.length() - 1)
                : configured;
    }
}
