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
    
    public void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            FileWriter writer = new FileWriter(CONFIG_FILE);
            gson.toJson(this, writer);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
