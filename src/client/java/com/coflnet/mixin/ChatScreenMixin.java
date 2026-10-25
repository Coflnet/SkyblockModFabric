package com.coflnet.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.coflnet.CoflModClient;
import CoflCore.CoflSkyCommand;
import com.coflnet.core.ExpectedCommandState;
import com.coflnet.core.GhostText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Style;
import com.coflnet.gui.hud.InfoDisplayRenderer;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Shadow
    protected EditBox input;

    @Shadow
    private boolean handleComponentClicked(Style style, boolean insertion) {
        throw new AssertionError();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void coflnet$clickInfoDisplay(MouseButtonEvent click, boolean doubleClick,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return;
        }
        var window = Minecraft.getInstance().getWindow();
        Style style = InfoDisplayRenderer.styleAt(click.x(), click.y(),
                window.getGuiScaledWidth(), window.getGuiScaledHeight());
        if (style != null && style.getClickEvent() != null && handleComponentClicked(style, false)) {
            cir.setReturnValue(true);
        }
    }

    /** Vanilla's CommandSuggestions.updateCommandInfo() resets the suggestion on every edit; re-apply ours. */
    @Inject(method = "onEdited", at = @At("TAIL"))
    private void coflnet$ghostText(String text, CallbackInfo ci) {
        var expected = ExpectedCommandState.current(System.currentTimeMillis());
        if (expected.isEmpty()) return;
        String suffix = GhostText.compute(text, expected.get()).suggestionSuffix();
        if (!suffix.isEmpty()) input.setSuggestion(suffix);
    }

    /** Draws the part of the typed text that diverges from the expected command in red. */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void coflnet$ghostMismatch(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        var expected = ExpectedCommandState.current(System.currentTimeMillis());
        if (expected.isEmpty()) return;
        String typed = input.getValue();
        int mismatch = GhostText.compute(typed, expected.get()).mismatchIndex();
        if (mismatch < 0 || mismatch >= typed.length()) return;
        var font = Minecraft.getInstance().font;
        int x = input.getScreenX(mismatch);
        int y = input.isBordered() ? input.getY() + (input.getHeight() - 8) / 2 : input.getY();
        graphics.text(font, typed.substring(mismatch), x, y, 0xFFFF5555, true);
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"))
    private void coflnet$clearExpectedCommand(String message, boolean addToHistory, CallbackInfo ci) {
        ExpectedCommandState.consume(message, System.currentTimeMillis());
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void onSendMessage(String message, boolean addToHistory, CallbackInfo ci) {
        // This hook runs before vanilla's empty-input check; an empty /cofl chat toggles chat.
        if (message.isBlank()) return;

        // Commands are never redirected here.
        if (message.startsWith("/")) return;

        // If the SkyCofl text_Tunnels channel is currently active, route the
        // typed message to /cofl chat BEFORE handleChatInput calls sendChat.
        // text_Tunnels' sendChat mixin would otherwise try to forward
        // "/cofl chat <msg>" to the Minecraft server (a client-only command).
        if (isTextTunnelsSkyCoflActive()) {
            ci.cancel();
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                String username = client.player.getName().getString();
                String[] parts = message.split(" ");
                String[] args = new String[parts.length + 1];
                args[0] = "chat";
                System.arraycopy(parts, 0, args, 1, parts.length);
                // Chat input handling runs on the render thread; defer the actual network
                // call so it can never block on the WSClientWrapper's monitor.
                CoflModClient.backgroundQueue.submit(() -> CoflSkyCommand.processCommand(args, username));
            }
            return;
        }

        // If flipper chat only mode is enabled and the message is not a command
        if (CoflModClient.flipperChatOnlyMode) {
            // Cancel the original message
            ci.cancel();

            // Send it to flipper chat instead
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                String username = client.player.getName().getString();
                String[] args = new String[message.split(" ").length + 1];
                args[0] = "chat";
                System.arraycopy(message.split(" "), 0, args, 1, message.split(" ").length);
                // Chat input handling runs on the render thread; defer the actual network
                // call so it can never block on the WSClientWrapper's monitor.
                CoflModClient.backgroundQueue.submit(() -> CoflSkyCommand.processCommand(args, username));
            }
        }
    }

    /**
     * Returns true when text_Tunnels is installed and the currently selected
     * tunnel has "/cofl chat " as its send-prefix (i.e. the SkyCofl tunnel).
     *
     * Uses reflection so that a text_Tunnels API change only silently disables
     * the feature rather than crashing the mod.
     */
    private static boolean isTextTunnelsSkyCoflActive() {
        if (!CoflModClient.isTextTunnelsInstalled()) return false;
        try {
            Class<?> msh = Class.forName("org.olim.text_tunnels.MessageSendHandler");
            java.lang.reflect.Field indexField = msh.getDeclaredField("currentIndex");
            indexField.setAccessible(true);
            int index = (int) indexField.get(null);
            if (index < 0) return false;
            java.lang.reflect.Field prefixesField = msh.getDeclaredField("sendPrefixes");
            prefixesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<String> prefixes =
                    (java.util.List<String>) prefixesField.get(null);
            return prefixes != null
                    && index < prefixes.size()
                    && "/cofl chat ".equals(prefixes.get(index));
        } catch (Exception ignored) {
            return false;
        }
    }
}
