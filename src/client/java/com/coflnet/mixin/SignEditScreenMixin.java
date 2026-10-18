package com.coflnet.mixin;

import com.coflnet.CoflModClient;
import com.coflnet.core.SignGuidance;
import com.coflnet.gui.BazaarAssist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Selects an autofilled price so typing replaces it, and suppresses the sign editor's rendering
 * while a click-armed "buy max" fill is auto-opening and
 * immediately re-submitting a sign. Without this the sign editor would flash on screen for the ~200ms
 * between opening the sign and {@code scheduleSignClose} closing it. Cancelling the render-state
 * extraction draws nothing for the screen (the sign panel/text never appears); the flag is only set
 * when we knowingly auto-open a sign and is cleared as soon as it closes, so normal manual sign
 * editing is unaffected.
 */
@Mixin(AbstractSignEditScreen.class)
public class SignEditScreenMixin {
    @Shadow @Final private String[] messages;
    @Shadow @Final private TextFieldHelper signField;
    @Unique private boolean coflPriceSelectionInitialized;

    @Inject(method = "init", at = @At("TAIL"))
    private void coflSelectSuggestedPrice(CallbackInfo ci) {
        // Resizing reinitializes the screen; preserve any selection or edits made by the player.
        if (coflPriceSelectionInitialized) return;
        coflPriceSelectionInitialized = true;

        String priceSuggestion = CoflModClient.findPriceSuggestion();
        String[] suggestion = priceSuggestion == null ? new String[0] : priceSuggestion.split(": ", 2);
        if (suggestion.length == 2 && messages.length == 4
                && suggestion[0].equals(messages[3])
                && suggestion[1].trim().equals(messages[0])) {
            // Vanilla typing/pasting replaces the selection, while cursor movement allows editing.
            signField.selectAll();
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void coflSuppressAutoFillRender(CallbackInfo ci) {
        if (CoflModClient.suppressSignRender) {
            ci.cancel();
        }
    }

    /** Shows what to type below the sign, between the sign and the Done button. */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void coflDrawGuidance(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (CoflModClient.suppressSignRender || messages.length != 4) return;
        long fillExpiresAt = CoflModClient.pendingSignFillExpiresAt;
        String fill = fillExpiresAt != 0 && System.currentTimeMillis() > fillExpiresAt
                ? null : CoflModClient.pendingSignFill;
        String expected = SignGuidance.expectedValue(fill, CoflModClient.pendingBazaarSearch,
                CoflModClient.findPriceSuggestion(), messages[3], BazaarAssist.expectedSignValue(messages[3]));
        SignGuidance.Guidance guidance = SignGuidance.compute(messages[0], expected);
        if (guidance.state() == SignGuidance.State.NONE) return;

        Screen screen = (Screen) (Object) this;
        Font font = Minecraft.getInstance().font;
        String[] parts = SignGuidance.displaySplit(guidance);
        String label = "Amount: ";
        int total = font.width(label + parts[0] + parts[1]);
        int x = (screen.width - total) / 2;
        // Vanilla sign sits around y=90 and the Done button at height/4+144; stay clear of both.
        int y = Math.min(150, screen.height / 4 + 144 - 26);
        graphics.text(font, label + parts[0], x, y, 0xFFFFFFFF, true);
        graphics.text(font, parts[1], x + font.width(label + parts[0]), y, 0xFF888888, true);
        if (guidance.state() != SignGuidance.State.TYPING) {
            int color = guidance.state() == SignGuidance.State.DONE ? 0xFF55FF55 : 0xFFFF5555;
            graphics.centeredText(font, guidance.message(), screen.width / 2, y + 12, color);
        }
    }
}
