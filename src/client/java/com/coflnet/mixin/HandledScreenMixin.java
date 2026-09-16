package com.coflnet.mixin;

import CoflCore.handlers.DescriptionHandler;
import com.coflnet.CoflModClient;
import com.coflnet.PerfTracer;
import com.coflnet.config.TextWidgetPositionConfig;
import com.coflnet.core.InfoDisplayLayout;
import com.coflnet.gui.RenderUtils;
import com.coflnet.gui.hud.InfoDisplayRenderer;
import net.minecraft.client.gui.screens.ChatScreen;
import com.coflnet.models.TextElement;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.screens.Screen;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin extends Screen {

    // Need protected constructor for the Screen parent - used for mixin compilation only
    protected HandledScreenMixin(Component title) {
        super(title);
    }

    private static final Gson gson = new Gson();

    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;
    @Shadow
    protected int imageWidth;
    @Shadow
    protected int imageHeight;
    protected MultiLineTextWidget sideTextWidget;
    // Always keep non-null; updates replace the reference with a fresh list. Volatile for visibility across threads.
    protected volatile List<MutableComponent> interactiveTextLines = new java.util.ArrayList<>();

    /**
     * Precomputed per-frame-invariant data for one rendered line: its measured
     * pixel width, its siblings (each with their own measured width, click
     * event and precomputed hover tooltip), and a fallback click/hover for the
     * line itself. Built once in {@link #updateTextWithJson} instead of being
     * re-measured (font.width) and re-split (hover text) every single frame in
     * {@link #renderMain} and on every click in {@link #onMouseClicked}.
     */
    private record HoverInfo(Component single, List<Component> multi) {
        static HoverInfo of(net.minecraft.network.chat.Style style) {
            if (style == null) return null;
            HoverEvent hoverEvent = style.getHoverEvent();
            if (!(hoverEvent instanceof HoverEvent.ShowText showTextEvent)) return null;
            String hoverText = showTextEvent.value().getString();
            String[] hoverLines = hoverText.split("\\\\n|\\n");
            if (hoverLines.length == 1) {
                return new HoverInfo(showTextEvent.value(), null);
            }
            List<Component> multiLineList = new java.util.ArrayList<>();
            for (String hoverLine : hoverLines) {
                multiLineList.add(Component.literal(hoverLine));
            }
            return new HoverInfo(null, multiLineList);
        }

        void apply(GuiGraphicsExtractor context, Font font, int mouseX, int mouseY) {
            if (multi != null) {
                context.setComponentTooltipForNextFrame(font, multi, mouseX, mouseY);
            } else if (single != null) {
                context.setTooltipForNextFrame(font, single, mouseX, mouseY);
            }
        }
    }

    private record SiblingInfo(MutableComponent component, int width, ClickEvent clickEvent, HoverInfo hover) {
    }

    private record LineInfo(MutableComponent line, int width, List<SiblingInfo> siblings,
                             ClickEvent clickEvent, HoverInfo hover) {
    }

    // Cache invalidated/rebuilt only when updateTextWithJson runs (new/changed InfoDisplay data),
    // not every frame.
    private volatile List<LineInfo> lineInfos = List.of();

    // Dragging and position storage
    private boolean isDragging = false;
    private double dragStartX, dragStartY;
    private double widgetStartX, widgetStartY;
    private TextWidgetPositionConfig positionConfig;
    private int currentMaxWidth = 100;

    @Inject(at = @At("HEAD"), method = "removed")
    public void onRemoved(CallbackInfo ci){
        try {
            // Fires whenever this container view goes away for ANY reason: an ESC/close AND when
            // Hypixel replaces it with another window (e.g. switching backpacks via the nav items,
            // which does not trigger onClose). Resend the outgoing storage contents so edits made
            // after the on-open upload aren't lost on navigation. Deduped via lastNbtRequest, so an
            // unchanged view is a no-op.
            CoflModClient.resendStorageOnClose(this);
        } catch (Exception e) {
            System.out.println("[HandledScreenMixin] resend on removed failed: " + e.getMessage());
        }
    }

    @Inject(at = @At("TAIL"), method = "init")
    public void init(CallbackInfo ci){
        try {
            // Load saved position
            positionConfig = TextWidgetPositionConfig.load();

            // init to whatever text is present
            DescriptionHandler.DescModification[] extraSlotDesc = CoflModClient.getExtraSlotDescMod();
            updateText(extraSlotDesc);
            String currentTitle = ((AbstractContainerScreen<?>) (Object) this).getTitle().getString();
            if(sideTextWidget != null && !currentTitle.equals("Crafting")) {
                sideTextWidget.setAlpha(0.3f); // make it transparent until properly loaded
            }
            DescriptionHandler.setRefreshCallback((lines, title) -> {
                try {
                    // Ensure UI update runs on the client (render) thread
                    Minecraft.getInstance().execute(() -> {
                        try {
                            updateText(CoflModClient.getExtraSlotDescMod());
                            // Descriptions changed - invalidate per-slot caches (e.g. ItemHighlightMixin).
                            CoflModClient.descriptionsVersion.incrementAndGet();
                        } catch (Exception ex) {
                            System.out.println("[HandledScreenMixin] refresh callback (on client) failed: " + ex.getMessage());
                        }
                    });
                } catch (Exception e) {
                    System.out.println("[HandledScreenMixin] refresh callback failed: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            System.out.println("[HandledScreenMixin] init failed: " + e.getMessage());
        }
    }

    protected void updateText(DescriptionHandler.DescModification[] lines) {
        if(lines == null || lines.length == 0) {
            sideTextWidget = null;
            // Keep list non-null; reset to empty
            interactiveTextLines = java.util.Collections.emptyList();
            lineInfos = List.of();
            return;
        }

        updateTextWithJson(lines);
    }

    protected void updateTextWithJson(DescriptionHandler.DescModification[] lines) {
    interactiveTextLines = new java.util.ArrayList<>();
        int maxWidth = 0;

        for (DescriptionHandler.DescModification descModification : lines) {
            if (!descModification.type.equals("APPEND") || descModification.value == null) {
                if (descModification.type.equals("SUGGEST")) {
                    MutableComponent suggestText = Component.literal("§7Will suggest: §r" + descModification.value.split(": ")[1].trim());
                    interactiveTextLines.add(suggestText);
                    int width = Minecraft.getInstance().font.width(suggestText);
                    if (width > maxWidth) {
                        maxWidth = width;
                    }
                }
                continue;
            }

            String jsonText = descModification.value.trim();
            if (!jsonText.startsWith("[") || !jsonText.endsWith("]")) {
                // Regular text
                MutableComponent regularText = Component.literal(descModification.value);
                interactiveTextLines.add(regularText);
                int width = Minecraft.getInstance().font.width(regularText);
                if (width > maxWidth) {
                    maxWidth = width;
                }
                continue;
            }

            try {
                TextElement[] textElements = gson.fromJson(jsonText, TextElement[].class);
                if (textElements == null || textElements.length == 0) {
                    MutableComponent fallbackText = Component.literal(descModification.value);
                    interactiveTextLines.add(fallbackText);
                    int width = Minecraft.getInstance().font.width(fallbackText);
                    if (width > maxWidth) {
                        maxWidth = width;
                    }
                    continue;
                }

                MutableComponent lineText = Component.empty();
                for (int i = 0; i < textElements.length; i++) {
                    TextElement element = textElements[i];
                    MutableComponent elementText = Component.literal(element.text);

                    // Add click event
                    if (element.onClick != null && !element.onClick.isEmpty()) {
                        if (element.onClick.startsWith("http")) {
                            elementText.withStyle(style -> {
                                try {
                                    return style.withClickEvent(new ClickEvent.OpenUrl(URI.create(element.onClick)));
                                } catch (Exception e) {
                                    return style.withClickEvent(new ClickEvent.RunCommand(element.onClick));
                                }
                            });
                        } else if(element.onClick.startsWith("suggest:")) {
                            String suggestion = element.onClick.substring("suggest:".length());
                            elementText.withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand(suggestion)));
                        } else if(element.onClick.startsWith("copy:")) {
                            String copyText = element.onClick.substring("copy:".length());
                            elementText.withStyle(style -> style.withClickEvent(new ClickEvent.CopyToClipboard(copyText)));
                        } else {
                            elementText.withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(element.onClick)));
                        }
                    }

                    // Add hover event
                    if (element.hover != null && !element.hover.isEmpty()) {
                        elementText.withStyle(style -> {
                            // Support multi-line hover text by splitting on \n
                            String[] hoverLines = element.hover.split("\\\\n|\\n");
                            if (hoverLines.length == 1) {
                                // Single line hover
                                HoverEvent hoverEvent = new HoverEvent.ShowText(Component.literal(element.hover));
                                return style.withHoverEvent(hoverEvent);
                            } else {
                                // Multi-line hover - create a list of Text objects for proper multi-line rendering
                                java.util.List<Component> multiLineList = new java.util.ArrayList<>();
                                for (String line : hoverLines) {
                                    multiLineList.add(Component.literal(line));
                                }
                                // Store the multi-line list as a single text component with proper formatting
                                MutableComponent multiLineText = Component.literal(hoverLines[0]);
                                for (int j = 1; j < hoverLines.length; j++) {
                                    multiLineText.append("\n").append(Component.literal(hoverLines[j]));
                                }
                                HoverEvent hoverEvent = new HoverEvent.ShowText(multiLineText);
                                return style.withHoverEvent(hoverEvent);
                            }
                        });
                    }

                    lineText.append(elementText);
                }

                interactiveTextLines.add(lineText);
                int width = Minecraft.getInstance().font.width(lineText);
                if (width > maxWidth) {
                    maxWidth = width;
                }
            } catch (Exception e) {
                // If JSON parsing fails, treat as regular text
                MutableComponent fallbackText = Component.literal(descModification.value);
                interactiveTextLines.add(fallbackText);
                int width = Minecraft.getInstance().font.width(fallbackText);
                if (width > maxWidth) {
                    maxWidth = width;
                }
            }
        }

        // Create the widget for positioning but we'll render manually
    if (interactiveTextLines == null || interactiveTextLines.isEmpty()) {
            lineInfos = List.of();
            return;
        }

        MutableComponent combinedText = Component.empty();
        List<MutableComponent> linesSnapshot = this.interactiveTextLines;
        for (int i = 0; i < linesSnapshot.size(); i++) {
            if (i > 0) combinedText.append("\n");
            combinedText.append(linesSnapshot.get(i));
        }

        int widgetWidth = maxWidth + 10;
        int widgetHeight = linesSnapshot.size() * Minecraft.getInstance().font.lineHeight;
        int widgetX;
        int widgetY;
        if (positionConfig.offsetX == -5 && positionConfig.offsetY == 5) {
            String title = ((AbstractContainerScreen<?>) (Object) this).getTitle().getString();
            var container = new InfoDisplayLayout.Rect(leftPos, topPos, imageWidth, imageHeight);
            var placement = InfoDisplayLayout.placeDefault(
                    title, width, height, container, widgetWidth, widgetHeight);
            widgetX = placement.x();
            widgetY = placement.y();
        } else {
            widgetX = leftPos + positionConfig.offsetX;
            if (positionConfig.offsetX < 0) {
                widgetX -= maxWidth;
            }
            widgetY = topPos + positionConfig.offsetY;
        }
        currentMaxWidth = maxWidth;

        sideTextWidget = new MultiLineTextWidget(
                widgetX, widgetY,
                combinedText,
                Minecraft.getInstance().font
        );
        sideTextWidget.setSize(widgetWidth, widgetHeight);
        sideTextWidget.setAlpha(0.9f);

        // Precompute everything renderMain/onMouseClicked need per frame/click, once here.
        lineInfos = buildLineInfos(linesSnapshot);
    }

    private static List<LineInfo> buildLineInfos(List<MutableComponent> lines) {
        Font font = Minecraft.getInstance().font;
        List<LineInfo> infos = new java.util.ArrayList<>(lines.size());
        for (MutableComponent line : lines) {
            int lineWidth = font.width(line);
            HoverInfo lineHover = HoverInfo.of(line.getStyle());
            ClickEvent lineClick = line.getStyle().getClickEvent();

            List<SiblingInfo> siblings = new java.util.ArrayList<>();
            for (Component sibling : line.getSiblings()) {
                if (!(sibling instanceof MutableComponent mutableSibling)) {
                    continue;
                }
                int siblingWidth = font.width(mutableSibling);
                HoverInfo siblingHover = HoverInfo.of(mutableSibling.getStyle());
                ClickEvent siblingClick = mutableSibling.getStyle().getClickEvent();
                siblings.add(new SiblingInfo(mutableSibling, siblingWidth, siblingClick, siblingHover));
            }
            infos.add(new LineInfo(line, lineWidth, siblings, lineClick, lineHover));
        }
        return infos;
    }

    @Inject(at = @At("TAIL"), method = "extractRenderState")
    public void renderMain(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci){
        long perfStart = PerfTracer.begin();
        try {
            if(sideTextWidget == null) {
                return;
            }

            // Snapshot to avoid races if another thread updates the list while rendering
            List<LineInfo> infos = this.lineInfos;
            if(infos == null || infos.isEmpty()) {
                sideTextWidget.extractRenderState(context, mouseX, mouseY, deltaTicks);
                return;
            }

            Font font = Minecraft.getInstance().font;
            int startX = sideTextWidget.getX();
            int startY = sideTextWidget.getY();
            int lineHeight = font.lineHeight;

            for (int i = 0; i < infos.size(); i++) {
                LineInfo info = infos.get(i);
                int lineY = startY + (i * lineHeight);

                // Use the screen's text rendering method to support hover/click events
                context.text(font, info.line(), startX, lineY, 0xFFFFFF, true);

                // Handle hover tooltips immediately during rendering
                if (mouseX < startX || mouseX > startX + info.width() ||
                    mouseY < lineY || mouseY > lineY + lineHeight) {
                    continue;
                }

                // Check each sibling for hover events (widths already measured in buildLineInfos)
                int currentX = startX;
                boolean foundHover = false;

                for (SiblingInfo sibling : info.siblings()) {
                    if (mouseX >= currentX && mouseX <= currentX + sibling.width()) {
                        if (sibling.hover() != null) {
                            sibling.hover().apply(context, font, mouseX, mouseY);
                            foundHover = true;
                            break;
                        }
                    }
                    currentX += sibling.width();
                }

                // If no sibling had hover, check the main line
                if (!foundHover && info.hover() != null) {
                    info.hover().apply(context, font, mouseX, mouseY);
                }
            }
            // for an unknown reason the single lines don't render anymore on 1.21.8+ so we render the widget as well
            sideTextWidget.extractRenderState(context, mouseX, mouseY, deltaTicks);
        } catch (Exception e) {
            System.out.println("[HandledScreenMixin] renderMain failed: " + e.getMessage());
        } finally {
            PerfTracer.end("handledScreenMixin.renderMain", perfStart);
        }
    }

    @Inject(at = @At("HEAD"), method = "mouseDragged", cancellable = true)
    public void onMouseDragged(net.minecraft.client.input.MouseButtonEvent click, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        try {
            // The Click object contains the mouse coordinates and button used for the drag
            int button = click.button();
            double mouseX = click.x();
            double mouseY = click.y();

            if (isDragging && button == 1) { // Right mouse button
                // Update widget position based on drag
                double newX = widgetStartX + (mouseX - dragStartX);
                double newY = widgetStartY + (mouseY - dragStartY);

                // Calculate relative offsets to GUI
                positionConfig.offsetX = (int) (newX - leftPos);
                if(positionConfig.offsetX < -5)
                    positionConfig.offsetX += currentMaxWidth; // adjust for left side start
                positionConfig.offsetY = (int) (newY - topPos);

                // Update widget position if it exists
                if (sideTextWidget != null) {
                    updateWidgetPosition();
                }

                cir.setReturnValue(true);
            }
        } catch (Exception e) {
            System.out.println("[HandledScreenMixin] mouseDragged failed: " + e.getMessage());
        }
    }

    @Inject(at = @At("HEAD"), method = "mouseReleased", cancellable = true)
    public void onMouseReleased(net.minecraft.client.input.MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
        try {
            int button = click.button();
            if (isDragging && button == 1) { // Right mouse button
                isDragging = false;
                positionConfig.save(); // Save the new position
                cir.setReturnValue(true);
            }
        } catch (Exception e) {
            System.out.println("[HandledScreenMixin] mouseReleased failed: " + e.getMessage());
        }
    }

    private void updateWidgetPosition() {
        if (sideTextWidget != null) {
            // Create a new widget at the new position
            MutableComponent currentText = Component.empty();
            List<MutableComponent> linesSnapshot = this.interactiveTextLines;
            if (linesSnapshot != null && !linesSnapshot.isEmpty()) {
                for (int i = 0; i < linesSnapshot.size(); i++) {
                    if (i > 0) currentText.append("\n");
                    currentText.append(linesSnapshot.get(i));
                }
            } else {
                // For legacy text, we need to recreate from current widget text
                return; // Skip update for legacy text during drag
            }

            int newX = leftPos + positionConfig.offsetX;
            if(positionConfig.offsetX < 0) {
                newX = newX - currentMaxWidth;
            }
            int newY = topPos + positionConfig.offsetY;

            sideTextWidget = new MultiLineTextWidget(
                    newX, newY,
                    currentText,
                    Minecraft.getInstance().font
            );
            sideTextWidget.setSize(sideTextWidget.getWidth(), sideTextWidget.getHeight());
            sideTextWidget.setAlpha(0.9f);
        }
    }

    @Inject(at = @At("HEAD"), method = "mouseClicked", cancellable = true)
    public void onMouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubleClick, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        long perfStart = PerfTracer.begin();
        try {
            var hudStyle = InfoDisplayRenderer.styleAt(click.x(), click.y(), width, height);
            if (click.button() == 0 && hudStyle != null && hudStyle.getClickEvent() != null) {
                handleInfoDisplayClickEvent(hudStyle.getClickEvent());
                cir.setReturnValue(true);
                return;
            }

            double mouseX = click.x();
            double mouseY = click.y();
            int button = click.button();

            List<LineInfo> infos = this.lineInfos;
            if (infos == null || infos.isEmpty() || sideTextWidget == null) {
                return;
            }

            int startX = sideTextWidget.getX();
            int startY = sideTextWidget.getY();
            int lineHeight = Minecraft.getInstance().font.lineHeight;

            // Check if mouse is over the text widget area (widths already measured in buildLineInfos)
            boolean overWidget = false;
            int widgetWidth = 0;
            int widgetHeight = infos.size() * lineHeight;

            for (LineInfo info : infos) {
                if (info.width() > widgetWidth) {
                    widgetWidth = info.width();
                }
            }

            if (mouseX >= startX && mouseX <= startX + widgetWidth &&
                mouseY >= startY && mouseY <= startY + widgetHeight) {
                overWidget = true;
            }

            // Handle right-click for dragging
            if (button == 1 && overWidget) { // Right mouse button
                isDragging = true;
                dragStartX = mouseX;
                dragStartY = mouseY;
                widgetStartX = startX;
                widgetStartY = startY;
                cir.setReturnValue(true);
                return;
            }

            // Handle left-click for text interactions (existing functionality)
            if (button == 0) { // Left mouse button
                for (int i = 0; i < infos.size(); i++) {
                    LineInfo info = infos.get(i);
                    int lineY = startY + (i * lineHeight);

                    // Check if mouse is over this line
                    if (mouseX < startX || mouseX > startX + info.width() ||
                        mouseY < lineY || mouseY > lineY + lineHeight) {
                        continue;
                    }

                    // Find which text component was clicked by checking character positions
                    int currentX = startX;
                    for (SiblingInfo sibling : info.siblings()) {
                        if (mouseX >= currentX && mouseX <= currentX + sibling.width()) {
                            if (sibling.clickEvent() != null) {
                                handleInfoDisplayClickEvent(sibling.clickEvent());
                                cir.setReturnValue(true);
                                return;
                            }
                        }
                        currentX += sibling.width();
                    }

                    // If no sibling was clicked, try the main text
                    if (info.clickEvent() != null) {
                        handleInfoDisplayClickEvent(info.clickEvent());
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[HandledScreenMixin] mouseClicked failed: " + e.getMessage());
        } finally {
            PerfTracer.end("handledScreenMixin.onMouseClicked", perfStart);
        }
    }

    private void handleInfoDisplayClickEvent(ClickEvent clickEvent) {
        if (clickEvent instanceof ClickEvent.RunCommand runCommand) {
            String command = runCommand.command();
            // Arm a sign fill instead of sending to the server, and auto-open the Custom Amount sign
            // when unambiguous (mirrors the bazaar-search flow). Format: "fillsign:<4th line>: <value>".
            if (command.startsWith("fillsign:")) {
                com.coflnet.CoflModClient.armSignFillAndOpen(command.substring("fillsign:".length()));
                return;
            }
            var player = Minecraft.getInstance().player;
            if (player != null && player.connection != null) {
                if (command.startsWith("/")) {
                    player.connection.sendCommand(command.substring(1));
                } else {
                    player.connection.sendChat(command);
                }
            }
        } else if (clickEvent instanceof ClickEvent.SuggestCommand suggestion) {
            Minecraft.getInstance().gui.setScreen(new ChatScreen(suggestion.command(), false));
        } else {
            Screen.defaultHandleClickEvent(clickEvent, Minecraft.getInstance(), this);
        }
    }
}
