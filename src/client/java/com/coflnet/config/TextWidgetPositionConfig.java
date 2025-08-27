package com.coflnet.config;

import com.google.gson.Gson;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class TextWidgetPositionConfig {
    private static final Gson gson = new Gson();
    private static final File POSITION_CONFIG_FILE = new File(MinecraftClient.getInstance().runDirectory, "config/CoflSky/coflsky_text_position.json");
    
    public int offsetX = -5;
    public int offsetY = 5;
    
    public static TextWidgetPositionConfig load() {
        try {
            if (POSITION_CONFIG_FILE.exists()) {
                FileReader reader = new FileReader(POSITION_CONFIG_FILE);
                TextWidgetPositionConfig config = gson.fromJson(reader, TextWidgetPositionConfig.class);
                reader.close();
                if (config != null) {
                    return config;
                }
            }
        } catch (IOException e) {
            // Use default values if loading fails
        }
        
        // Return default config if loading fails or file doesn't exist
        return new TextWidgetPositionConfig();
    }
    
    public void save() {
        try {
            POSITION_CONFIG_FILE.getParentFile().mkdirs();
            FileWriter writer = new FileWriter(POSITION_CONFIG_FILE);
            gson.toJson(this, writer);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
