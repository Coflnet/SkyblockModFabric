package com.coflnet.mixin;

import CoflCore.classes.Position;
import com.coflnet.EventSubscribers;
import com.coflnet.gui.RenderUtils;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector4f;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class WorldRendererMixin {

    // Precomputed constants instead of allocating a new float[]{...} literal per position per frame.
    private static final float HIGHLIGHT_R = 0.3f;
    private static final float HIGHLIGHT_G = 1f;
    private static final float HIGHLIGHT_B = 0.1f;
    private static final float HIGHLIGHT_A = 0.5f;

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderWorld(GraphicsResourceAllocator allocator, boolean renderBlockOutline, CameraRenderState cameraState, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, boolean renderWeather, CallbackInfo ci) {
        if (EventSubscribers.positions == null || EventSubscribers.positions.isEmpty()) {
            return;
        }

        if (cameraState == null || cameraState.pos == null) {
            return;
        }

        // Create a PoseStack for world-space rendering
        PoseStack matrices = new PoseStack();
        
        for (Position position : EventSubscribers.positions) {
            // Primitive overload: no double[]/float[] wrapper allocation per position per frame.
            RenderUtils.renderHighlightBox(
                    matrices,
                    cameraState.pos,
                    position.getX(), position.getY(), position.getZ(),
                    position.getX() + 1.0, position.getY() + 1.0, position.getZ() + 1.0,
                    HIGHLIGHT_R, HIGHLIGHT_G, HIGHLIGHT_B, HIGHLIGHT_A
            );
        }
    }
}
