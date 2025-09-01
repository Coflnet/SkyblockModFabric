package com.coflnet.mixin;

import com.coflnet.CoflModClient;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {
    @Inject(method = "openEditSignScreen", at = @At("HEAD"))
    private void openEditSignScreen(SignBlockEntity sign, boolean front, CallbackInfo ci){
        // Handle bazaar search first
        if (CoflModClient.pendingBazaarSearch != null) {
            handleBazaarSearch(sign, front);
            return;
        }

        // Handle existing price suggestion logic
        String toSuggest = CoflModClient.findPriceSuggestion();
        System.out.println("Value to suggest: '"+toSuggest+"'");
        Text[] lines = sign.getFrontText().getMessages(MinecraftClient.getInstance().shouldFilterText());
        String[] suggestionParts = toSuggest.split(": ");

        System.out.println(suggestionParts[0]);
        System.out.println(lines[3].getString());

        if(toSuggest == "") return;
        if(suggestionParts[0].compareTo(lines[3].getString()) != 0) return;

        lines[0] = Text.of(suggestionParts[1].trim());
        sign.changeText(signText -> new SignText(
                lines, lines,
                signText.getColor(),
                signText.isGlowing()
        ), true);
    }

    /**
     * Handles filling in the bazaar search term and closing the sign.
     */
    private void handleBazaarSearch(SignBlockEntity sign, boolean front) {
        System.out.println("Filling bazaar search with: " + CoflModClient.pendingBazaarSearch);
        
        Text[] lines = sign.getFrontText().getMessages(MinecraftClient.getInstance().shouldFilterText());
        
        // Fill the first line with the search term
        lines[0] = Text.of(CoflModClient.pendingBazaarSearch);
        
        // Clear other lines
        lines[1] = Text.of("");
        lines[2] = Text.of("");
        lines[3] = Text.of("");
        
        // Apply the changes to the sign
        sign.changeText(signText -> new SignText(
                lines, lines,
                signText.getColor(),
                signText.isGlowing()
        ), front);
        
        System.out.println("Applied bazaar search text to sign");
        
        // Clear the pending search term
        CoflModClient.pendingBazaarSearch = null;
        
        // Schedule closing the sign after a short delay to ensure the text is saved
        MinecraftClient client = MinecraftClient.getInstance();
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(200); // Increased delay to ensure sign text is processed
                client.execute(() -> {
                    if (client.currentScreen != null) {
                        System.out.println("Closing sign screen to complete bazaar search");
                        client.currentScreen.close();
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
