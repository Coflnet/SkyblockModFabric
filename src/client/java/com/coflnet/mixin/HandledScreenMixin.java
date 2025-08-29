package com.coflnet.mixin;

import CoflCore.handlers.DescriptionHandler;
import com.coflnet.CoflModClient;
import com.coflnet.config.TextWidgetPositionConfig;
import com.coflnet.gui.RenderUtils;
import com.coflnet.models.TextElement;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.client.gui.widget.ScrollableTextWidget;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.List;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    
    private static final Gson gson = new Gson();
    
    @Shadow
    protected int x;
    @Shadow
    protected int y;
    @Shadow
    protected int backgroundWidth;
    @Shadow
    protected int backgroundHeight;
    protected MultilineTextWidget sideTextWidget;
    protected List<MutableText> interactiveTextLines;
    
    // Dragging and position storage
    private boolean isDragging = false;
    private double dragStartX, dragStartY;
    private double widgetStartX, widgetStartY;
    private TextWidgetPositionConfig positionConfig;
    private int currentMaxWidth = 100;

    @Inject(at = @At("TAIL"), method = "init")
    public void init(CallbackInfo ci){
        // Load saved position
        positionConfig = TextWidgetPositionConfig.load();
        
        // init to whatever text is present
        DescriptionHandler.DescModification[] extraSlotDesc = CoflModClient.getExtraSlotDescMod();
        updateText(extraSlotDesc);
        String currentTitle = ((HandledScreen<?>) (Object) this).getTitle().getString();
        if(sideTextWidget != null && !currentTitle.equals("Crafting")) {
            sideTextWidget.setAlpha(0.3f); // make it transparent until properly loaded
        }
        DescriptionHandler.setRefreshCallback((lines, title) -> {
            updateText(CoflModClient.getExtraSlotDescMod());
        });
    }

    protected void updateText(DescriptionHandler.DescModification[] lines) {
        if(lines == null || lines.length == 0) {
            sideTextWidget = null;
            interactiveTextLines = null;
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
                    MutableText suggestText = Text.literal("§7Will suggest: §r" + descModification.value.split(": ")[1].trim());
                    interactiveTextLines.add(suggestText);
                    int width = MinecraftClient.getInstance().textRenderer.getWidth(suggestText);
                    if (width > maxWidth) {
                        maxWidth = width;
                    }
                }
                continue;
            }

            String jsonText = descModification.value.trim();
            if (!jsonText.startsWith("[") || !jsonText.endsWith("]")) {
                // Regular text
                MutableText regularText = Text.literal(descModification.value);
                interactiveTextLines.add(regularText);
                int width = MinecraftClient.getInstance().textRenderer.getWidth(regularText);
                if (width > maxWidth) {
                    maxWidth = width;
                }
                continue;
            }

            try {
                Type listType = new TypeToken<List<TextElement>>(){}.getType();
                List<TextElement> textElements = gson.fromJson(jsonText, listType);
                
                MutableText lineText = Text.empty();
                for (int i = 0; i < textElements.size(); i++) {
                    TextElement element = textElements.get(i);
                    MutableText elementText = Text.literal(element.text);
                    
                    // Add click event
                    if (element.onClick != null && !element.onClick.isEmpty()) {
                        if (element.onClick.startsWith("http")) {
                            elementText.styled(style -> {
                                try {
                                    return style.withClickEvent(new ClickEvent.OpenUrl(URI.create(element.onClick)));
                                } catch (Exception e) {
                                    return style.withClickEvent(new ClickEvent.RunCommand(element.onClick));
                                }
                            });
                        } else if(element.onClick.startsWith("suggest:")) {
                            String suggestion = element.onClick.substring("suggest:".length());
                            elementText.styled(style -> style.withClickEvent(new ClickEvent.SuggestCommand(suggestion)));
                        } else if(element.onClick.startsWith("copy:")) {
                            String copyText = element.onClick.substring("copy:".length());
                            elementText.styled(style -> style.withClickEvent(new ClickEvent.CopyToClipboard(copyText)));
                        } else {
                            elementText.styled(style -> style.withClickEvent(new ClickEvent.RunCommand(element.onClick)));
                        }
                    }
                    
                    // Add hover event
                    if (element.hover != null && !element.hover.isEmpty()) {
                        elementText.styled(style -> {
                            // Support multi-line hover text by splitting on \n
                            String[] hoverLines = element.hover.split("\\\\n|\\n");
                            if (hoverLines.length == 1) {
                                // Single line hover
                                HoverEvent hoverEvent = new HoverEvent.ShowText(Text.literal(element.hover));
                                return style.withHoverEvent(hoverEvent);
                            } else {
                                // Multi-line hover - create a list of Text objects for proper multi-line rendering
                                java.util.List<Text> multiLineList = new java.util.ArrayList<>();
                                for (String line : hoverLines) {
                                    multiLineList.add(Text.literal(line));
                                }
                                // Store the multi-line list as a single text component with proper formatting
                                MutableText multiLineText = Text.literal(hoverLines[0]);
                                for (int j = 1; j < hoverLines.length; j++) {
                                    multiLineText.append("\n").append(Text.literal(hoverLines[j]));
                                }
                                HoverEvent hoverEvent = new HoverEvent.ShowText(multiLineText);
                                return style.withHoverEvent(hoverEvent);
                            }
                        });
                    }
                    
                    lineText.append(elementText);
                }
                
                interactiveTextLines.add(lineText);
                int width = MinecraftClient.getInstance().textRenderer.getWidth(lineText);
                if (width > maxWidth) {
                    maxWidth = width;
                }
            } catch (Exception e) {
                // If JSON parsing fails, treat as regular text
                MutableText fallbackText = Text.literal(descModification.value);
                interactiveTextLines.add(fallbackText);
                int width = MinecraftClient.getInstance().textRenderer.getWidth(fallbackText);
                if (width > maxWidth) {
                    maxWidth = width;
                }
            }
        }
        
        // Create the widget for positioning but we'll render manually
        if (interactiveTextLines.isEmpty()) {
            return;
        }

        MutableText combinedText = Text.empty();
        for (int i = 0; i < interactiveTextLines.size(); i++) {
            if (i > 0) combinedText.append("\n");
            combinedText.append(interactiveTextLines.get(i));
        }
        
        // Use custom position relative to GUI
        int widgetX = x + positionConfig.offsetX;
        if(positionConfig.offsetX < 0) { // if it hasn't been respositioned move start over
            widgetX = widgetX - maxWidth;
        }
        currentMaxWidth = maxWidth;
        int widgetY = y + positionConfig.offsetY;
        
        sideTextWidget = new MultilineTextWidget(
                widgetX, widgetY,
                combinedText,
                MinecraftClient.getInstance().textRenderer
        );
        sideTextWidget.setDimensions(maxWidth + 10, interactiveTextLines.size() * 12);
        sideTextWidget.setAlpha(0.9f);
    }

    @Inject(at = @At("TAIL"), method = "render")
    public void renderMain(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci){
        if(sideTextWidget == null) {
            return;
        }

        if(interactiveTextLines == null || interactiveTextLines.isEmpty()) {
            sideTextWidget.render(context, mouseX, mouseY, deltaTicks);
            return;
        }

        // Render interactive text using proper text component rendering
        int startX = sideTextWidget.getX();
        int startY = sideTextWidget.getY();
        int lineHeight = 12;
        
        for (int i = 0; i < interactiveTextLines.size(); i++) {
            MutableText line = interactiveTextLines.get(i);
            int lineY = startY + (i * lineHeight);
            
            // Use the screen's text rendering method to support hover/click events
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, line, startX, lineY, 0xFFFFFF);
            
            // Handle hover tooltips immediately during rendering
            int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(line);
            if (mouseX < startX || mouseX > startX + textWidth || 
                mouseY < lineY || mouseY > lineY + lineHeight) {
                continue;
            }
            
            // Check each sibling for hover events
            int currentX = startX;
            boolean foundHover = false;
            
            for (Text sibling : line.getSiblings()) {
                if (!(sibling instanceof MutableText mutableSibling)) {
                    continue;
                }

                int siblingWidth = MinecraftClient.getInstance().textRenderer.getWidth(mutableSibling);
                
                if (mouseX >= currentX && mouseX <= currentX + siblingWidth) {
                    if (mutableSibling.getStyle().getHoverEvent() != null && 
                        mutableSibling.getStyle().getHoverEvent() instanceof HoverEvent.ShowText showTextEvent) {
                        
                        // Check if the hover text contains newlines and render accordingly
                        String hoverText = showTextEvent.value().getString();
                        if (hoverText.contains("\n")) {
                            // Multi-line tooltip - split into list
                            String[] lines = hoverText.split("\n");
                            java.util.List<Text> tooltipLines = new java.util.ArrayList<>();
                            for (String hoverline : lines) {
                                tooltipLines.add(Text.literal(hoverline));
                            }
                            context.drawTooltip(MinecraftClient.getInstance().textRenderer, 
                                               tooltipLines, 
                                               mouseX, mouseY);
                        } else {
                            // Single line tooltip
                            context.drawTooltip(MinecraftClient.getInstance().textRenderer, 
                                               showTextEvent.value(), 
                                               mouseX, mouseY);
                        }
                        foundHover = true;
                        break;
                    }
                }
                currentX += siblingWidth;
            }
            
            // If no sibling had hover, check the main line
            if (!foundHover && line.getStyle().getHoverEvent() != null && 
                line.getStyle().getHoverEvent() instanceof HoverEvent.ShowText showTextEvent) {
                
                // Check if the hover text contains newlines and render accordingly
                String hoverText = showTextEvent.value().getString();
                if (hoverText.contains("\n")) {
                    // Multi-line tooltip - split into list
                    String[] lines = hoverText.split("\n");
                    java.util.List<Text> tooltipLines = new java.util.ArrayList<>();
                    for (String tooltipLine : lines) {
                        tooltipLines.add(Text.literal(tooltipLine));
                    }
                    context.drawTooltip(MinecraftClient.getInstance().textRenderer, 
                                       tooltipLines, 
                                       mouseX, mouseY);
                } else {
                    // Single line tooltip
                    context.drawTooltip(MinecraftClient.getInstance().textRenderer, 
                                       showTextEvent.value(), 
                                       mouseX, mouseY);
                }
            }
        }
        sideTextWidget.render(context, mouseX, mouseY, deltaTicks);
    }

    @Inject(at = @At("HEAD"), method = "mouseDragged", cancellable = true)
    public void onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (isDragging && button == 1) { // Right mouse button
            // Update widget position based on drag
            double newX = widgetStartX + (mouseX - dragStartX);
            double newY = widgetStartY + (mouseY - dragStartY);
            System.out.println("Dragging to: " + newX + ", " + newY);
            
            // Calculate relative offsets to GUI
            positionConfig.offsetX = (int) (newX - x);
            if(positionConfig.offsetX < -5)
                positionConfig.offsetX += currentMaxWidth; // adjust for left side start
            positionConfig.offsetY = (int) (newY - y);
            
            // Update widget position if it exists
            if (sideTextWidget != null) {
                updateWidgetPosition();
            }
            
            cir.setReturnValue(true);
        }
    }
    
    @Inject(at = @At("HEAD"), method = "mouseReleased", cancellable = true)  
    public void onMouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (isDragging && button == 1) { // Right mouse button
            isDragging = false;
            positionConfig.save(); // Save the new position
            cir.setReturnValue(true);
        }
    }
    
    private void updateWidgetPosition() {
        if (sideTextWidget != null) {
            // Create a new widget at the new position
            MutableText currentText = Text.empty();
            if (interactiveTextLines != null && !interactiveTextLines.isEmpty()) {
                for (int i = 0; i < interactiveTextLines.size(); i++) {
                    if (i > 0) currentText.append("\n");
                    currentText.append(interactiveTextLines.get(i));
                }
            } else {
                // For legacy text, we need to recreate from current widget text
                return; // Skip update for legacy text during drag
            }
            
            int newX = x + positionConfig.offsetX;
            if(positionConfig.offsetX < 0) {
                newX = newX - currentMaxWidth;
            }
            int newY = y + positionConfig.offsetY;
            
            sideTextWidget = new MultilineTextWidget(
                    newX, newY,
                    currentText,
                    MinecraftClient.getInstance().textRenderer
            );
            sideTextWidget.setDimensions(sideTextWidget.getWidth(), sideTextWidget.getHeight());
            sideTextWidget.setAlpha(0.9f);
        }
    }

    @Inject(at = @At("HEAD"), method = "mouseClicked", cancellable = true)
    public void onMouseClicked(double mouseX, double mouseY, int button, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (interactiveTextLines == null || interactiveTextLines.isEmpty() || sideTextWidget == null) {
            return;
        }

        int startX = sideTextWidget.getX();
        int startY = sideTextWidget.getY();
        int lineHeight = 12;
        
        // Check if mouse is over the text widget area
        boolean overWidget = false;
        int widgetWidth = 0;
        int widgetHeight = interactiveTextLines.size() * lineHeight;
        
        for (MutableText line : interactiveTextLines) {
            int lineWidth = MinecraftClient.getInstance().textRenderer.getWidth(line);
            if (lineWidth > widgetWidth) {
                widgetWidth = lineWidth;
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
            for (int i = 0; i < interactiveTextLines.size(); i++) {
                MutableText line = interactiveTextLines.get(i);
                int lineY = startY + (i * lineHeight);
                int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(line);
                
                // Check if mouse is over this line
                if (mouseX < startX || mouseX > startX + textWidth || 
                    mouseY < lineY || mouseY > lineY + lineHeight) {
                    continue;
                }
                
                // Find which text component was clicked by checking character positions
                int currentX = startX;
                for (Text sibling : line.getSiblings()) {
                    if (!(sibling instanceof MutableText mutableSibling)) {
                        continue;
                    }

                    int siblingWidth = MinecraftClient.getInstance().textRenderer.getWidth(mutableSibling);
                    
                    if (mouseX >= currentX && mouseX <= currentX + siblingWidth) {
                        // Handle the click event
                        boolean handled = ((HandledScreen<?>) (Object) this).handleTextClick(mutableSibling.getStyle());
                        if (handled) {
                            cir.setReturnValue(true);
                            return;
                        }
                    }
                    currentX += siblingWidth;
                }
                
                // If no sibling was clicked, try the main text
                boolean handled = ((HandledScreen<?>) (Object) this).handleTextClick(line.getStyle());
                if (handled) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }
}
