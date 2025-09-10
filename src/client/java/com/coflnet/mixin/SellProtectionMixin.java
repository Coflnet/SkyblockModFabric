package com.coflnet.mixin;

import com.coflnet.config.SellProtectionManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(HandledScreen.class)
public abstract class SellProtectionMixin {

    @Shadow @Nullable protected Slot focusedSlot;
    @Shadow public abstract @Nullable Slot getSlotAt(double x, double y);
    
    // Patterns for extracting coin amounts
    private static final Pattern INVENTORY_SACK_PATTERN = Pattern.compile("You earn: ([0-9,]+(?:\\.[0-9]+)?) coins");
    private static final Pattern SELL_INSTANTLY_PATTERN = Pattern.compile("Total: ([0-9,]+(?:\\.[0-9]+)?) coins");

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onSellProtectionMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        try {
            // Check if sell protection is enabled
            if (!SellProtectionManager.isEnabled()) {
                return;
            }

            // Only check left and right clicks
            if (button != 0 && button != 1) {
                return;
            }

            // Check if we're in a chest with "➜" in the title
            String screenTitle = ((HandledScreen<?>) (Object) this).getTitle().getString();
            if (!screenTitle.contains("➜")) {
                return;
            }

            // Get the slot being clicked
            Slot clickedSlot = getSlotAt(mouseX, mouseY);
            if (clickedSlot == null || !clickedSlot.hasStack()) {
                return;
            }

            ItemStack clickedItem = clickedSlot.getStack();
            String itemName = "";
            
            // Get item name from custom name or item display name
            if (clickedItem.getCustomName() != null) {
                itemName = clickedItem.getCustomName().getString();
            } else {
                itemName = clickedItem.getItem().getDefaultStack().getName().getString();
            }

            // Check if ctrl is pressed
            boolean ctrlPressed = GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                                  GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

            // Check for sell protection patterns and extract amounts
            boolean shouldBlock = false;
            String protection = "";
            long sellAmount = 1000000; // Default value

            if (itemName.contains("Sell Instantly")) {
                sellAmount = extractSellInstantlyAmount(clickedItem);
                if (button == 0 && !ctrlPressed && sellAmount > SellProtectionManager.getMaxAmount()) { // Left click without ctrl
                    shouldBlock = true;
                    protection = "Sell Instantly";
                }
            } else if (itemName.contains("Sell Sacks Now") || itemName.contains("Sell Inventory Now")) {
                sellAmount = extractInventorySackAmount(clickedItem);
                // Block both left and right clicks for these items if amount exceeds threshold
                if (!ctrlPressed && sellAmount > SellProtectionManager.getMaxAmount()) {
                    shouldBlock = true;
                    protection = itemName.contains("Sell Sacks Now") ? "Sell Sacks Now" : "Sell Inventory Now";
                }
            }

            if (shouldBlock) {
                // Send warning message to chat
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    String formattedAmount = formatCoins(sellAmount);
                    String message = "§c[SkyCofl Sell Protection] §fBlocked click on §e" + protection + "§f (§6" + formattedAmount + " coins§f)! Hold §bCtrl§f to override.";
                    client.player.sendMessage(Text.literal(message), false);
                }

                // Cancel the click
                cir.setReturnValue(true);
                return;
            }
            // No special handling needed for ctrl+click - just let it proceed normally

        } catch (Exception e) {
            System.out.println("[SellProtectionMixin] mouseClicked failed: " + e.getMessage());
        }
    }

    private long extractSellInstantlyAmount(ItemStack item) {
        try {
            List<Text> tooltip = item.getTooltip(
                Item.TooltipContext.DEFAULT,
                MinecraftClient.getInstance().player, 
                net.minecraft.item.tooltip.TooltipType.BASIC
            );
            
            for (Text line : tooltip) {
                String lineText = line.getString();
                Matcher matcher = SELL_INSTANTLY_PATTERN.matcher(lineText);
                if (matcher.find()) {
                    String amountStr = matcher.group(1).replace(",", "");
                    long amount = (long) (Double.parseDouble(amountStr));
                    System.out.println("[SellProtectionMixin] Found sell instantly amount: " + amount + " from text: " + lineText);
                    return amount;
                }
            }
        } catch (Exception e) {
            System.out.println("[SellProtectionMixin] Failed to extract sell instantly amount: " + e.getMessage());
        }
        System.out.println("[SellProtectionMixin] No sell instantly amount found, using default: 1000000");
        return 1000000; // Default value if parsing fails
    }

    private long extractInventorySackAmount(ItemStack item) {
        try {
            List<Text> tooltip = item.getTooltip(
                Item.TooltipContext.DEFAULT,
                MinecraftClient.getInstance().player, 
                net.minecraft.item.tooltip.TooltipType.BASIC
            );
            
            for (Text line : tooltip) {
                String lineText = line.getString();
                Matcher matcher = INVENTORY_SACK_PATTERN.matcher(lineText);
                if (matcher.find()) {
                    String amountStr = matcher.group(1).replace(",", "");
                    long amount = (long) (Double.parseDouble(amountStr));
                    System.out.println("[SellProtectionMixin] Found inventory/sack amount: " + amount + " from text: " + lineText);
                    return amount;
                }
            }
        } catch (Exception e) {
            System.out.println("[SellProtectionMixin] Failed to extract inventory/sack amount: " + e.getMessage());
        }
        System.out.println("[SellProtectionMixin] No inventory/sack amount found, using default: 1000000");
        return 1000000; // Default value if parsing fails
    }

    private String formatCoins(long coins) {
        if (coins >= 1000000000) {
            return String.format(java.util.Locale.US, "%.1fB", coins / 1000000000.0);
        } else if (coins >= 1000000) {
            return String.format(java.util.Locale.US, "%.1fM", coins / 1000000.0);
        } else if (coins >= 1000) {
            return String.format(java.util.Locale.US, "%.1fK", coins / 1000.0);
        } else {
            return String.valueOf(coins);
        }
    }
}
