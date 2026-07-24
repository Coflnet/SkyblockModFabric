package com.coflnet.lore;

import java.util.ArrayList;
import java.util.List;

/**
 * a client side restyle rule for one class of backend lore line.
 *
 * the coflnet backend owns which fields appear and on which line configured via
 * the cofl lore add rm up down left commands . the client engine never invents
 * or removes fields. it only restyles the look of line classes it understands
 * when a backend line matches {@link #match}, the line is
 * re rendered from {@link #template}; every other backend line passes through
 * unchanged so the remaining backend fields are never lost.
 *
 * The {@link #match} key is one of the fields from {@link LoreSegment}.
 */
public class LoreModule {
    public String name;
    public String match;      // lore segment key.
    public String template;

    public LoreModule() {
    }

    public LoreModule(String name, String match, String template) {
        this.name = name;
        this.match = match;
        this.template = template;
    }

    /**
     * Default restyle rules, one per {@link LoreSegment}. {@link #match} is the
     * segment key the backend field key e.g. lbin not a line class the
     * engine substitutes each fields segment independently. templates reproduce
     * the stock look. a module applies whenever its template is non blank
     *  clearing the template box disables that field . built directly from the
     * segment catalog so the two never drift.
     */
    public static List<LoreModule> defaults() {
        List<LoreModule> list = new ArrayList<>();
        for (LoreSegment seg : LoreSegment.ALL) {
            list.add(new LoreModule(seg.display, seg.key, seg.defaultTemplate));
        }
        return list;
    }
}
