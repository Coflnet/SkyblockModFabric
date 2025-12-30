package com.coflnet.mixin;

import com.coflnet.config.AngryCoopProtectionManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
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

import java.util.Locale;
import java.util.Optional;

@Mixin(HandledScreen.class)
public abstract class AuctionProtectionMixin {

    @Shadow @Nullable protected Slot focusedSlot;
    @Shadow public abstract @Nullable Slot getSlotAt(double x, double y);

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onAngryCoopMouseClicked(Click click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!AngryCoopProtectionManager.isEnabled()) {
                return;
            }

            int button = click.button();
            if (button != 0 && button != 1) {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) {
                return;
            }

            HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
            String screenTitle = stripFormatting(screen.getTitle().getString());
            ScreenMode mode = determineScreenMode(screenTitle);
            if (mode == null) {
                return;
            }

            double mouseX = click.x();
            double mouseY = click.y();
            Slot clickedSlot = getSlotAt(mouseX, mouseY);
            if (clickedSlot == null || !clickedSlot.hasStack()) {
                return;
            }

            ItemStack clickedStack = clickedSlot.getStack();
            if (clickedStack.isEmpty()) {
                return;
            }

            boolean ctrlPressed = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

            String playerNameLower = client.player.getGameProfile().name().toLowerCase(Locale.ROOT);
            String clickedName = stripFormatting(clickedStack.getName().getString()).trim();

            if (isClaimAll(clickedName)) {
                if (!ctrlPressed && hasForeignEntry(screen, playerNameLower, client.player.getInventory(), mode)) {
                    client.player.sendMessage(Text.literal(getClaimAllMessage(mode)), false);
                    cir.setReturnValue(true);
                }
                return;
            }

            Optional<String> foreignActor = getForeignActor(clickedStack, playerNameLower, mode);
            if (foreignActor.isEmpty()) {
                return;
            }

            if (ctrlPressed) {
                return;
            }

            client.player.sendMessage(Text.literal(getBlockedClickMessage(mode, foreignActor.get())), false);
            cir.setReturnValue(true);
        } catch (Exception e) {
            System.out.println("[AuctionProtectionMixin] mouseClicked failed: " + e.getMessage());
        }
    }

    private boolean isClaimAll(String name) {
        return name.trim().equalsIgnoreCase("Claim All");
    }

    private ScreenMode determineScreenMode(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.contains("manage auctions")) {
            return ScreenMode.SELLER;
        }
        if (lower.contains("your bids")) {
            return ScreenMode.BIDDER;
        }
        return null;
    }

    private Optional<String> getForeignActor(ItemStack stack, String playerNameLower, ScreenMode mode) {
        var loreComponent = stack.get(DataComponentTypes.LORE);
        if (loreComponent == null) {
            return Optional.empty();
        }

        for (Text line : loreComponent.lines()) {
            String raw = stripFormatting(line.getString());
            String lower = raw.toLowerCase(Locale.ROOT);

            if (mode == ScreenMode.SELLER) {
                if (lower.contains("this is your own auction")) {
                    return Optional.empty();
                }

                if (lower.startsWith("seller:")) {
                    if (!lower.contains(playerNameLower)) {
                        String seller = raw.substring("Seller:".length()).trim();
                        return Optional.of(seller.isEmpty() ? "Unknown" : seller);
                    } else {
                        return Optional.empty();
                    }
                }
            } else {
                if (lower.startsWith("bidder:")) {
                    if (lower.startsWith("bidder: you")) {
                        return Optional.empty();
                    }
                    if (!lower.contains(playerNameLower)) {
                        String bidder = raw.substring("Bidder:".length()).trim();
                        return Optional.of(bidder.isEmpty() ? "Unknown" : bidder);
                    } else {
                        return Optional.empty();
                    }
                }

                if (lower.contains("you are the highest bidder")) {
                    return Optional.empty();
                }
            }
        }

        return Optional.empty();
    }

    private boolean hasForeignEntry(HandledScreen<?> screen, String playerNameLower, PlayerInventory playerInventory, ScreenMode mode) {
        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot.inventory == playerInventory) {
                continue;
            }

            if (!slot.hasStack()) {
                continue;
            }

            if (getForeignActor(slot.getStack(), playerNameLower, mode).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private String getClaimAllMessage(ScreenMode mode) {
        String reason = mode == ScreenMode.SELLER
                ? "auctions listed by co-op members"
                : "bids placed by co-op members";
        return "§c[SkyCofl Angry Coop] §fBlocked Claim All because " + reason + " were detected. Hold §bCtrl§f to override.";
    }

    private String getBlockedClickMessage(ScreenMode mode, String foreignName) {
        String action = mode == ScreenMode.SELLER ? "listed by" : "bid on by";
        return "§c[SkyCofl Angry Coop] §fBlocked claiming auction " + action + " §e" + foreignName + "§f. Hold §bCtrl§f to override.";
    }

    private String stripFormatting(String text) {
        if (text == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder(text.length());
        boolean skip = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§') {
                skip = true;
                continue;
            }
            if (skip) {
                skip = false;
                continue;
            }
            builder.append(c);
        }
        return builder.toString();
    }
}
