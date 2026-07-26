package com.coflnet.config;

import com.coflnet.util.BoundedTextFile;
import com.coflnet.util.JsonNesting;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;

public class TextWidgetPositionConfig {
    private static final int MAX_CONFIG_FILE_BYTES = 65_536;
    private static final Gson gson = new Gson();
    private static final File POSITION_CONFIG_FILE = new File(Minecraft.getInstance().gameDirectory, "config/CoflSky/coflsky_text_position.json");
    
    public int offsetX = -5;
    public int offsetY = 5;
    
    public static TextWidgetPositionConfig load() {
        // Try to load from combined config first
        CoflModConfig combinedConfig = CoflModConfig.get();
        TextWidgetPositionConfig config = new TextWidgetPositionConfig();
        config.offsetX = combinedConfig.textWidgetOffsetX;
        config.offsetY = combinedConfig.textWidgetOffsetY;
        
        // Check if old config exists and migrate
        try {
            if (POSITION_CONFIG_FILE.exists()) {
                String json = BoundedTextFile.readUtf8(
                        POSITION_CONFIG_FILE.toPath(), MAX_CONFIG_FILE_BYTES);
                if (json != null && JsonNesting.isWithinLimit(json, 64)) {
                    TextWidgetPositionConfig oldConfig =
                            gson.fromJson(json, TextWidgetPositionConfig.class);
                    if (oldConfig != null) {
                        // Migrate to new config
                        combinedConfig.textWidgetOffsetX = oldConfig.offsetX;
                        combinedConfig.textWidgetOffsetY = oldConfig.offsetY;
                        if (combinedConfig.saveAndReport()
                                && !POSITION_CONFIG_FILE.delete()) {
                            System.out.println("Could not delete migrated text position config");
                        }

                        config.offsetX = oldConfig.offsetX;
                        config.offsetY = oldConfig.offsetY;
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            // Use default values if loading fails
        }
        
        return config;
    }
    
    public void save() {
        // Save to combined config instead
        CoflModConfig combinedConfig = CoflModConfig.get();
        combinedConfig.textWidgetOffsetX = this.offsetX;
        combinedConfig.textWidgetOffsetY = this.offsetY;
        combinedConfig.save();
    }
}
