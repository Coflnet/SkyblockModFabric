package com.coflnet.mixin;

import com.coflnet.CoflModClient;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class ClientPlayerEntityMixin {
    @Inject(method = "openTextEdit", at = @At("HEAD"))
    private void openTextEdit(SignBlockEntity sign, boolean front, CallbackInfo ci){
        try {
            // Handle trade coins input first (auto-fill the coin amount sign).
            if (CoflModClient.pendingCoinAmount != null) {
                handleCoinInput(sign, front);
                return;
            }

            // Handle bazaar search first
            if (CoflModClient.pendingBazaarSearch != null) {
                handleBazaarSearch(sign, front);
                return;
            }

            // Handle a click-armed sign fill (e.g. the Instant Buy "buy max" suggestion).
            if (CoflModClient.pendingSignFill != null) {
                handleSignFill(sign, front);
                return;
            }

            // Handle existing price suggestion logic
            String toSuggest = CoflModClient.findPriceSuggestion();
            System.out.println("Value to suggest: '"+toSuggest+"'");
            Component[] lines = sign.getFrontText().getMessages(Minecraft.getInstance().isTextFilteringEnabled());
            String[] suggestionParts = toSuggest.split(": ");

            if(toSuggest == "") return;
            if(suggestionParts.length == 0) return;
            if(lines.length < 4) return;
            if(!suggestionParts[0].equals(lines[3].getString())) return;

            lines[0] = Component.literal(suggestionParts[1].trim());
            sign.updateText(signText -> new SignText(
                    lines, lines,
                    signText.getColor(),
                    signText.hasGlowingText()
            ), true);
        } catch (Exception e) {
            System.out.println("[ClientPlayerEntityMixin] openEditSignScreen failed: " + e.getMessage());
        }
    }

    /**
     * Handles a click-armed sign fill: fills line 0 with the suggested value, but only when the
     * sign's 4th line matches the armed prefix (so we never fill an unrelated sign), then closes the
     * sign so the amount is submitted. Payload format is "<4th line>: <value>", same as the
     * always-on price suggestion but gated behind an explicit InfoDisplay click.
     */
    private void handleSignFill(SignBlockEntity sign, boolean front) {
        String payload = CoflModClient.pendingSignFill;
        CoflModClient.pendingSignFill = null; // consume once, regardless of match

        String matchLine = null;
        String value = null;
        if (payload != null) {
            try {
                JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
                if (obj.has("line") && !obj.get("line").isJsonNull()) matchLine = obj.get("line").getAsString();
                if (obj.has("value") && !obj.get("value").isJsonNull()) value = obj.get("value").getAsString();
            } catch (Exception e) {
                // malformed payload - fall through to the abort path below
            }
        }
        if (matchLine == null || value == null) {
            CoflModClient.suppressSignRender = false; // nothing to fill, don't keep hiding signs
            return;
        }

        Component[] lines = sign.getFrontText().getMessages(Minecraft.getInstance().isTextFilteringEnabled());
        if (lines.length < 4 || !matchLine.equals(lines[3].getString())) {
            CoflModClient.suppressSignRender = false; // not the sign this suggestion is for
            return;
        }

        lines[0] = Component.literal(value.trim());
        sign.updateText(signText -> new SignText(
                lines, lines,
                signText.getColor(),
                signText.hasGlowingText()
        ), front);

        scheduleSignClose(Minecraft.getInstance(), "buy max sign fill");
    }

    /**
     * Handles filling in the trade coin amount and closing the sign. Mirrors the
     * bazaar-search auto-fill flow exactly — fills line 0 with the digit string
     * and closes the sign after a short delay so Hypixel registers the amount.
     */
    private void handleCoinInput(SignBlockEntity sign, boolean front) {
        String amount = CoflModClient.pendingCoinAmount;
        System.out.println("Filling trade coins with: " + amount);

        Component[] lines = sign.getFrontText().getMessages(Minecraft.getInstance().isTextFilteringEnabled());
        lines[0] = Component.literal(amount);
        lines[1] = Component.literal("");
        lines[2] = Component.literal("");
        lines[3] = Component.literal("");

        sign.updateText(signText -> new SignText(
                lines, lines,
                signText.getColor(),
                signText.hasGlowingText()
        ), front);

        CoflModClient.pendingCoinAmount = null;

        scheduleSignClose(Minecraft.getInstance(), "coin input");
    }

    /**
     * Closes the sign editing screen after a short delay so Hypixel registers the
     * filled-in text. Captures the screen that is open once the sign has appeared
     * and only closes it if that exact same screen is still open when the timer
     * fires. This guards against the TOCTOU window where the player navigates to a
     * different screen during the delay — without the identity check we would close
     * an unrelated screen.
     */
    private void scheduleSignClose(Minecraft client, String context) {
        Thread.startVirtualThread(() -> {
            try {
                // Grab the sign screen once it has actually opened (next client tick).
                Screen[] expected = new Screen[1];
                client.execute(() -> expected[0] = client.gui.screen());
                Thread.sleep(200); // let the sign text be processed before closing
                client.execute(() -> {
                    Screen current = client.gui.screen();
                    if (current != null && current == expected[0]) {
                        System.out.println("Closing sign screen to complete " + context);
                        current.onClose();
                    }
                    // Re-enable sign rendering now that the auto-filled sign is closing (no-op for
                    // the bazaar-search / coin paths, where the flag is already false).
                    CoflModClient.suppressSignRender = false;
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Handles filling in the bazaar search term and closing the sign.
     */
    private void handleBazaarSearch(SignBlockEntity sign, boolean front) {
        System.out.println("Filling bazaar search with: " + CoflModClient.pendingBazaarSearch);
        
        Component[] lines = sign.getFrontText().getMessages(Minecraft.getInstance().isTextFilteringEnabled());
        
        // Fill the first line with the search term
        lines[0] = Component.literal(CoflModClient.pendingBazaarSearch);
        
        // Clear other lines
        lines[1] = Component.literal("");
        lines[2] = Component.literal("");
        lines[3] = Component.literal("");
        
        // Apply the changes to the sign
        sign.updateText(signText -> new SignText(
                lines, lines,
                signText.getColor(),
                signText.hasGlowingText()
        ), front);
        
        System.out.println("Applied bazaar search text to sign");
        
        // Clear the pending search term
        CoflModClient.pendingBazaarSearch = null;

        // Schedule closing the sign after a short delay to ensure the text is saved
        scheduleSignClose(Minecraft.getInstance(), "bazaar search");
    }
}
