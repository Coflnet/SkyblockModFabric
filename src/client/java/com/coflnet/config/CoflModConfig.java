package com.coflnet.config;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    // Permanent, backend-updatable HUD "info displays" (1-3, fixed slots).
    // Always exactly 3 entries; see ensureInfoDisplays().
    public List<InfoDisplaySettings> infoDisplays;

    // When true (default), the info displays stay visible even while a container/other
    // GUI is open, not just when no GUI (or only chat) is open - that's the point of
    // calling them "permanent". Off reverts to hiding behind any non-chat screen.
    public boolean infoDisplaysShowInGuis = true;

    /** Per-display layout/appearance, user-editable via /cofl displays or the settings GUI. */
    public static final class InfoDisplaySettings {
        public int id;
        public boolean enabled;
        // Fractions (0..1) of the scaled screen size; see com.coflnet.core.InfoDisplayLayout.
        public double x;
        public double y;
        public double scale = 1.0;
        public double backgroundAlpha = 0.35;
        public double textAlpha = 1.0;

        public InfoDisplaySettings() {
        }

        public InfoDisplaySettings(int id, boolean enabled, double x, double y) {
            this.id = id;
            this.enabled = enabled;
            this.x = x;
            this.y = y;
        }
    }

    /** Sensible default layout for a given display slot (top-right, stacked). */
    public static InfoDisplaySettings defaultInfoDisplay(int id) {
        return new InfoDisplaySettings(id, id == 1 || id == 2, 0.62, 0.02 + (id - 1) * 0.30);
    }

    /**
     * Fills in the 3 fixed display slots with defaults if missing/corrupt: an
     * old config file (Gson leaves the field null), a hand-edited file with the
     * wrong number of entries, or a null entry inside the list.
     */
    public void ensureInfoDisplays() {
        if (infoDisplays == null || infoDisplays.size() != 3) {
            infoDisplays = new ArrayList<>();
            for (int id = 1; id <= 3; id++) {
                infoDisplays.add(defaultInfoDisplay(id));
            }
            return;
        }
        for (int i = 0; i < infoDisplays.size(); i++) {
            if (infoDisplays.get(i) == null) {
                infoDisplays.set(i, defaultInfoDisplay(i + 1));
            }
        }
    }

    /** Looks up a display's settings by id (1-3); null if not present. */
    public InfoDisplaySettings displaySettings(int id) {
        if (infoDisplays == null) {
            return null;
        }
        for (InfoDisplaySettings settings : infoDisplays) {
            if (settings != null && settings.id == id) {
                return settings;
            }
        }
        return null;
    }

    // Share config between all local settings managers so a save cannot overwrite a newer layout.
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
                    config.ensureInfoDisplays();
                    return config;
                }
            }
        } catch (IOException e) {
            // Use default values if loading fails
        }

        // Return default config if loading fails or file doesn't exist
        CoflModConfig config = new CoflModConfig();
        config.ensureInfoDisplays();
        return config;
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
