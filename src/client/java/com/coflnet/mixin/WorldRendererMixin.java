package com.coflnet.mixin;

import CoflCore.classes.Position;
import com.coflnet.EventSubscribers;
import com.coflnet.gui.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderWorld(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f matrix4f, Matrix4f projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        if (EventSubscribers.positions == null || EventSubscribers.positions.isEmpty()) {
            return;
        }

        if (camera == null) {
            return;
        }

        // Create a MatrixStack for world-space rendering
        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(positionMatrix);
        
        for (Position position : EventSubscribers.positions) {
            RenderUtils.renderHighlightBox(
                    matrices,
                    camera,
                    new double[]{
                            position.getX(),
                            position.getY(),
                            position.getZ()
                    },
                    new double[]{
                            position.getX() + 1.0,
                            position.getY() + 1.0,
                            position.getZ() + 1.0
                    },
                    new float[]{0.3f, 1f, 0.1f, 0.5f}
            );
        }
    }
}
