package com.coflnet.config;

import com.coflnet.lore.LoreModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * loads and saves the lore engine settings.
 *
 * the engine is always on there is no enable toggle an enabled restyle rule
 * is what changes an items look so a master switch only confused things.
 *
 * the backend layout and the styling blob is synced as one whole
 * {@code DescriptionSetting} object through {@link com.coflnet.lore.LoreSync}:
 * a single atomic {@code /cofl lore} JSON write, and a {@code /cofl lore json}
 * read. this replaces the old chat menu scraping and the one command at a time
 * driver entirely.
 */
public class LoreManager {
    private static final AtomicLong REVISION = new AtomicLong();

    private static CoflModConfig getConfig() {
        return CoflModConfig.get();
    }

    /** the ordered module list filled with defaults on first access. */
    public static List<LoreModule> getModules() {
        CoflModConfig cfg = getConfig();
        ensureModulesExist(cfg);
        return cfg.loreModules;
    }

    public static void saveModules(List<LoreModule> modules) {
        CoflModConfig cfg = getConfig();
        cfg.loreModules = modules;
        cfg.save();
        REVISION.incrementAndGet();
        // no reload after save the in memory config stays authoritative a reload
        // here re read a disk image taken before this mutation and silently dropped
        // a concurrent edit lost update .
    }

    //  item blacklist by skyblock item id tag

    /** item ids the engine must not inject any cofl lore onto. */
    public static List<String> getItemBlacklist() {
        CoflModConfig cfg = getConfig();
        if (cfg.loreItemBlacklist == null) {
            cfg.loreItemBlacklist = new ArrayList<>();
        }
        return cfg.loreItemBlacklist;
    }

    public static void saveItemBlacklist(List<String> ids) {
        CoflModConfig cfg = getConfig();
        cfg.loreItemBlacklist = ids;
        cfg.save();
        REVISION.incrementAndGet();
        // no reload after save the in memory config stays authoritative a reload
        // here re read a disk image taken before this mutation and silently dropped
        // a concurrent edit lost update .
    }

    /** true when the given item id is blacklisted case insensitive . */
    public static boolean isItemBlacklisted(String itemId) {
        if (itemId == null) {
            return false;
        }
        String lower = itemId.toLowerCase(Locale.ROOT);
        for (String entry : getItemBlacklist()) {
            if (entry != null && !entry.isBlank() && lower.equals(entry.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    //  backend layout whole object json sync
    //
    // the lore layout and now the styling blob lives on the coflnet server as a
    // single descriptionsetting object. the backend exposes it directly as json
    //  read cofl lore json replies with a loresettings socket message
    //  carrying the whole object the reply is fed to loresync.
    //  write running cofl lore with the json as its argument saves the
    //  whole object atomically one update no re indexing no throttle .
    //
    // this replaces the old chat menu scraping one command at a time driver.
    // keeping everything on the backend means a users layout and styling follow
    // them across instances through the normal settings sync and it stays
    // backwards compatible via the backends version header. loresync owns the
    // full object and only ever mutates fields customformat so a partial
    // write can never wipe unrelated settings. see com.coflnet.lore.loresync.

    /**
     * the persisted mirror of the backend layout seeded from the last synced
     * object. used to populate the gui before a fresh read lands the writer
     * always starts from the last received object so a stale mirror can never
     * corrupt the real settings.
     */
    public static List<List<String>> getLayout() {
        CoflModConfig cfg = getConfig();
        if (cfg.loreLayout == null) {
            cfg.loreLayout = new ArrayList<>();
        }
        return cfg.loreLayout;
    }

    /**
     * stores the real layout received from the backend json. persists it as the
     * mirror the gui reads from. called on the websocket reading thread via
     * {@link com.coflnet.lore.LoreSync}.
     */
    public static void setSyncedLayout(List<List<String>> layout) {
        if (layout == null) {
            return;
        }
        CoflModConfig cfg = getConfig();
        cfg.loreLayout = layout;
        cfg.save();
        REVISION.incrementAndGet();
        // no reload after save the in memory config stays authoritative a reload
        // here re read a disk image taken before this mutation and silently dropped
        // a concurrent edit lost update .
    }

    /**
     * saves the layout to the backend as one whole object fields the current
     * module styling preserving every unrelated setting then refreshes the
     * open inventory so the change shows without a relobby.
     */
    public static void applyLayout(
            List<List<String>> target,
            List<LoreModule> modules,
            boolean templatesChanged) {
        List<List<String>> goal = new ArrayList<>();
        if (target != null) {
            for (List<String> line : target) {
                goal.add(line == null ? new ArrayList<>() : new ArrayList<>(line));
            }
        }
        com.coflnet.lore.LoreSync.save(goal, modules, templatesChanged);
    }

    public static void commitConfirmed(
            List<List<String>> layout,
            List<LoreModule> modules) {
        CoflModConfig cfg = getConfig();
        cfg.loreLayout = copyLayout(layout);
        if (modules != null) {
            cfg.loreModules = copyModules(modules);
        }
        cfg.save();
        REVISION.incrementAndGet();
    }

    public static long revision() {
        return REVISION.get();
    }

    public static boolean hasCustomTemplates() {
        for (LoreModule module : getModules()) {
            if (module == null || module.match == null
                    || module.template == null || module.template.isBlank()) {
                continue;
            }
            com.coflnet.lore.LoreSegment segment =
                    com.coflnet.lore.LoreSegment.byKey(module.match);
            if (segment != null && !segment.defaultTemplate.equals(module.template)) {
                return true;
            }
        }
        return false;
    }

    private static void ensureModulesExist(CoflModConfig cfg) {
        if (cfg.loreModules == null || cfg.loreModules.isEmpty()) {
            cfg.loreModules = LoreModule.defaults();
            return;
        }
        List<LoreModule> cleaned = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        boolean changed = false;
        for (LoreModule m : cfg.loreModules) {
            if (m == null || m.match == null || m.match.isBlank()) {
                changed = true;
                continue;
            }
            String key = m.match.toUpperCase(Locale.ROOT);
            if (!seen.add(key)) {
                changed = true;
                continue;
            }
            com.coflnet.lore.LoreSegment segment =
                    com.coflnet.lore.LoreSegment.byKey(m.match);
            if (segment != null && m.template == null) {
                m.template = segment.defaultTemplate;
                changed = true;
            }
            cleaned.add(m);
        }
        if (changed) {
            cfg.loreModules = cleaned;
        }
        for (LoreModule def : LoreModule.defaults()) {
            if (seen.add(def.match.toUpperCase(Locale.ROOT))) {
                cfg.loreModules.add(new LoreModule(def.name, def.match, def.template));
                changed = true;
            }
        }
        // self heal a historical bad default early builds shipped the craft cost
        //  clean craft module with the full craft template ...full craft cost
        //  craftcost . that makes the clean craft row emit full craft text so
        // editing it appears to do nothing. if a saved craft cost template still
        // carries that stale text restore the correct clean craft default. genuine
        // user edits anything not matching the old default are left untouched.
        for (LoreModule m : cfg.loreModules) {
            if ("CRAFT_COST".equalsIgnoreCase(m.match) && m.template != null) {
                // only heal the exact historical bad default the full craft cost template
                // wrongly shipped on the clean craft module . a genuine user edit that
                // merely mentions craftcost or full craft cost is left untouched.
                com.coflnet.lore.LoreSegment full = com.coflnet.lore.LoreSegment.byKey("FullCraftCost");
                boolean staleDefault = full != null && full.defaultTemplate.equals(m.template);
                if (staleDefault) {
                    com.coflnet.lore.LoreSegment seg = com.coflnet.lore.LoreSegment.byKey("CRAFT_COST");
                    if (seg != null && !seg.defaultTemplate.equals(m.template)) {
                        m.template = seg.defaultTemplate;
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            cfg.save();
            REVISION.incrementAndGet();
        }
    }

    private static List<List<String>> copyLayout(List<List<String>> source) {
        List<List<String>> copy = new ArrayList<>();
        if (source != null) {
            for (List<String> line : source) {
                copy.add(line == null ? new ArrayList<>() : new ArrayList<>(line));
            }
        }
        return copy;
    }

    private static List<LoreModule> copyModules(List<LoreModule> source) {
        List<LoreModule> copy = new ArrayList<>();
        if (source != null) {
            for (LoreModule module : source) {
                if (module != null) {
                    copy.add(new LoreModule(module.name, module.match, module.template));
                }
            }
        }
        return copy;
    }
}
