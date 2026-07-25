package com.coflnet.config;

import com.coflnet.util.JsonNesting;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;

import java.io.File;
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

    // Developer mode: when on, container screens show a "Copy Dump" button that
    // copies the open container's title/size/slots to the clipboard. Off by default.
    public boolean devMode = false;

    // Trade overlay: when on, the SkyCofl TradeGUI replaces the Hypixel trade
    // window. Independent of dev mode (which only controls the Copy Dump button).
    public boolean tradeGuiEnabled = false;

    // TradeGUI item-list column count (1-3). Persisted so the user's choice
    // sticks across trades and restarts. Default 1 (the original look).
    public int tradeListColumns = 1;

    public boolean flipHudEnabled = true;
    public int flipHudX = -1;
    public int flipHudY = 8;
    
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
                String json = java.nio.file.Files.readString(CONFIG_FILE.toPath());
                if (JsonNesting.isWithinLimit(json, 64)) {
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
