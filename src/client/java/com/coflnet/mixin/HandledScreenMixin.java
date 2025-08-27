package com.coflnet.mixin;

import CoflCore.handlers.DescriptionHandler;
import com.coflnet.CoflModClient;
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

    @Inject(at = @At("TAIL"), method = "init")
    public void init(CallbackInfo ci){
        System.out.println("[HandledScreenMixin] Init called");
        // init to whatever text is present
        DescriptionHandler.DescModification[] extraSlotDesc = CoflModClient.getExtraSlotDescMod();
        System.out.println("[HandledScreenMixin] Got extraSlotDesc: " + (extraSlotDesc != null ? extraSlotDesc.length + " items" : "null"));
        updateText(extraSlotDesc);
        String currentTitle = ((HandledScreen<?>) (Object) this).getTitle().getString();
        System.out.println("[HandledScreenMixin] Current title: " + currentTitle);
        if(sideTextWidget != null && !currentTitle.equals("Crafting")) {
            System.out.println("[HandledScreenMixin] Setting widget alpha to 0.3f");
            sideTextWidget.setAlpha(0.3f); // make it transparent until properly loaded
        }
        DescriptionHandler.setRefreshCallback((lines, title) -> {
            System.out.println("[HandledScreenMixin] Refresh callback triggered for " + title + " with " + lines.length + " lines");
            updateText(CoflModClient.getExtraSlotDescMod());
        });
    }

    protected void updateText(DescriptionHandler.DescModification[] lines) {
        System.out.println("[HandledScreenMixin] updateText called with " + (lines != null ? lines.length + " lines" : "null"));
        if(lines == null || lines.length == 0) {
            System.out.println("[HandledScreenMixin] No lines to display, clearing widgets");
            sideTextWidget = null;
            interactiveTextLines = null;
            return;
        }

        // Check if any line contains JSON format
        boolean hasJsonFormat = false;
        for (DescriptionHandler.DescModification descModification : lines) {
            System.out.println("[HandledScreenMixin] Line type: " + descModification.type + ", value: " + descModification.value);
            if (descModification.value != null && descModification.value.trim().startsWith("[") && descModification.value.trim().endsWith("]")) {
                System.out.println("[HandledScreenMixin] Found JSON format in line: " + descModification.value);
                hasJsonFormat = true;
                break;
            }
        }

        System.out.println("[HandledScreenMixin] Has JSON format: " + hasJsonFormat);
        if (hasJsonFormat) {
            updateTextWithJson(lines);
        } else {
            updateTextLegacy(lines);
        }
    }

    protected void updateTextWithJson(DescriptionHandler.DescModification[] lines) {
        System.out.println("[HandledScreenMixin] updateTextWithJson called");
        interactiveTextLines = new java.util.ArrayList<>();
        int maxWidth = 0;
        
        for (DescriptionHandler.DescModification descModification : lines) {
            System.out.println("[HandledScreenMixin] Processing line: " + descModification.type + " = " + descModification.value);
            if(descModification.type.equals("APPEND") && descModification.value != null) {
                String jsonText = descModification.value.trim();
                if (jsonText.startsWith("[") && jsonText.endsWith("]")) {
                    System.out.println("[HandledScreenMixin] Parsing JSON: " + jsonText);
                    try {
                        Type listType = new TypeToken<List<TextElement>>(){}.getType();
                        List<TextElement> textElements = gson.fromJson(jsonText, listType);
                        System.out.println("[HandledScreenMixin] Parsed " + textElements.size() + " text elements");
                        
                        MutableText lineText = Text.empty();
                        for (int i = 0; i < textElements.size(); i++) {
                            TextElement element = textElements.get(i);
                            System.out.println("[HandledScreenMixin] Element " + i + ": text='" + element.text + "', hover='" + element.hover + "', onClick='" + element.onClick + "'");
                            MutableText elementText = Text.literal(element.text);
                            
                            // Add click event
                            if (element.onClick != null && !element.onClick.isEmpty()) {
                                System.out.println("[HandledScreenMixin] Adding click event: " + element.onClick);
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
                                System.out.println("[HandledScreenMixin] Adding hover event: " + element.hover);
                                elementText.styled(style -> {
                                    HoverEvent hoverEvent = new HoverEvent.ShowText(Text.of(element.hover));
                                    System.out.println("[HandledScreenMixin] Created hover event: " + hoverEvent);
                                    return style.withHoverEvent(hoverEvent);
                                });
                                System.out.println("[HandledScreenMixin] Element text after adding hover: " + elementText.getStyle().getHoverEvent());
                            }
                            
                            lineText.append(elementText);
                        }
                        
                        interactiveTextLines.add(lineText);
                        int width = MinecraftClient.getInstance().textRenderer.getWidth(lineText);
                        System.out.println("[HandledScreenMixin] Line width: " + width);
                        if (width > maxWidth) {
                            maxWidth = width;
                        }
                    } catch (Exception e) {
                        System.out.println("[HandledScreenMixin] JSON parsing failed: " + e.getMessage());
                        e.printStackTrace();
                        // If JSON parsing fails, treat as regular text
                        MutableText fallbackText = Text.literal(descModification.value);
                        interactiveTextLines.add(fallbackText);
                        int width = MinecraftClient.getInstance().textRenderer.getWidth(fallbackText);
                        if (width > maxWidth) {
                            maxWidth = width;
                        }
                    }
                } else {
                    System.out.println("[HandledScreenMixin] Regular text (not JSON): " + jsonText);
                    // Regular text
                    MutableText regularText = Text.literal(descModification.value);
                    interactiveTextLines.add(regularText);
                    int width = MinecraftClient.getInstance().textRenderer.getWidth(regularText);
                    if (width > maxWidth) {
                        maxWidth = width;
                    }
                }
            } else if(descModification.type.equals("SUGGEST")) {
                System.out.println("[HandledScreenMixin] Processing SUGGEST type");
                MutableText suggestText = Text.literal("§7Will suggest: §r" + descModification.value.split(": ")[1].trim());
                interactiveTextLines.add(suggestText);
                int width = MinecraftClient.getInstance().textRenderer.getWidth(suggestText);
                if (width > maxWidth) {
                    maxWidth = width;
                }
            }
        }

        System.out.println("[HandledScreenMixin] Final maxWidth: " + maxWidth + ", lines: " + interactiveTextLines.size());
        
        // Create the widget for positioning but we'll render manually
        if (!interactiveTextLines.isEmpty()) {
            MutableText combinedText = Text.empty();
            for (int i = 0; i < interactiveTextLines.size(); i++) {
                if (i > 0) combinedText.append("\n");
                combinedText.append(interactiveTextLines.get(i));
            }
            
            int widgetX = x - maxWidth - 5;
            int widgetY = y + 5;
            System.out.println("[HandledScreenMixin] Creating widget at position (" + widgetX + ", " + widgetY + ") with dimensions " + (maxWidth + 10) + "x" + (interactiveTextLines.size() * 12));
            
            sideTextWidget = new MultilineTextWidget(
                    widgetX, widgetY,
                    combinedText,
                    MinecraftClient.getInstance().textRenderer
            );
            sideTextWidget.setDimensions(maxWidth + 10, interactiveTextLines.size() * 12);
            sideTextWidget.setAlpha(0.9f);
            System.out.println("[HandledScreenMixin] Widget created successfully");
        } else {
            System.out.println("[HandledScreenMixin] No interactive text lines to display");
        }
    }

    protected void updateTextLegacy(DescriptionHandler.DescModification[] lines) {
        System.out.println("[HandledScreenMixin] updateTextLegacy called");
        String temp = "";
        int maxWidth = 0;
        int lineCount = 0;
        for (DescriptionHandler.DescModification descModification : lines) {
            if(descModification.type.equals("APPEND")) {
                temp = temp + "\n" + descModification.value;
                int width = MinecraftClient.getInstance().textRenderer.getWidth(descModification.value);
                if(width > maxWidth) {
                    maxWidth = width;
                }
            }
            else if(descModification.type.equals("SUGGEST")) {
                temp += "\n§7Will suggest: §r" +descModification.value.split(": ")[1].trim();
            }
            else
                continue;
            lineCount++;
        }
        System.out.println("[HandledScreenMixin] Legacy text: '" + temp + "', maxWidth: " + maxWidth + ", lineCount: " + lineCount);
        
        if (lineCount > 0) {
            int widgetX = x - maxWidth - 5;
            int widgetY = y + 5;
            System.out.println("[HandledScreenMixin] Creating legacy widget at position (" + widgetX + ", " + widgetY + ") with dimensions " + (maxWidth + 10) + "x" + (lineCount * 20));
            
            sideTextWidget = new MultilineTextWidget(
                    widgetX, widgetY,
                    Text.of(temp),
                    MinecraftClient.getInstance().textRenderer
            );
            sideTextWidget.setDimensions(maxWidth + 10, lineCount * 20);
            sideTextWidget.setAlpha(0.9f);
            System.out.println("[HandledScreenMixin] Legacy widget created successfully");
        } else {
            System.out.println("[HandledScreenMixin] No legacy text to display");
        }
        interactiveTextLines = null;
    }

    @Inject(at = @At("TAIL"), method = "render")
    public void renderMain(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci){
        if(sideTextWidget != null) {
            if(interactiveTextLines != null && !interactiveTextLines.isEmpty()) {
                // Render interactive text using proper text component rendering
                int startX = sideTextWidget.getX();
                int startY = sideTextWidget.getY();
                int lineHeight = 12;
                
                for (int i = 0; i < interactiveTextLines.size(); i++) {
                    MutableText line = interactiveTextLines.get(i);
                    int lineY = startY + (i * lineHeight);
                    System.out.println("[HandledScreenMixin] Rendering line " + i + " at y=" + lineY + ": " + line.getString());
                    
                    // Use the screen's text rendering method to support hover/click events
                    context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, line, startX, lineY, 0xFFFFFF);
                    
                    // Handle hover tooltips immediately during rendering
                    int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(line);
                    if (mouseX >= startX && mouseX <= startX + textWidth && 
                        mouseY >= lineY && mouseY <= lineY + lineHeight) {
                        
                        System.out.println("[HandledScreenMixin] Mouse hovering during render - line " + i + " at (" + mouseX + ", " + mouseY + ")");
                        
                        // Check each sibling for hover events
                        int currentX = startX;
                        boolean foundHover = false;
                        
                        for (Text sibling : line.getSiblings()) {
                            if (sibling instanceof MutableText mutableSibling) {
                                int siblingWidth = MinecraftClient.getInstance().textRenderer.getWidth(mutableSibling);
                                
                                if (mouseX >= currentX && mouseX <= currentX + siblingWidth) {
                                    if (mutableSibling.getStyle().getHoverEvent() != null && 
                                        mutableSibling.getStyle().getHoverEvent() instanceof HoverEvent.ShowText showTextEvent) {
                                        
                                        System.out.println("[HandledScreenMixin] Showing hover tooltip during render: " + showTextEvent.value().getString());
                                        context.drawTooltip(MinecraftClient.getInstance().textRenderer, 
                                                           showTextEvent.value(), 
                                                           mouseX, mouseY);
                                        foundHover = true;
                                        break;
                                    }
                                }
                                currentX += siblingWidth;
                            }
                        }
                        
                        // If no sibling had hover, check the main line
                        if (!foundHover && line.getStyle().getHoverEvent() != null && 
                            line.getStyle().getHoverEvent() instanceof HoverEvent.ShowText showTextEvent) {
                            
                            System.out.println("[HandledScreenMixin] Showing main line hover tooltip during render: " + showTextEvent.value().getString());
                            context.drawTooltip(MinecraftClient.getInstance().textRenderer, 
                                               showTextEvent.value(), 
                                               mouseX, mouseY);
                        }
                    }
                }
            } else {
                // Render standard widget
                System.out.println("[HandledScreenMixin] Rendering standard widget");
            }
            sideTextWidget.render(context, mouseX, mouseY, deltaTicks);
        } else {
            // Uncomment next line only if you want to see when there's no widget (will be very spammy)
            // System.out.println("[HandledScreenMixin] No sideTextWidget to render");
        }
    }

    @Inject(at = @At("HEAD"), method = "mouseClicked", cancellable = true)
    public void onMouseClicked(double mouseX, double mouseY, int button, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (interactiveTextLines != null && !interactiveTextLines.isEmpty() && sideTextWidget != null) {
            int startX = sideTextWidget.getX();
            int startY = sideTextWidget.getY();
            int lineHeight = 12;
            
            for (int i = 0; i < interactiveTextLines.size(); i++) {
                MutableText line = interactiveTextLines.get(i);
                int lineY = startY + (i * lineHeight);
                int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(line);
                
                // Check if mouse is over this line
                if (mouseX >= startX && mouseX <= startX + textWidth && 
                    mouseY >= lineY && mouseY <= lineY + lineHeight) {
                    
                    System.out.println("[HandledScreenMixin] Mouse clicked on line " + i + " at (" + mouseX + ", " + mouseY + ")");
                    
                    // Find which text component was clicked by checking character positions
                    int currentX = startX;
                    for (Text sibling : line.getSiblings()) {
                        if (sibling instanceof MutableText mutableSibling) {
                            int siblingWidth = MinecraftClient.getInstance().textRenderer.getWidth(mutableSibling);
                            
                            if (mouseX >= currentX && mouseX <= currentX + siblingWidth) {
                                System.out.println("[HandledScreenMixin] Clicked on text: '" + mutableSibling.getString() + "'");
                                
                                // Handle the click event
                                boolean handled = ((HandledScreen<?>) (Object) this).handleTextClick(mutableSibling.getStyle());
                                if (handled) {
                                    System.out.println("[HandledScreenMixin] Click handled successfully");
                                    cir.setReturnValue(true);
                                    return;
                                }
                            }
                            currentX += siblingWidth;
                        }
                    }
                    
                    // If no sibling was clicked, try the main text
                    boolean handled = ((HandledScreen<?>) (Object) this).handleTextClick(line.getStyle());
                    if (handled) {
                        System.out.println("[HandledScreenMixin] Main line click handled successfully");
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }
        }
    }
}
