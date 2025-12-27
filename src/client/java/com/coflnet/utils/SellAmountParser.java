package com.coflnet.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SellAmountParser {
    
    // Patterns for extracting coin amounts
    private static final Pattern INVENTORY_SACK_PATTERN = Pattern.compile("You earn: ([0-9,]+(?:\\.[0-9]+)?) coins");
    private static final Pattern SELL_INSTANTLY_PATTERN = Pattern.compile("Total: ([0-9,]+(?:\\.[0-9]+)?) coins");
    
    // Default high value to trigger protection when parsing fails
    private static final long DEFAULT_PROTECTION_AMOUNT = Long.MAX_VALUE;
    
    /**
     * Extracts the coin amount from "Sell Instantly" items
     * @param item the ItemStack to parse
     * @return the coin amount, or DEFAULT_PROTECTION_AMOUNT if parsing fails
     */
    public static long extractSellInstantlyAmount(ItemStack item) {
        try {
            List<Text> tooltip = item.getTooltip(
                Item.TooltipContext.DEFAULT,
                MinecraftClient.getInstance().player, 
                net.minecraft.item.tooltip.TooltipType.BASIC
            );
            
            return extractSellInstantlyAmountFromTooltip(tooltip);
        } catch (Exception e) {
            System.out.println("[SellAmountParser] Failed to get tooltip for sell instantly amount: " + e.getMessage());
            return DEFAULT_PROTECTION_AMOUNT;
        }
    }
    
    /**
     * Extracts the coin amount from "Sell Sacks Now" or "Sell Inventory Now" items
     * @param item the ItemStack to parse
     * @return the coin amount, or DEFAULT_PROTECTION_AMOUNT if parsing fails
     */
    public static long extractInventorySackAmount(ItemStack item) {
        try {
            List<Text> tooltip = item.getTooltip(
                Item.TooltipContext.DEFAULT,
                MinecraftClient.getInstance().player, 
                net.minecraft.item.tooltip.TooltipType.BASIC
            );
            
            return extractInventorySackAmountFromTooltip(tooltip);
        } catch (Exception e) {
            System.out.println("[SellAmountParser] Failed to get tooltip for inventory/sack amount: " + e.getMessage());
            return DEFAULT_PROTECTION_AMOUNT;
        }
    }
    
    /**
     * Extracts the coin amount from "Sell Instantly" tooltip lines
     * @param lines the tooltip lines to parse
     * @return the coin amount, 0 if nothing to sell, or DEFAULT_PROTECTION_AMOUNT if parsing fails
     */
    public static long extractSellInstantlyAmountFromTooltip(List<Text> lines) {
        for (Text line : lines) {
            String lineText = line.getString();
            // Check if the description says "You don't have anything to sell" - treat as 0 value
            if (lineText.contains("You don't have anything to sell")) {
                return 0;
            }
            Matcher matcher = SELL_INSTANTLY_PATTERN.matcher(lineText);
            if (matcher.find()) {
                String amountStr = matcher.group(1).replace(",", "");
                try {
                    long amount = (long) (Double.parseDouble(amountStr));
                    return amount;
                } catch (NumberFormatException e) {
                    System.out.println("[SellAmountParser] Failed to parse sell instantly amount: " + amountStr);
                    return DEFAULT_PROTECTION_AMOUNT;
                }
            }
        }
        System.out.println("[SellAmountParser] No sell instantly amount found, using default protection amount");
        return DEFAULT_PROTECTION_AMOUNT;
    }
    
    /**
     * Extracts the coin amount from "Sell Sacks Now" or "Sell Inventory Now" tooltip lines
     * @param lines the tooltip lines to parse
     * @return the coin amount, 0 if nothing to sell, or DEFAULT_PROTECTION_AMOUNT if parsing fails
     */
    public static long extractInventorySackAmountFromTooltip(List<Text> lines) {
        for (Text line : lines) {
            String lineText = line.getString();
            // Check if the description says "You don't have anything to sell" - treat as 0 value
            if (lineText.contains("You don't have anything to sell")) {
                System.out.println("[SellAmountParser] Found 'You don't have anything to sell' message, returning 0");
                return 0;
            }
            Matcher matcher = INVENTORY_SACK_PATTERN.matcher(lineText);
            if (matcher.find()) {
                String amountStr = matcher.group(1).replace(",", "");
                try {
                    long amount = (long) (Double.parseDouble(amountStr));
                    return amount;
                } catch (NumberFormatException e) {
                    System.out.println("[SellAmountParser] Failed to parse inventory/sack amount: " + amountStr);
                    return DEFAULT_PROTECTION_AMOUNT;
                }
            }
        }
        System.out.println("[SellAmountParser] No inventory/sack amount found, using default protection amount");
        return DEFAULT_PROTECTION_AMOUNT;
    }
    
    /**
     * @return the default amount used when parsing fails (triggers protection)
     */
    public static long getDefaultProtectionAmount() {
        return DEFAULT_PROTECTION_AMOUNT;
    }
}
