package com.coflnet.mixin;

import com.coflnet.CoflModClient;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the sign editor's rendering while a click-armed "buy max" fill is auto-opening and
 * immediately re-submitting a sign. Without this the sign editor would flash on screen for the ~200ms
 * between opening the sign and {@code scheduleSignClose} closing it. Cancelling the render-state
 * extraction draws nothing for the screen (the sign panel/text never appears); the flag is only set
 * when we knowingly auto-open a sign and is cleared as soon as it closes, so normal manual sign
 * editing is unaffected.
 */
@Mixin(AbstractSignEditScreen.class)
public class SignEditScreenMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void coflSuppressAutoFillRender(CallbackInfo ci) {
        if (CoflModClient.suppressSignRender) {
            ci.cancel();
        }
    }
}
