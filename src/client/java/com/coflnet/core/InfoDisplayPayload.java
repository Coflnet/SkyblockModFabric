package com.coflnet.core;

import java.util.Collections;
import java.util.List;

/**
 * Parsed, validated content pushed to one of the 1-3 permanent HUD "info
 * displays". Immutable value holder produced by {@link InfoDisplayPayloadParser};
 * has no Minecraft dependency so it can be built and unit tested off the game
 * thread.
 */
public final class InfoDisplayPayload {
    public final int id;
    public final String title;
    public final List<InfoDisplayLine> lines;
    public final int ttlSeconds;
    public final boolean clear;

    public InfoDisplayPayload(int id, String title, List<InfoDisplayLine> lines, int ttlSeconds, boolean clear) {
        this.id = id;
        this.title = title;
        this.lines = lines == null ? Collections.emptyList() : List.copyOf(lines);
        this.ttlSeconds = ttlSeconds;
        this.clear = clear;
    }

    /** One line of display content; either a plain string or a TextElement-like object. */
    public static final class InfoDisplayLine {
        public final String text;
        public final String hover;
        public final String onClick;

        public InfoDisplayLine(String text, String hover, String onClick) {
            this.text = text == null ? "" : text;
            this.hover = hover;
            this.onClick = onClick;
        }
    }
}
