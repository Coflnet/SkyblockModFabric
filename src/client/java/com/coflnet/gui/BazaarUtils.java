package com.coflnet.gui;

import com.coflnet.CoflModClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

/**
 * Utility class for bazaar-related functionality.
 */
public class BazaarUtils {
    
    /**
     * Automatically searches for an item in the bazaar if currently viewing the bazaar.
     * This is a convenience method that can be called from anywhere in the mod.
     * 
     * @param itemName the name of the item to search for
     * @return true if the search was initiated, false if not on bazaar or search failed
     */
    public static boolean autoSearchBazaar(String itemName) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (!(client.currentScreen instanceof GenericContainerScreen gcs)) {
            return false;
        }
        
        if (!CoflModClient.isBazaar(gcs)) {
            return false;
        }
        
        CoflModClient.searchInBazaarSmart(itemName);
        return true;
    }

    /**
     * Searches for an item in the bazaar with basic name cleaning.
     * 
     * @param itemName the name of the item to search for
     * @return true if the search was initiated, false if not on bazaar or search failed
     */
    public static boolean searchBazaar(String itemName) {
        return autoSearchBazaar(itemName);
    }
    
    /**
     * Checks if the player is currently viewing the bazaar.
     * 
     * @return true if currently on the bazaar, false otherwise
     */
    public static boolean isCurrentlyOnBazaar() {
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (!(client.currentScreen instanceof GenericContainerScreen gcs)) {
            return false;
        }
        
        return CoflModClient.isBazaar(gcs);
    }
    
    /**
     * Gets the current bazaar screen if available.
     * 
     * @return the bazaar screen or null if not currently on bazaar
     */
    public static GenericContainerScreen getCurrentBazaarScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (!(client.currentScreen instanceof GenericContainerScreen gcs)) {
            return null;
        }
        
        if (!CoflModClient.isBazaar(gcs)) {
            return null;
        }
        
        return gcs;
    }
    
    /**
     * Lists all searchable items currently visible in the bazaar.
     * 
     * @return an array of item names that can be searched for
     */
    public static String[] getVisibleBazaarItems() {
        GenericContainerScreen bazaar = getCurrentBazaarScreen();
        if (bazaar == null) {
            return new String[0];
        }
        
        java.util.List<String> items = new java.util.ArrayList<>();
        
        for (int i = 0; i < bazaar.getScreenHandler().getInventory().size(); i++) {
            ItemStack stack = bazaar.getScreenHandler().getInventory().getStack(i);
            
            if (stack.isEmpty() || stack.getItem() == Items.BLACK_STAINED_GLASS_PANE) {
                continue;
            }
            
            if (stack.getCustomName() != null) {
                String itemName = stack.getCustomName().getString();
                // Filter out UI elements
                if (!itemName.contains("Search") && !itemName.contains("Go Back") && !itemName.contains("Close")) {
                    items.add(itemName);
                }
            }
        }
        
        return items.toArray(new String[0]);
    }
}
