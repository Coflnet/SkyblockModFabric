package com.coflnet.config;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class CoflModConfig {
    private static final Gson gson = new Gson();
    private static final File CONFIG_FILE = new File(Minecraft.getInstance().gameDirectory, "config/CoflSky/coflmod.json");
    
    // Text widget position settings
    public int textWidgetOffsetX = -5;
    public int textWidgetOffsetY = 5;
    
    // Sell protection settings
    public boolean sellProtectionEnabled = true;
    public long sellProtectionThreshold = 1000000; // Default: 1 million coins

    public boolean angryCoopProtectionEnabled = true;

    // the ordered list of lore line templates. null until first use then
    // populated from loremodule.defaults so the look matches the stock lore.
    public java.util.List<com.coflnet.lore.LoreModule> loreModules = null;

    // menus where the lore engine should not inject by container title
    // substring case insensitive . lets the user blacklist specific guis.
    public java.util.List<String> loreBlacklist = null;

    // client side mirror of the backend lore field layout the thing the
    //  cofl lore chat menu edits . each inner list is one 0 indexed line and
    // holds the backend field keys on that line in order. the gui edits this
    // mirror and drives cofl lore add rm up down to keep the backend in sync 
    // since the backend layout is not readable from the client. null until the
    // layout gui is first opened then seeded from the observed default.
    public java.util.List<java.util.List<String>> loreLayout = null;

    // items the lore engine must not inject any cofl lore onto by skyblock item
    // id tag added from the held item in the lore gui . replaces the old
    // menu title blacklist.
    public java.util.List<String> loreItemBlacklist = null;

    // your own purchase history skyblock item id coins you paid. captured
    // from you purchased x for y coins auction house chat lines and shown via
    // the purchased token purchased for lore module so you dont resell
    // an item below what you paid.
    public java.util.Map<String, Long> lorePurchases = null;

    // Developer mode: when on, container screens show a "Copy Dump" button that
    // copies the open container's title/size/slots to the clipboard. Off by default.
    public boolean devMode = false;

    // Trade overlay: when on, the SkyCofl TradeGUI replaces the Hypixel trade
    // window. Independent of dev mode (which only controls the Copy Dump button).
    public boolean tradeGuiEnabled = false;

    // TradeGUI item-list column count (1-3). Persisted so the user's choice
    // sticks across trades and restarts. Default 1 (the original look).
    public int tradeListColumns = 1;
    
    // Single shared, lazily-loaded instance. All managers (DevManager,
    // TradeGuiManager, SellProtectionManager, ...) delegate to this so there is
    // exactly one in-memory copy of the config. Without it, each manager cached
    // its own copy and a save() from one could clobber a fresh change made
    // through another.
    private static CoflModConfig instance = null;

    /** Returns the shared config instance, loading it from disk on first use. */
    public static synchronized CoflModConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /** Discards the cached instance and re-reads it from disk. */
    public static synchronized CoflModConfig reload() {
        instance = load();
        return instance;
    }

    public static CoflModConfig load() {
        try {
            if (CONFIG_FILE.exists()) {
                FileReader reader = new FileReader(CONFIG_FILE);
                CoflModConfig config = gson.fromJson(reader, CoflModConfig.class);
                reader.close();
                if (config != null) {
                    return config;
                }
            }
        } catch (IOException e) {
            // Use default values if loading fails
        }
        
        // Return default config if loading fails or file doesn't exist
        return new CoflModConfig();
    }
    
    // serialises saves and writes atomically. the config is written from several
    // threads chat purchases the websocket lore sync the gui so a plain truncating
    // writer could interleave and corrupt the file wiping every setting on next load.
    private static final Object SAVE_LOCK = new Object();

    public void save() {
        synchronized (SAVE_LOCK) {
            try {
                CONFIG_FILE.getParentFile().mkdirs();
                File tmp = new File(CONFIG_FILE.getParentFile(), CONFIG_FILE.getName() + ".tmp");
                try (FileWriter writer = new FileWriter(tmp)) {
                    gson.toJson(this, writer);
                }
                // atomic replace so a reader never sees a half written file and two
                // overlapping writers cannot corrupt the config.
                try {
                    java.nio.file.Files.move(tmp.toPath(), CONFIG_FILE.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException nonAtomic) {
                    java.nio.file.Files.move(tmp.toPath(), CONFIG_FILE.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
