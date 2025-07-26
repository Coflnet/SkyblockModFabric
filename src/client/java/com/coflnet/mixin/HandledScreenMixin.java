package com.coflnet.mixin;

import CoflCore.handlers.DescriptionHandler;
import com.coflnet.CoflModClient;
import com.coflnet.gui.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ScrollableTextWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    private int widgetWidth = 1;
    private int widgetHeight = 1;
    private String msg = "";
    private ScrollableTextWidget sideTextWidget = new ScrollableTextWidget(
            5, 5,
            100, 150,
            Text.of(RenderUtils.lorem()),
            MinecraftClient.getInstance().textRenderer
    );

    @Inject(at = @At("TAIL"), method = "init")
    public void init(CallbackInfo ci){
        DescriptionHandler.DescModification[] lines = CoflModClient.getExtraSlotDescMod();
        if(lines == null || lines.length == 0) return;
        String temp = "";
        for (DescriptionHandler.DescModification descModification : lines) {
            temp = temp + "\n" + descModification.value;
        }

        sideTextWidget.setMessage(Text.of(temp));
    }

    @Inject(at = @At("TAIL"), method = "render")
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci){
        sideTextWidget.render(context, mouseX, mouseY, deltaTicks);
    }

}
