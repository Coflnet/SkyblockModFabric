package com.coflnet.mixin;

import com.coflnet.CoflModClient;
import CoflCore.CoflSkyCommand;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    
    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    private void onSendMessage(String message, boolean addToHistory, CallbackInfo ci) {
        // If flipper chat only mode is enabled and the message is not a command
        if (CoflModClient.flipperChatOnlyMode && !message.startsWith("/")) {
            // Cancel the original message
            ci.cancel();
            
            // Send it to flipper chat instead
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                String username = client.player.getName().getString();
                String[] args = new String[message.split(" ").length + 1];
                args[0] = "chat";
                System.arraycopy(message.split(" "), 0, args, 1, message.split(" ").length);
                CoflSkyCommand.processCommand(args, username);
            }
        }
    }
}
