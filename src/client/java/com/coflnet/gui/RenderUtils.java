package com.coflnet.gui;

import com.coflnet.CoflMod;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.injection.invoke.arg.ArgumentCountException;

import java.awt.*;

/**
 * Created by ForBai
 * Modified by iroot-work
 */
public class RenderUtils {
    private static Tessellator tessellator = null;
    private static BufferBuilder buffer = null;
    public static TextRenderer textRenderer = null;
    public static int z = 0;
    private static final RenderLayer.MultiPhase THROUGH_WALLS_LAYER = RenderLayer.of(
            "filled_through_walls", RenderLayer.DEFAULT_BUFFER_SIZE, false, true,
            RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                            .withLocation(Identifier.of(CoflMod.MOD_ID, "pipeline/debug_filled_box_through_walls"))
                            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLE_STRIP)
                            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                            .build()
            ), RenderLayer.MultiPhaseParameters.builder()
                    .layering(RenderPhase.VIEW_OFFSET_Z_LAYERING_FORWARD)
                    .build(false)
    );

    public static void init(){
        z = 0; // 401
        tessellator = Tessellator.getInstance();
        textRenderer = MinecraftClient.getInstance().textRenderer;
    }

    //draw a rectangle
    public static void drawRect(DrawContext context, float x, float y, float width, float height, int color) {
        context.fill((int) x, (int) y, (int) (x + width), (int) (y + height), color);
    }

    //draws an outlined rectangle with a given color and size and a given line width
    public static void drawRectOutline(DrawContext context, int x, int y, int width, int height, float lineWidth, int fillCol, int lineCol) {
        drawRect(context, x, y, width, height, lineCol);
        drawRect(context, x + lineWidth, y + lineWidth, width - lineWidth * 2, height - lineWidth * 2, fillCol);
    }

    //draws a circle with a given radius and thickness
    public static void drawCircle(DrawContext context, int x, int y, int radius, int color) {
        for (int i = 0; i <= 360; i++) {
            context.fill(
                    (int) (x + Math.sin(i * Math.PI / 180) * radius),
                    (int) (y + Math.cos(i * Math.PI / 180) * radius),
                    x,
                    y,
                    color
            );
        }
    }

    //draws a circle outline with a given radius and thickness
    public static void drawCircleOutline(DrawContext context, int x, int y, int radius, int thickness, int fillCol, int lineCol) {
        drawCircle(context, x, y, radius, lineCol);
        drawCircle(context, x, y, radius + thickness * 2, fillCol);
    }

    //draws line from x1,y1 to x2,y2 with a given color and thickness
    public static void drawLine(float x1, float y1, float x2, float y2, float thickness, Color color) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        setColor(color);
        GL11.glLineWidth(thickness);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2d(x1, y1);
        GL11.glVertex2d(x2, y2);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    //draws a triangle from x1,y1 to x2,y2 to x3,y3
    public static void drawTriangle(int x, int y, int x2, int y2, int x3, int y3, Color color) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        setColor(color);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x2, y2);
        GL11.glVertex2d(x3, y3);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    //draws a triangle outline from x1,y1 to x2,y2 to x3,y3
    public static void drawTriangleOutline(int x, int y, int x2, int y2, int x3, int y3, Color color) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        setColor(color);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x2, y2);
        GL11.glVertex2d(x3, y3);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    //draws an arc with a given radius, start angle, and end angle
    public static void drawArc(DrawContext context, int x, int y, int radius, int startAngle, int endAngle, int color) {
//        buffer = tessellator.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
//
//        for (int i = startAngle; i <= endAngle; i++) {
//            buffer.vertex(
//                    context.getMatrices().peek().getPositionMatrix(),
//                    (float) (x + Math.sin(i * Math.PI / 180) * radius),
//                    (float) (y + Math.cos(i * Math.PI / 180) * radius),
//                    z
//            ).color(color);
//        }
//
//        //RenderSystem.setShader(GameRenderer::getPositionColorProgram);
//        //RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, 0.0F);
//        //BufferRenderer.drawWithGlobalProgram(buffer.end());
//        //RenderLayer.getGui().draw(buffer.end());
    }


    //draw a loading circle with a given radius, thickness, and speed
    public static void drawLoadingCircle(DrawContext context, float x, float y, float radius, float thickness, float speed, int color) {
//        buffer = tessellator.begin(VertexFormat.DrawMode.LINE_STRIP, VertexFormats.POSITION_COLOR);
//
//        for (int i = 0; i <= 360; i++) {
//            buffer.vertex(
//                    context.getMatrices().peek().getPositionMatrix(),
//                    (float) (x + Math.sin((i + speed) * Math.PI / 180) * radius),
//                    (float) (y + Math.cos((i + speed) * Math.PI / 180) * radius),
//                    z
//            ).color(color);
//        }
//        //RenderSystem.setShader(GameRenderer::getPositionColorProgram);
//        //RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, 0.0F);
//        //BufferRenderer.drawWithGlobalProgram(buffer.end());
//        RenderLayer.getGui().draw(buffer.end());
    }

    //draws a rounded rectangle with a given radius and color and size
    public static void drawRoundedRect(DrawContext context, int x, int y, int width, int height, int radius, @NotNull int color) {
        //draw the two rectangles
        drawRect(context, x + radius, y, width - radius * 2, height, color);
        drawRect(context, x, y + radius, radius, height - radius * 2, color);
        drawRect(context, x + width - radius, y + radius, radius, height - radius * 2, color);

        //draw the circles
        //drawArc(x + radius, y + radius, radius, 180, 270, color);
        //drawArc(x + width - radius, y + radius, radius, 90, 180, color);
        //drawArc(x + radius, y + height - radius, radius, 270, 360, color);
        //drawArc(x + width - radius, y + height - radius, radius, 0, 90, color);

        drawCircle(context, x + radius, y + radius, radius, color);
        drawCircle(context, x + width - radius + 1, y + radius, radius, color);
        drawCircle(context, x + radius, y + height - radius + 1, radius, color);
        drawCircle(context, x + width - radius + 1, y + height - radius + 1, radius, color);

        //drawRectOutline(x, y, width, height, 1, Color.GREEN);
        //System.out.println("Rounded Rect drawn!");
    }

    //draws a gradient rectangle with a given color and size
    public static void drawGradientRect(int x, int y, int width, int height, Color color1, Color color2) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUADS);
        setColor(color1);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x + width, y);
        setColor(color2);
        GL11.glVertex2d(x + width, y + height);
        GL11.glVertex2d(x, y + height);
        GL11.glEnd();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }


    public static void drawString(DrawContext context, String text, int x, int y, int color) {
        context.drawText(textRenderer, text, x, y, color, false);
    }

    public static void drawStringWithShadow(DrawContext context, String text, int x, int y, int color) {
        context.drawText(textRenderer, text, x, y, color, true);
    }

    public static void drawCenteredString(DrawContext context, String text, int x, int y, int color) {
        context.drawText(
                textRenderer,
                text,
                x - textRenderer.getWidth(text) / 2,
                y,
                color,
                false
        );
    }

    public static void drawCenteredStringWithShadow(DrawContext context, String text, int x, int y, int color) {
        context.drawText(
                textRenderer,
                text,
                x - textRenderer.getWidth(text) / 2,
                y,
                color,
                true
        );
    }

    //draws a string with custom scale
    private static void drawString(DrawContext context, String text, int x, int y, int color, int scale, boolean centered, boolean shadow) {
        MatrixStack ms = new MatrixStack();
        ms.push();
        ms.scale(scale,scale,0);
        context.drawText(textRenderer, text, centered ? x - textRenderer.getWidth(text) / 2 : x, y, color, shadow);
        ms.pop();
    }

    //draws a string with custom scale
    public static void drawString(DrawContext context, String text, int x, int y, int color, int scale) {
        drawString(context, text, x, y, color, scale, false, false);
    }

    //draws a string with custom scale and shadow
    public static void drawStringWithShadow(DrawContext context, String text, int x, int y, int color, int scale) {
        drawString(context, text, x, y, color, scale, false, true);
    }

    public static void drawCenteredString(DrawContext context, String text, int x, int y, int color, int scale) {
        drawString(context, text, x, y, color, scale, true, false);
    }

    public static void drawCenteredStringWithShadow(DrawContext context, String text, int x, int y, int color, int scale) {
        drawString(context, text, x, y, color, scale, true, true);
    }

    //draws an ItemStack at a given position with a given scale
    public static void drawItemStack(DrawContext context, ItemStack itemStack, int x, int y, float scale) {
        context.drawItem(itemStack, x, y, 0);
    }

    /*
    public static void drawItemStackWithText(ItemStack stack, int x, int y, String text) {
        if (stack == null) return;

        RenderItem itemRender = Minecraft.getMinecraft().getRenderItem();
        setColor(Color.WHITE);
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.zLevel = -145;
        itemRender.renderItemAndEffectIntoGUI(stack, x, y);
        itemRender.renderItemOverlayIntoGUI(Minecraft.getMinecraft().fontRendererObj, stack, x, y, text);
        itemRender.zLevel = 0;
        RenderHelper.disableStandardItemLighting();

    }

    public static void drawItemStack(ItemStack stack, int x, int y) {
        drawItemStackWithText(stack, x, y, null);
    }


    public static void drawItemStack(ItemStack itemStack, int x, int y, float scaleX, float scaleY) {
        GL11.glPushMatrix();
        GL11.glScalef(scaleX, scaleY, 0);
        drawItemStack(itemStack, x, y);
        GL11.glPopMatrix();
    }

    //draw centered ItemStack at a given position with a given scale
    public static void drawCenteredItemStack(ItemStack itemStack, int x, int y, float scale) {
        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, scale);
        drawItemStack(itemStack, (int) (x - (scale / 2)), (int) (y - (scale / 2)));
        GL11.glPopMatrix();
    }

     */

    /*
    //draw a check mar with a given color and size using lines
    public static void drawCheckMark(int x, int y, int size, Color color) {
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2);
        setColor(color);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2d(x, y + size / 2);
        GL11.glVertex2d(x + size / 2, y + size);
        GL11.glVertex2d(x + size / 2, y + size);
        GL11.glVertex2d(x + size, y);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    //draw a cross mark with a given color and start and end points
    public static void drawCrossMark(int x, int y, int x2, int y2, Color color) {
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2);
        setColor(color);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x2, y2);
        GL11.glVertex2d(x2, y);
        GL11.glVertex2d(x, y2);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }
     */


    //set alpha of color
    public static Color setAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    //set color
    public static void setColor(int color) {
        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        GL11.glColor4f(red, green, blue, alpha);
    }

    public static void setColor(Color color) {
        setColor(color.getRGB());
    }

    //rotate
    public static void rotate(float angle) {
        GL11.glRotatef(angle, 0.0F, 0.0F, 1.0F);
    }

    public static String lorem(){
        return "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. " +
                "At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet." +
                " Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua." +
                " At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. +" +
                "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. " +
                "At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. " +
                "Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis dolore te feugait nulla facilisi. " +
                "Lorem ipsum dolor sit amet,";
    }

    public static void renderHighlightBox(WorldRenderContext context, double[] minXYZ, double[] maxXYZ, float[] rgba) {
        if (minXYZ.length != 3) throw new ArgumentCountException(minXYZ.length, 3, "Expected 3 values (x/y/z coordinates) in array");
        if (maxXYZ.length != 3) throw new ArgumentCountException(maxXYZ.length, 3, "Expected 3 values (x/y/z coordinates) in array");
        if (rgba.length != 4) throw new ArgumentCountException(maxXYZ.length, 3, "Expected 4 values (r/g/b/a) in array");

        MatrixStack matrices = context.matrixStack();
        Entity player = MinecraftClient.getInstance().player;

        matrices.push();
        if (player.isSneaking()) player.setSneaking(false);
        matrices.translate(-player.getX(), -player.getY(), -player.getZ());

        VertexConsumer buffer = context.consumers().getBuffer(THROUGH_WALLS_LAYER);
        VertexRendering.drawFilledBox(
                matrices, buffer,
                minXYZ[0], minXYZ[1], minXYZ[2],
                maxXYZ[0], maxXYZ[1], maxXYZ[2],
                rgba[0], rgba[1], rgba[2], rgba[3]
        );

        ((VertexConsumerProvider.Immediate)context.consumers()).draw(THROUGH_WALLS_LAYER);
        matrices.pop();
    }
}

