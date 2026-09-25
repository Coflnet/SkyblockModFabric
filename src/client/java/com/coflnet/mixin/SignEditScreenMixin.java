package com.coflnet.mixin;

import com.coflnet.CoflModClient;
import net.minecraft.client.gui.font.TextFieldHelper;
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

        String[] suggestion = CoflModClient.findPriceSuggestion().split(": ", 2);
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
}
