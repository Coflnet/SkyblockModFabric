package com.coflnet.config;

import com.coflnet.util.BoundedTextFile;
import com.coflnet.util.JsonNesting;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class CoflModConfig {
    private static final int MAX_CONFIG_FILE_BYTES = 1_048_576;
    private static final Gson gson = new Gson();
    private static final File CONFIG_FILE = new File(Minecraft.getInstance().gameDirectory, "config/CoflSky/coflmod.json");
    
    // Text widget position settings
    public int textWidgetOffsetX = -5;
    public int textWidgetOffsetY = 5;
    public boolean textWidgetPositionMigrated = false;
    
    // Sell protection settings
    public boolean sellProtectionEnabled = true;
    public long sellProtectionThreshold = 1000000; // Default: 1 million coins

    public boolean angryCoopProtectionEnabled = true;

    // the ordered list of lore line templates. null until first use then
    // populated from loremodule.defaults so the look matches the stock lore.
    public java.util.List<com.coflnet.lore.LoreModule> loreModules = null;

    // client side mirror of the backend lore field layout.
    public java.util.List<java.util.List<String>> loreLayout = null;

    // items the lore engine must not inject any cofl lore onto by skyblock item
    // id tag added from the held item in the lore gui . replaces the old
    // menu title blacklist.
    public java.util.List<String> loreItemBlacklist = null;

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
                String json = BoundedTextFile.readUtf8(
                        CONFIG_FILE.toPath(), MAX_CONFIG_FILE_BYTES);
                if (json != null && JsonNesting.isWithinLimit(json, 64)) {
                    CoflModConfig config = gson.fromJson(json, CoflModConfig.class);
                    if (config != null) {
                        return config;
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            System.out.println("Could not load CoflMod config: " + exception);
        }
        
        // Return default config if loading fails or file doesn't exist
        return new CoflModConfig();
    }

    private static final Object SAVE_LOCK = new Object();

    public void save() {
        saveAndReport();
    }

    boolean saveAndReport() {
        synchronized (SAVE_LOCK) {
            File tmp = new File(CONFIG_FILE.getParentFile(), CONFIG_FILE.getName() + ".tmp");
            try {
                CONFIG_FILE.getParentFile().mkdirs();
                try (FileWriter writer = new FileWriter(tmp)) {
                    gson.toJson(this, writer);
                }
                try {
                    java.nio.file.Files.move(tmp.toPath(), CONFIG_FILE.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException nonAtomic) {
                    java.nio.file.Files.move(tmp.toPath(), CONFIG_FILE.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    java.nio.file.Files.deleteIfExists(tmp.toPath());
                } catch (IOException cleanupException) {
                    System.out.println("Could not clean up temporary CoflMod config: " + cleanupException);
                }
                return false;
            }
        }
    }
}
