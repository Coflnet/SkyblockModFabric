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
        String toSuggest = CoflModClient.findPriceSuggestion();
        System.out.println("Value to suggest: '"+toSuggest+"'");
        Text[] lines = sign.getFrontText().getMessages(MinecraftClient.getInstance().shouldFilterText());
        String[] suggestionParts = toSuggest.split(":");

        if(toSuggest == "") return;
        if(suggestionParts[0] != lines[3].getString()) return;

        lines[0] = Text.of(suggestionParts[1].trim());
        sign.changeText(signText -> new SignText(
                lines, lines,
                signText.getColor(),
                signText.isGlowing()
        ), true);
    }
}
