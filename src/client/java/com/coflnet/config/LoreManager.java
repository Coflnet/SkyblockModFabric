package com.coflnet.config;

import com.coflnet.lore.LoreModule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads and saves the lore engine settings, mirroring {@link TradeGuiManager}.
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
    private static CoflModConfig config = null;

    private static void ensureConfig() {
        if (config == null) {
            config = CoflModConfig.load();
        }
    }

    public static void reloadConfig() {
        config = CoflModConfig.load();
    }

    public static CoflModConfig getConfig() {
        ensureConfig();
        return config;
    }

    /** the engine is always active an enabled rule is what changes an item. */
    public static boolean isEnabled() {
        return true;
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
        reloadConfig();
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
        reloadConfig();
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

    //  purchase history what you paid 

    public static Map<String, Long> getPurchases() {
        CoflModConfig cfg = getConfig();
        if (cfg.lorePurchases == null) {
            cfg.lorePurchases = new LinkedHashMap<>();
        }
        return cfg.lorePurchases;
    }

    /** records the coins paid for an item id from an ah purchase chat line . */
    public static void recordPurchase(String itemId, long coins) {
        if (itemId == null || itemId.isBlank() || coins <= 0) {
            return;
        }
        CoflModConfig cfg = getConfig();
        if (cfg.lorePurchases == null) {
            cfg.lorePurchases = new LinkedHashMap<>();
        }
        cfg.lorePurchases.put(itemId, coins);
        cfg.save();
        reloadConfig();
    }

    /** the coins paid for an item id or null if never purchased via ah. */
    public static Long purchasePrice(String itemId) {
        if (itemId == null) {
            return null;
        }
        return getPurchases().get(itemId);
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

    public static boolean isLayoutCaptured() {
        return com.coflnet.lore.LoreSync.hasReceived();
    }

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
        reloadConfig();
    }

    /**
     * saves the layout to the backend as one whole object fields the current
     * module styling preserving every unrelated setting then refreshes the
     * open inventory so the change shows without a relobby.
     */
    public static void applyLayout(List<List<String>> target) {
        List<List<String>> goal = new ArrayList<>();
        if (target != null) {
            for (List<String> line : target) {
                goal.add(line == null ? new ArrayList<>() : new ArrayList<>(line));
            }
        }
        // persist the goal locally so a gui reopen reflects the users intent.
        CoflModConfig cfg = getConfig();
        cfg.loreLayout = goal;
        cfg.save();
        reloadConfig();

        com.coflnet.lore.LoreSync.save(goal, getModules());
    }

    private static void ensureModulesExist(CoflModConfig cfg) {
        if (cfg.loreModules == null || cfg.loreModules.isEmpty()) {
            cfg.loreModules = LoreModule.defaults();
            return;
        }
        // the engine now keys modules by segment key e.g. lbin fullcraftcost 
        // for per field substitution. older configs used line class keys lbin 
        // median craft ... and a null match purchased for module which
        // the new engine cant use. detect a pre segment config and rebuild it
        // from defaults so the user gets working per field modules. templates
        // were the stock look anyway so nothing custom is meaningfully lost a
        // user who hand edited can re edit per field in the new gui. 
        boolean segmentScheme = true;
        for (LoreModule m : cfg.loreModules) {
            if (m == null || m.match == null || com.coflnet.lore.LoreSegment.byKey(m.match) == null) {
                segmentScheme = false;
                break;
            }
        }
        if (!segmentScheme) {
            cfg.loreModules = LoreModule.defaults();
            cfg.save();
            return;
        }
        // same scheme merge in any segment modules the saved config predates 
        // without disturbing the users customised templates.
        java.util.Set<String> have = new java.util.HashSet<>();
        for (LoreModule m : cfg.loreModules) {
            have.add(m.match.toUpperCase(Locale.ROOT));
        }
        boolean changed = false;
        for (LoreModule def : LoreModule.defaults()) {
            if (!have.contains(def.match.toUpperCase(Locale.ROOT))) {
                cfg.loreModules.add(new LoreModule(def.name, def.match, def.template, def.enabled));
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
                String t = m.template.toLowerCase(Locale.ROOT);
                boolean staleDefault = t.contains("full craft cost:")
                        || t.contains("{craftcost}");   // wrong token for the clean craft field
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
        }
    }
}
