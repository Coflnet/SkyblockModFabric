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

    // lore engine when on item lore is rendered from the users templates
    //  loremodules instead of the stock backend strings. off by default so
    // nothing changes until the user opts in.
    public boolean loreEngineEnabled = false;

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
