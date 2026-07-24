package com.coflnet.gui.cofl;

import com.coflnet.config.LoreManager;
import com.coflnet.lore.LoreData;
import com.coflnet.lore.LoreField;
import com.coflnet.lore.LoreModule;
import com.coflnet.lore.LoreParser;
import com.coflnet.lore.LoreTemplate;
import com.coflnet.gui.RenderUtils;

import CoflCore.handlers.DescriptionHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * the lore configuration gui a custom screen in the skycofl flat panel idiom
 * (flat panels via {@link RenderUtils}, the shared {@link CoflColConfig} palette,
 * manual hit-testing, {@link EditBox} widgets, deferred {@code setComponentTooltip}
 * hovers). Replaces the {@code /cofl lore} chat menu.
 *
 * the lore engine is always on and there is no enable toggle a restyle applies
 * whenever its template box is non blank clearing the box restores the stock line.
 *
 * tabs
 *
 *  layout which backend fields show on which line. field chips per
 *  line click to remove a searchable field palette click to add to the
 *  selected line. saving writes the complete settings object once.
 *  templates shows the real current layout as chips click an
 *  editable field chip to edit how that line looks with a full live preview
 *  of the entire rendered lore below using your held items data when
 *  available otherwise sample values . freeform fields that have no
 *  parseable value pass through unchanged and are marked as such.
 *  blacklist items by id the engine leaves untouched.
 *
 */
public class LoreConfigScreen extends Screen {
    private static final int PAD = 10;
    private static final int ROW_H = 22;

    private static final int CHIP_TINT = 0xFF2C323B;
    private static final int CHIP_HOVER = 0xFF3C4450;
    private static final int CHIP_PASSTHROUGH = 0xFF23272E;
    private static final int SELECTED = 0xFF8AD62F;

    private static final LoreData SAMPLE = buildSample();

    private enum Tab { LAYOUT, TEMPLATES, BLACKLIST }

    private final Screen parent;

    private List<List<String>> workingLayout;
    private List<LoreModule> workingModules;
    private List<String> workingBlacklist;

    private Tab tab = Tab.LAYOUT;
    private int selectedLine = 0;
    private int paletteScroll = 0;
    private int previewScroll = 0;
    private boolean capturedFromBackend = false;
    // set once the user edits the layout so a late backend echo cant clobber
    // their in progress edits the open time fetch can land mid edit .
    private boolean userEditedLayout = false;
    // same idea for the styling templates so a backend read that lands after the
    // user has restyled a field does not re copy the synced modules over their edit.
    private boolean userEditedTemplates = false;
    // set on any real edit so closing with no changes does not fire a full
    // cofl lore write plus a verifier and a chat line every time the screen closes.
    private boolean dirty = false;

    // templates tab which module is being edited 1 none show hint .
    private int editingModule = -1;

    // a deferred tooltip queued during draw painted at the end so its on top.
    private List<Component> hoverTooltip = null;
    private int hoverTipX, hoverTipY;

    // the single template editor templates tab and palette search box layout .
    private EditBox templateBox = null;
    private EditBox searchBox = null;

    // geometry recomputed in init .
    private int panelX, panelY, panelW, panelH;
    private int contentX, contentY, contentW, contentH;
    private int tabLayoutX, tabTemplatesX, tabBlacklistX, tabY, tabW, tabH;
    private int saveX, saveY, saveW, saveH;
    private int addHeldX, addHeldY, addHeldW, addHeldH;

    private int paletteX, paletteY, paletteW, paletteH;
    private int linesX, linesW;

    public LoreConfigScreen(Screen parent) {
        super(Component.literal("SkyCofl Lore"));
        this.parent = parent;
        this.workingLayout = deepCopyLayout(LoreManager.getLayout());
        this.workingModules = copyModules(LoreManager.getModules());
        this.workingBlacklist = new ArrayList<>(LoreManager.getItemBlacklist());
    }

    public static void open(Screen parent) {
        LoreConfigScreen screen = new LoreConfigScreen(parent);
        Minecraft.getInstance().gui.setScreen(screen);
        screen.requestRealLayout();
    }

    /** the currently open screen so the chat handler can refresh it. */
    private static LoreConfigScreen current = null;

    /** requests the whole lore settings object from the backend cofl lore json . */
    private void requestRealLayout() {
        current = this;
        // if we already synced backend data this session the local mirror is current so
        // drop the loading header immediately a fresh read that gets skipped as stale
        // right after a save would otherwise leave it stuck even though the layout is
        // already correct.
        if (com.coflnet.lore.LoreSync.hasReceived()) {
            capturedFromBackend = true;
        }
        com.coflnet.lore.LoreSync.requestFromBackend();
    }

    /**
     * called when a backend settings read has landed. refreshes the working copies
     * so the user edits from true state unless they have already started editing
     * (the open time fetch can land mid edit). called even for an empty layout so
     * the loading header never sticks on a stock account.
     */
    public static void onLayoutCaptured(List<List<String>> layout) {
        if (current == null) {
            return;
        }
        Minecraft.getInstance().execute(() -> {
            if (current == null) {
                return;
            }
            // the read has landed drop the loading header even if the layout came back
            // empty so it can never stick on a stock account.
            current.capturedFromBackend = true;
            // re copy the freshly synced styling templates unless the user is already
            // editing them so a reset or restyle done on another instance shows up here
            // instead of the stale open time copy being saved back over it. skip while
            // a field is open in the editor so we never yank text out from under them.
            if (!current.userEditedTemplates && current.editingModule < 0) {
                current.workingModules = copyModules(LoreManager.getModules());
            }
            if (layout == null || current.userEditedLayout) {
                return;
            }
            current.workingLayout = deepCopyLayout(layout);
            if (current.selectedLine >= current.workingLayout.size()) {
                current.selectedLine = Math.max(0, current.workingLayout.size() - 1);
            }
        });
    }

    @Override
    protected void init() {
        panelW = Math.min(560, Math.max(1, this.width - 8));
        panelH = Math.min(320, Math.max(1, this.height - 8));
        panelX = this.width / 2 - panelW / 2;
        panelY = this.height / 2 - panelH / 2;

        tabW = Math.max(1, (panelW - PAD * 2 - 8) / 3);
        tabH = 16;
        tabY = panelY + 26;
        tabLayoutX = panelX + PAD;
        tabTemplatesX = tabLayoutX + tabW + 4;
        tabBlacklistX = tabTemplatesX + tabW + 4;

        contentX = panelX + PAD;
        contentY = tabY + tabH + 10;
        contentW = Math.max(1, panelW - PAD * 2);

        saveW = Math.min(110, Math.max(1, (contentW - 4) / 2));
        saveH = 18;
        saveX = panelX + panelW - PAD - saveW;
        saveY = panelY + panelH - PAD - saveH;
        contentH = Math.max(1, saveY - 8 - contentY);

        addHeldW = Math.min(150, Math.max(1, contentW - saveW - 4));
        addHeldH = 18;
        addHeldX = panelX + PAD;
        addHeldY = saveY;

        linesX = contentX;
        paletteW = Math.min(Math.max(1, contentW - 1),
                Math.min(170, Math.max(60, (contentW - 8) / 2)));
        linesW = Math.max(1, contentW - paletteW - 8);
        paletteX = contentX + contentW - paletteW;
        paletteY = contentY;
        paletteH = contentH;

        rebuildTabWidgets();
    }

    private void rebuildTabWidgets() {
        this.clearWidgets();
        templateBox = null;
        searchBox = null;
        Font font = Minecraft.getInstance().font;

        if (tab == Tab.LAYOUT) {
            // search box at the top of the palette.
            searchBox = new EditBox(font, paletteX + 4, paletteY + 16, paletteW - 8, 12,
                    Component.literal("search"));
            searchBox.setMaxLength(40);
            searchBox.setHint(Component.literal("search fields\u2026"));
            addRenderableWidget(searchBox);
        } else if (tab == Tab.TEMPLATES && editingModule >= 0 && editingModule < workingModules.size()) {
            // editor lives in the right column at a fixed position independent of
            // how many chips are on the left so the editbox never jumps.
            LoreModule m = workingModules.get(editingModule);
            int editorX = templateEditorX();
            int editorW = templateEditorWidth();
            templateBox = new EditBox(font, editorX, contentY + 16, editorW, 14,
                    Component.literal("template"));
            templateBox.setMaxLength(200);
            templateBox.setValue(m.template == null ? "" : m.template);
            // mirror keystrokes into the working module so the live preview drawn
            // every frame updates as the user types.
            templateBox.setResponder(v -> {
                if (editingModule >= 0 && editingModule < workingModules.size()) {
                    workingModules.get(editingModule).template = v;
                    dirty = true;
                    userEditedTemplates = true;
                }
            });
            addRenderableWidget(templateBox);
        }
    }

    /** pull the editor value back into the working module before save tab switch. */
    private void captureWidgets() {
        if (tab == Tab.TEMPLATES && templateBox != null
                && editingModule >= 0 && editingModule < workingModules.size()) {
            workingModules.get(editingModule).template = templateBox.getValue();
        }
    }

    private void switchTab(Tab next) {
        captureWidgets();
        tab = next;
        paletteScroll = 0;
        previewScroll = 0;
        editingModule = -1;
        rebuildTabWidgets();
    }

    //  rendering

    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor context) {
        RenderUtils.drawRect(context, 0, 0, this.width, this.height, 0xC0101018);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        hoverTooltip = null;

        box(context, panelX, panelY, panelW, panelH, CoflColConfig.BACKGROUND_PRIMARY);
        RenderUtils.drawString(context, "§lSkyCofl Lore", panelX + PAD, panelY + PAD, CoflColConfig.TEXT_PRIMARY);
        RenderUtils.drawString(context, "§8Always on \u2022 hover anything for help",
                panelX + PAD + font.width("§lSkyCofl Lore ") + 6, panelY + PAD, CoflColConfig.TEXT_PRIMARY);

        drawTab(context, tabLayoutX, "Layout", tab == Tab.LAYOUT, mouseX, mouseY,
                "Choose which Coflnet fields appear on item lore and on which line.",
                "Saving writes the complete layout to the server once.");
        drawTab(context, tabTemplatesX, "Templates", tab == Tab.TEMPLATES, mouseX, mouseY,
                "Restyle how each line looks. Click a field to edit it;",
                "see the whole lore preview update live below.");
        drawTab(context, tabBlacklistX, "Blacklist", tab == Tab.BLACKLIST, mouseX, mouseY,
                "Items the lore engine leaves completely untouched.",
                "Add the item you are holding with the button below.");

        switch (tab) {
            case LAYOUT -> drawLayoutTab(context, font, mouseX, mouseY);
            case TEMPLATES -> drawTemplatesTab(context, font, mouseX, mouseY);
            case BLACKLIST -> drawBlacklistTab(context, font, mouseX, mouseY);
        }

        boolean saveHover = capturedFromBackend && inRect(mouseX, mouseY, saveX, saveY, saveW, saveH);
        int saveColor = capturedFromBackend
                ? (saveHover ? CoflColConfig.CONFIRM_HOVER : CoflColConfig.CONFIRM)
                : 0xFF555B63;
        box(context, saveX, saveY, saveW, saveH, saveColor);
        RenderUtils.drawCenteredString(context, capturedFromBackend ? "Save & Close" : "Waiting for server",
                saveX + saveW / 2, saveY + 5, 0xFF222831);
        if (saveHover) {
            queueTip(mouseX, mouseY, "Apply all changes and close.",
                    "§7Layout edits are sent to the server.");
        }

        if (hoverTooltip != null) {
            context.setComponentTooltipForNextFrame(font, hoverTooltip, hoverTipX, hoverTipY);
        }
    }

    private void drawTab(GuiGraphicsExtractor context, int x, String label, boolean active,
                         int mouseX, int mouseY, String... tip) {
        boolean hover = inRect(mouseX, mouseY, x, tabY, tabW, tabH);
        int col = active ? CoflColConfig.BACKGROUND_SECONDARY : (hover ? CHIP_HOVER : CoflColConfig.BACKGROUND_PRIMARY);
        box(context, x, tabY, tabW, tabH, col);
        RenderUtils.drawCenteredString(context, active ? "§f" + label : "§7" + label,
                x + tabW / 2, tabY + 4, CoflColConfig.TEXT_PRIMARY);
        if (active) {
            RenderUtils.drawRect(context, x, tabY + tabH - 1, tabW, 1, SELECTED);
        }
        if (hover) {
            queueTip(mouseX, mouseY, tip);
        }
    }

    //  layout tab

    private void drawLayoutTab(GuiGraphicsExtractor context, Font font, int mouseX, int mouseY) {
        String header = capturedFromBackend
                ? "§7Lines \u2014 click a chip to remove, click a row to select it"
                : "§e\u231b Loading real layout from server\u2026 §7(edits sync on Save)";
        RenderUtils.drawString(context, header, linesX, contentY - 9, CoflColConfig.TEXT_PRIMARY);

        int y = contentY + 6;
        int lineCount = Math.max(workingLayout.size() + 1, 1);
        for (int line = 0; line < lineCount; line++) {
            boolean isSel = line == selectedLine;
            int rowY = y + line * ROW_H;
            if (rowY + ROW_H > contentY + contentH) {
                break;
            }
            boolean rowHover = inRect(mouseX, mouseY, linesX, rowY, linesW, ROW_H - 2);
            box(context, linesX, rowY, linesW, ROW_H - 2,
                    isSel ? CoflColConfig.BACKGROUND_SECONDARY : (rowHover ? CHIP_TINT : CoflColConfig.BACKGROUND_PRIMARY));
            if (isSel) {
                RenderUtils.drawRect(context, linesX, rowY, 2, ROW_H - 2, SELECTED);
            }
            RenderUtils.drawString(context, "§7" + (line + 1), linesX + 4, rowY + 6, CoflColConfig.TEXT_PRIMARY);

            int chipX = linesX + 16;
            List<String> fields = line < workingLayout.size() ? workingLayout.get(line) : List.of();
            for (String key : fields) {
                String label = LoreField.displayOf(key);
                int w = font.width(label) + 12;
                if (chipX + w > linesX + linesW - 4) {
                    break;
                }
                boolean chipHover = inRect(mouseX, mouseY, chipX, rowY + 3, w, ROW_H - 8);
                box(context, chipX, rowY + 3, w, ROW_H - 8, chipHover ? CoflColConfig.CANCEL_HOVER : CHIP_TINT);
                RenderUtils.drawString(context, label, chipX + 5, rowY + 6, CoflColConfig.TEXT_PRIMARY);
                if (chipHover) {
                    LoreField f = LoreField.byKey(key);
                    queueTip(mouseX, mouseY, "§c[click to remove] §f" + label,
                            f != null ? "§7" + f.description : "§7" + key);
                }
                chipX += w + 3;
            }
            if (fields.isEmpty()) {
                RenderUtils.drawString(context, "§8(empty \u2014 add fields from the right)", chipX, rowY + 6, CoflColConfig.TEXT_PRIMARY);
            }
        }

        // palette with search.
        box(context, paletteX, paletteY, paletteW, paletteH, CoflColConfig.BACKGROUND_SECONDARY);
        RenderUtils.drawString(context, "§fAdd to line " + (selectedLine + 1), paletteX + 5, paletteY + 4, CoflColConfig.TEXT_PRIMARY);
        //  search editbox widget sits at palettey 16
        int listTop = paletteY + 32;
        context.enableScissor(paletteX, listTop, paletteX + paletteW, paletteY + paletteH);
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        int py = listTop + 2 - paletteScroll;
        for (LoreField f : LoreField.ALL) {
            if (!matchesQuery(f, query)) {
                continue;
            }
            if (py + 14 >= listTop && py <= paletteY + paletteH) {
                boolean fHover = inRect(mouseX, mouseY, paletteX + 3, py, paletteW - 6, 13)
                        && mouseY >= listTop && mouseY <= paletteY + paletteH;
                box(context, paletteX + 3, py, paletteW - 6, 13, fHover ? CoflColConfig.CONFIRM_HOVER : CHIP_TINT);
                RenderUtils.drawString(context, (fHover ? "§f" : "§7") + f.display, paletteX + 6, py + 3,
                        CoflColConfig.TEXT_PRIMARY);
                if (fHover) {
                    queueTip(mouseX, mouseY, "§a[click to add] §f" + f.display, "§7" + f.description);
                }
            }
            py += 15;
        }
        context.disableScissor();
    }

    //  templates tab

    private void drawTemplatesTab(GuiGraphicsExtractor context, Font font, int mouseX, int mouseY) {
        RenderUtils.drawString(context, "§7Pick a field on the left, edit it on the right. §8Only fields this item shows are listed.",
                contentX, contentY - 9, CoflColConfig.TEXT_PRIMARY);

        // left column one row per segment actually detected in this items output.
        int colW = templateListWidth();
        List<com.coflnet.lore.LoreSegment> detected = detectedSegments();
        int rowH = 14;
        int listTop = contentY + 4;
        for (int i = 0; i < detected.size(); i++) {
            com.coflnet.lore.LoreSegment seg = detected.get(i);
            int rowY = listTop + i * (rowH + 2);
            if (rowY + rowH > contentY + contentH) {
                break;
            }
            int modIdx = moduleIndexForKey(seg.key);
            boolean isSel = modIdx >= 0 && modIdx == editingModule;
            boolean h = inRect(mouseX, mouseY, contentX, rowY, colW, rowH);
            int col = isSel ? CoflColConfig.BACKGROUND_SECONDARY : (h ? CoflColConfig.CONFIRM_HOVER : CHIP_TINT);
            box(context, contentX, rowY, colW, rowH, col);
            if (isSel) {
                RenderUtils.drawRect(context, contentX, rowY, 2, rowH, SELECTED);
            }
            // dim the field if its template is currently empty hidden .
            LoreModule m = modIdx >= 0 ? workingModules.get(modIdx) : null;
            boolean hidden = m == null || m.template == null || m.template.isBlank();
            RenderUtils.drawString(context, (hidden ? "§8" : "§f") + seg.display, contentX + 5, rowY + 3, CoflColConfig.TEXT_PRIMARY);
            if (h) {
                queueTip(mouseX, mouseY, "§a[click to edit] §f" + seg.display,
                        hidden ? "§8Currently showing the stock backend look (empty template)." : "§7Restyle just this field.");
            }
        }
        if (detected.isEmpty()) {
            RenderUtils.drawString(context, "§8(hold a Cofl item)", contentX + 4, listTop + 4, CoflColConfig.TEXT_PRIMARY);
        }

        // divider between columns.
        RenderUtils.drawRect(context, contentX + colW + 4, contentY, 1, contentH, CHIP_HOVER);

        // right column editor for the selected field.
        int editorX = templateEditorX();
        int editorW = templateEditorWidth();
        if (editingModule >= 0 && editingModule < workingModules.size()) {
            LoreModule m = workingModules.get(editingModule);
            RenderUtils.drawString(context, "§fEditing §e" + m.name + "  §8" + tokensFor(m),
                    editorX, contentY + 2, CoflColConfig.TEXT_PRIMARY);
            //  template editbox widget sits at contenty 16
            String tmpl = templateBox != null ? templateBox.getValue() : m.template;
            RenderUtils.drawString(context, "§8this field: §r" + onePreview(tmpl),
                    editorX, contentY + 34, CoflColConfig.TEXT_PRIMARY);
            // reset clear buttons.
            int resetW = resetButtonWidth();
            int stockX = stockButtonX();
            int stockW = stockButtonWidth();
            boolean rHover = inRect(mouseX, mouseY, editorX, contentY + 46, resetW, 14);
            box(context, editorX, contentY + 46, resetW, 14, rHover ? CoflColConfig.CONFIRM_HOVER : CHIP_TINT);
            RenderUtils.drawCenteredString(context, "Reset", editorX + resetW / 2, contentY + 49, CoflColConfig.TEXT_PRIMARY);
            boolean cHover = inRect(mouseX, mouseY, stockX, contentY + 46, stockW, 14);
            box(context, stockX, contentY + 46, stockW, 14, cHover ? CoflColConfig.CANCEL_HOVER : CHIP_TINT);
            RenderUtils.drawCenteredString(context, "Show stock", stockX + stockW / 2, contentY + 49, CoflColConfig.TEXT_PRIMARY);
            if (rHover) {
                queueTip(mouseX, mouseY, "§a[reset] §7Restore this field's default style.");
            }
            if (cHover) {
                queueTip(mouseX, mouseY, "§c[show stock] §7Empty the template so this field falls back to the stock backend look.");
            }
        } else {
            RenderUtils.drawString(context, "§8Select a field on the left to edit it.",
                    editorX, contentY + 2, CoflColConfig.TEXT_PRIMARY);
        }

        // full live preview of the whole lore under the editor right column .
        int prevTop = contentY + 66;
        RenderUtils.drawString(context, "§7Live preview §8(" + (hasHeldData() ? "your held item" : "sample data") + ")",
                editorX, prevTop, CoflColConfig.TEXT_PRIMARY);
        int boxTop = prevTop + 11;
        int boxH = contentY + contentH - boxTop;
        if (boxH > 10) {
            box(context, editorX, boxTop, editorW, boxH, 0xFF15181D);
            context.enableScissor(editorX, boxTop, editorX + editorW, boxTop + boxH);
            List<String> preview = buildPreviewLines();
            int ly = boxTop + 3 - previewScroll;
            for (String line : preview) {
                if (ly + 10 >= boxTop && ly <= boxTop + boxH) {
                    RenderUtils.drawString(context, trunc(font, line, editorW - 8), editorX + 4, ly, CoflColConfig.TEXT_PRIMARY);
                }
                ly += 10;
            }
            context.disableScissor();
        }
    }

    //  blacklist tab

    private void drawBlacklistTab(GuiGraphicsExtractor context, Font font, int mouseX, int mouseY) {
        RenderUtils.drawString(context, "§7Items the engine never touches. Hold an item and click the button below.",
                contentX, contentY - 9, CoflColConfig.TEXT_PRIMARY);

        if (workingBlacklist.isEmpty()) {
            RenderUtils.drawString(context, "§8(no blacklisted items yet)", contentX + 4, contentY + 8, CoflColConfig.TEXT_PRIMARY);
        }
        int y = contentY + 6;
        for (int i = 0; i < workingBlacklist.size(); i++) {
            int rowY = y + i * 18;
            if (rowY + 16 > contentY + contentH) {
                break;
            }
            box(context, contentX, rowY, contentW, 16, CoflColConfig.BACKGROUND_SECONDARY);
            RenderUtils.drawString(context, "§f" + workingBlacklist.get(i), contentX + 6, rowY + 4, CoflColConfig.TEXT_PRIMARY);
            int rx = contentX + contentW - 18;
            boolean rHover = inRect(mouseX, mouseY, rx, rowY + 1, 14, 14);
            box(context, rx, rowY + 1, 14, 14, rHover ? CoflColConfig.CANCEL_HOVER : CoflColConfig.CANCEL);
            RenderUtils.drawCenteredString(context, "\u00D7", rx + 7, rowY + 4, CoflColConfig.TEXT_PRIMARY);
            if (rHover) {
                queueTip(mouseX, mouseY, "§c[click to remove]", "§7Stop blacklisting this item.");
            }
        }

        ItemStack held = heldItem();
        boolean hasHeld = held != null && !held.isEmpty() && held.getItem() != Items.AIR;
        boolean addHover = inRect(mouseX, mouseY, addHeldX, addHeldY, addHeldW, addHeldH);
        box(context, addHeldX, addHeldY, addHeldW, addHeldH,
                hasHeld ? (addHover ? CoflColConfig.CONFIRM_HOVER : CoflColConfig.BACKGROUND_SECONDARY) : 0xFF555B63);
        String heldName = hasHeld ? net.minecraft.ChatFormatting.stripFormatting(held.getHoverName().getString()) : "nothing held";
        RenderUtils.drawCenteredString(context, "+ Blacklist held: " + trunc(font, heldName, addHeldW - 16),
                addHeldX + addHeldW / 2, addHeldY + 5, CoflColConfig.TEXT_PRIMARY);
        if (addHover && hasHeld) {
            queueTip(mouseX, mouseY, "§a[click to add] §f" + heldName, "§7The engine will skip this item.");
        }
    }

    //  input

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x();
        double my = click.y();

        if (inRect(mx, my, tabLayoutX, tabY, tabW, tabH)) { switchTab(Tab.LAYOUT); return true; }
        if (inRect(mx, my, tabTemplatesX, tabY, tabW, tabH)) { switchTab(Tab.TEMPLATES); return true; }
        if (inRect(mx, my, tabBlacklistX, tabY, tabW, tabH)) { switchTab(Tab.BLACKLIST); return true; }
        if (!capturedFromBackend) {
            return true;
        }
        if (inRect(mx, my, saveX, saveY, saveW, saveH)) {
            if (dirty) {
                saveAll();
            }
            current = null;
            Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }

        boolean handled = switch (tab) {
            case LAYOUT -> clickLayout(mx, my);
            case TEMPLATES -> clickTemplates(mx, my);
            case BLACKLIST -> clickBlacklist(mx, my);
        };
        if (handled) {
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private boolean clickLayout(double mx, double my) {
        int listTop = paletteY + 32;
        if (inRect(mx, my, paletteX, listTop, paletteW, paletteH - (listTop - paletteY))) {
            String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(java.util.Locale.ROOT);
            int py = listTop + 2 - paletteScroll;
            for (LoreField f : LoreField.ALL) {
                if (!matchesQuery(f, query)) {
                    continue;
                }
                if (inRect(mx, my, paletteX + 3, py, paletteW - 6, 13)) {
                    if (!containsField(f.key)) {
                        ensureLine(selectedLine).add(f.key);
                        userEditedLayout = true;
                        dirty = true;
                    }
                    return true;
                }
                py += 15;
            }
            return true;
        }
        int y = contentY + 6;
        int lineCount = Math.max(workingLayout.size() + 1, 1);
        Font font = Minecraft.getInstance().font;
        for (int line = 0; line < lineCount; line++) {
            int rowY = y + line * ROW_H;
            if (rowY + ROW_H > contentY + contentH) {
                break;   // match the draw clamp never hit a row scrolled off the panel.
            }
            if (!inRect(mx, my, linesX, rowY, linesW, ROW_H - 2)) {
                continue;
            }
            int chipX = linesX + 16;
            List<String> fields = line < workingLayout.size() ? workingLayout.get(line) : List.of();
            for (int fi = 0; fi < fields.size(); fi++) {
                String label = LoreField.displayOf(fields.get(fi));
                int w = font.width(label) + 12;
                if (chipX + w > linesX + linesW - 4) {
                    break;   // match the draw clamp chips past the row edge are not shown.
                }
                if (inRect(mx, my, chipX, rowY + 3, w, ROW_H - 8)) {
                    workingLayout.get(line).remove(fi);
                    userEditedLayout = true;
                    dirty = true;
                    return true;
                }
                chipX += w + 3;
            }
            selectedLine = line;
            return true;
        }
        return false;
    }

    private boolean clickTemplates(double mx, double my) {
        int editorX = templateEditorX();
        // reset clear buttons only when editing positions match the draw.
        if (editingModule >= 0 && editingModule < workingModules.size()) {
            if (inRect(mx, my, editorX, contentY + 46, resetButtonWidth(), 14)) {
                String def = defaultTemplateFor(workingModules.get(editingModule));
                workingModules.get(editingModule).template = def;
                dirty = true;
                userEditedTemplates = true;
                if (templateBox != null) {
                    templateBox.setValue(def == null ? "" : def);
                }
                return true;
            }
            if (inRect(mx, my, stockButtonX(), contentY + 46, stockButtonWidth(), 14)) {
                workingModules.get(editingModule).template = "";
                dirty = true;
                userEditedTemplates = true;
                if (templateBox != null) {
                    templateBox.setValue("");
                }
                return true;
            }
        }
        // left column list hit testing one unambiguous row per detected segment.
        int colW = templateListWidth();
        int rowH = 14;
        int listTop = contentY + 4;
        List<com.coflnet.lore.LoreSegment> detected = detectedSegments();
        for (int i = 0; i < detected.size(); i++) {
            int rowY = listTop + i * (rowH + 2);
            if (rowY + rowH > contentY + contentH) {
                break;   // match the draw clamp never select a row scrolled off the panel.
            }
            if (inRect(mx, my, contentX, rowY, colW, rowH)) {
                int modIdx = moduleIndexForKey(detected.get(i).key);
                if (modIdx >= 0) {
                    captureWidgets();
                    editingModule = modIdx;
                    rebuildTabWidgets();
                }
                return true;
            }
        }
        return false;
    }

    private boolean clickBlacklist(double mx, double my) {
        int y = contentY + 6;
        for (int i = 0; i < workingBlacklist.size(); i++) {
            int rowY = y + i * 18;
            if (rowY + 16 > contentY + contentH) {
                break;   // match the draw clamp never remove a row scrolled off the panel.
            }
            int rx = contentX + contentW - 18;
            if (inRect(mx, my, rx, rowY + 1, 14, 14)) {
                workingBlacklist.remove(i);
                dirty = true;
                return true;
            }
        }
        if (inRect(mx, my, addHeldX, addHeldY, addHeldW, addHeldH)) {
            ItemStack held = heldItem();
            if (held != null && !held.isEmpty() && held.getItem() != Items.AIR) {
                // key on the item type tag e.g. hyperion not the per instance uuid so
                // the engine hides the lore on every copy not just this one stack.
                String id = com.coflnet.CoflModClient.getItemTagFromStack(held);
                if (id != null && !workingBlacklist.contains(id)) {
                    workingBlacklist.add(id);
                    dirty = true;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.LAYOUT && inRect(mouseX, mouseY, paletteX, paletteY, paletteW, paletteH)) {
            int max = Math.max(0, LoreField.ALL.size() * 15);
            paletteScroll = Math.max(0, Math.min(max, paletteScroll + (int) (-scrollY * 18)));
            return true;
        }
        if (tab == Tab.TEMPLATES && inRect(mouseX, mouseY, contentX, contentY + 77, contentW, Math.max(0, contentH - 77))) {
            int max = Math.max(0, buildPreviewLines().size() * 10);
            previewScroll = Math.max(0, Math.min(max, previewScroll + (int) (-scrollY * 12)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    //  save

    private void saveAll() {
        captureWidgets();
        List<List<String>> cleaned = new ArrayList<>();
        for (List<String> line : workingLayout) {
            List<String> l = new ArrayList<>();
            for (String f : line) {
                if (f != null && !f.isBlank()) {
                    l.add(f.trim());
                }
            }
            cleaned.add(l);
        }
        while (!cleaned.isEmpty() && cleaned.get(cleaned.size() - 1).isEmpty()) {
            cleaned.remove(cleaned.size() - 1);
        }
        // persist the edited modules and blacklist first so applylayout which reads
        // getmodules to build the backend customformat write sees the fresh templates
        // not the stale persisted ones otherwise a styling edit ships old and a later
        // read reverts it.
        LoreManager.saveModules(workingModules);
        LoreManager.saveItemBlacklist(workingBlacklist);
        LoreManager.applyLayout(cleaned);
    }

    private List<String> ensureLine(int line) {
        while (workingLayout.size() <= line) {
            workingLayout.add(new ArrayList<>());
        }
        return workingLayout.get(line);
    }

    private boolean containsField(String key) {
        for (List<String> line : workingLayout) {
            if (line == null) {
                continue;
            }
            for (String field : line) {
                if (field != null && field.equalsIgnoreCase(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    //  preview engine mirrors loreengine over the unsaved working modules

    /**
     * builds the full lore preview lines using the same per segment substitution
     * the real engine does but against the unsaved working modules so edits show
     * live. uses the held items real backend strings when available otherwise a
     * synthetic sample set so the preview is still meaningful.
     */
    private List<String> buildPreviewLines() {
        List<String> appends = heldAppendValues();
        boolean real = appends != null;
        if (!real) {
            appends = SAMPLE_LINES;
        }
        LoreData data = real ? parseHeld(appends) : SAMPLE;

        // segment key non blank template
        Map<String, String> templates = new HashMap<>();
        for (LoreModule m : workingModules) {
            if (m == null || m.match == null || m.match.isBlank()
                    || m.template == null || m.template.isBlank()) {
                continue;
            }
            // mirror the real engine a module still at its stock default is left
            // untouched so the preview shows exactly what the engine will render.
            com.coflnet.lore.LoreSegment seg = com.coflnet.lore.LoreSegment.byKey(m.match);
            if (seg != null && seg.defaultTemplate.equals(m.template)) {
                continue;
            }
            templates.put(m.match.toUpperCase(java.util.Locale.ROOT), m.template);
        }

        List<String> out = new ArrayList<>();
        for (String backendLine : appends) {
            out.add(com.coflnet.lore.LoreEngine.renderLine(backendLine, templates, data));
        }
        if (out.isEmpty()) {
            out.add("§8(nothing to show \u2014 add fields in the Layout tab)");
        }
        return out;
    }

    /** single line preview for the editor render the fields template once. */
    private String onePreview(String template) {
        if (template == null || template.isBlank()) {
            return "§8(stock backend look \u2014 empty template)";
        }
        List<String> appends = heldAppendValues();
        LoreData data = appends != null ? parseHeld(appends) : SAMPLE;
        String rendered = LoreTemplate.render(template, data);
        return rendered == null ? "§8(no data for this item)" : rendered;
    }

    //  held item data

    private boolean hasHeldData() {
        return heldAppendValues() != null;
    }

    /** the held items backend append strings or null when none are cached. */
    private List<String> heldAppendValues() {
        ItemStack held = heldItem();
        if (held == null || held.isEmpty()) {
            return null;
        }
        String id = com.coflnet.CoflModClient.getIdFromStack(held);
        if (id == null) {
            return null;
        }
        DescriptionHandler.DescModification[] tips = DescriptionHandler.getTooltipData(id);
        if (tips == null) {
            return null;
        }
        List<String> appends = new ArrayList<>();
        for (DescriptionHandler.DescModification tip : tips) {
            if ("APPEND".equals(tip.type) && tip.value != null) {
                appends.add(tip.value);
            }
        }
        return appends.isEmpty() ? null : appends;
    }

    private static LoreData parseHeld(List<String> appends) {
        List<String> stripped = new ArrayList<>(appends.size());
        for (String v : appends) {
            stripped.add(net.minecraft.ChatFormatting.stripFormatting(v));
        }
        return LoreParser.parse(stripped);
    }

    //  helpers

    private static boolean matchesQuery(LoreField f, String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        return f.display.toLowerCase(java.util.Locale.ROOT).contains(query)
                || f.key.toLowerCase(java.util.Locale.ROOT).contains(query);
    }

    /**
     * the segments to list in the templates tab one per restylable field that is
     * active in the current layout. this is the users rule only fields actually
     * in the layout appear and every one of them is editable each maps to a
     * module via {@link #moduleIndexForKey}).
     *
     * we walk the layout lines in order and for each field key include its
     * {@link com.coflnet.lore.LoreSegment} when one exists (a restylable field).
     * packed sub fields that share a line but are not separate layout keys e.g.
     * {@code clean craft} riding on the {@code FullCraftCost} line) are added
     * alongside their parent so they stay editable too. freeform layout fields
     * with no segment (e.g. {@code ITEM_KEY}) are skipped — they pass through
     * unstyled and have nothing to edit.
     */
    private List<com.coflnet.lore.LoreSegment> detectedSegments() {
        List<com.coflnet.lore.LoreSegment> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (List<String> line : workingLayout) {
            if (line == null) {
                continue;
            }
            for (String key : line) {
                if (key == null) {
                    continue;
                }
                // the layout field itself when it is restylable.
                com.coflnet.lore.LoreSegment direct = com.coflnet.lore.LoreSegment.byKey(key);
                if (direct != null && seen.add(direct.key.toUpperCase(java.util.Locale.ROOT))) {
                    out.add(direct);
                }
                // packed sub fields that live on this layout fields line but are not
                // their own layout key e.g. craft cost clean craft rides on the
                // fullcraftcost line . include them so they stay editable.
                for (String subKey : packedSubFields(key)) {
                    com.coflnet.lore.LoreSegment sub = com.coflnet.lore.LoreSegment.byKey(subKey);
                    if (sub != null && seen.add(sub.key.toUpperCase(java.util.Locale.ROOT))) {
                        out.add(sub);
                    }
                }
            }
        }
        return out;
    }

    /**
     * sub field segment keys the backend packs onto the same line as the given
     * layout field key but which are not themselves placeable layout keys. keeps
     * them editable in the templates tab even though the layout only lists the
     * parent field.
     */
    private static List<String> packedSubFields(String layoutKey) {
        if (layoutKey == null) {
            return List.of();
        }
        switch (layoutKey.toUpperCase(java.util.Locale.ROOT)) {
            // the craft line shows both clean craft craft cost and full craft
            // cost fullcraftcost either may be the layout key so surface both.
            case "FULLCRAFTCOST":
                return List.of("CRAFT_COST");
            case "CRAFT_COST":
                return List.of("FullCraftCost");
            default:
                return List.of();
        }
    }

    /** index of the module that restyles a layout field key or 1 if freeform. */
    private int moduleIndexForKey(String key) {
        for (int i = 0; i < workingModules.size(); i++) {
            LoreModule m = workingModules.get(i);
            if (m.match != null && m.match.equalsIgnoreCase(key)) {
                return i;
            }
        }
        return -1;
    }

    private static String tokensFor(LoreModule m) {
        if (m.match == null) {
            return "";
        }
        com.coflnet.lore.LoreSegment seg = com.coflnet.lore.LoreSegment.byKey(m.match);
        return seg == null ? "" : seg.tokens;
    }

    private static String defaultTemplateFor(LoreModule m) {
        if (m.match != null) {
            com.coflnet.lore.LoreSegment seg = com.coflnet.lore.LoreSegment.byKey(m.match);
            if (seg != null) {
                return seg.defaultTemplate;
            }
        }
        return m.template;
    }

    private int templateListWidth() {
        return Math.min(130, Math.max(1, (contentW - 8) / 2));
    }

    private int templateEditorX() {
        return Math.min(contentX + contentW - 1, contentX + templateListWidth() + 8);
    }

    private int templateEditorWidth() {
        return Math.max(1, contentX + contentW - templateEditorX());
    }

    private int resetButtonWidth() {
        return Math.min(60, Math.max(1, (templateEditorWidth() - 6) / 2));
    }

    private int stockButtonX() {
        return templateEditorX() + resetButtonWidth() + Math.min(6, Math.max(0, templateEditorWidth() - 2));
    }

    private int stockButtonWidth() {
        return Math.max(1, contentX + contentW - stockButtonX());
    }

    private void queueTip(int mouseX, int mouseY, String... lines) {
        List<Component> tip = new ArrayList<>();
        for (String s : lines) {
            tip.add(Component.literal(s));
        }
        hoverTooltip = tip;
        hoverTipX = mouseX;
        hoverTipY = mouseY;
    }

    private static ItemStack heldItem() {
        if (Minecraft.getInstance().player == null) {
            return ItemStack.EMPTY;
        }
        return Minecraft.getInstance().player.getMainHandItem();
    }

    private static String trunc(Font font, String text, int maxW) {
        if (font.width(text) <= maxW) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (font.width(sb.toString() + c + "..") > maxW) {
                break;
            }
            sb.append(c);
        }
        return sb + "..";
    }

    private static List<List<String>> deepCopyLayout(List<List<String>> src) {
        List<List<String>> out = new ArrayList<>();
        if (src == null) {
            return out;
        }
        for (List<String> line : src) {
            // tolerate a null line as applylayout and detectedsegments do a backend
            // reply or a hand edited config can carry one and it must not npe.
            out.add(line == null ? new ArrayList<>() : new ArrayList<>(line));
        }
        return out;
    }

    private static List<LoreModule> copyModules(List<LoreModule> src) {
        List<LoreModule> out = new ArrayList<>();
        for (LoreModule m : src) {
            out.add(new LoreModule(m.name, m.match, m.template));
        }
        return out;
    }

    private static boolean inRect(double px, double py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private static void box(GuiGraphicsExtractor context, int x, int y, int w, int h, int color) {
        RenderUtils.drawRect(context, x, y, w, h, color);
    }

    /**
     * synthetic backend lines used for the preview when no held item with coflnet
     * data is available. shaped exactly like real backend appends combined fields
     * per line plus a freeform line so the segment engine exercises the same way.
     */
    private static final List<String> SAMPLE_LINES = List.of(
            "§7lbin: §e690,000 §8(sample)",
            "§7Med: §b550,000 §7Vol: §e37 ",
            "§7Buy: §6743,740 §7Sell: §6700,000",
            "§7clean craft: §e320,000 §7Full Craft Cost: §e500,000 ",
            "§7Modifier Cost: §e180,000",
            "§7Paid: §e480,000 §812d ago",
            "§7Obtain cost: §f600,000",
            "§7Item-Key: sample passthrough line"
    );

    private static LoreData buildSample() {
        LoreData d = new LoreData();
        d.lbin = 690000.0;
        d.lbinEach = 86250.0;
        d.median = 550000.0;
        d.medianEach = 49722.0;
        d.volume = 37.0;
        d.craftCost = 500000.0;
        d.cleanCraft = 320000.0;
        d.modifierCost = 180000.0;
        d.obtainCost = 600000.0;
        d.buy = 743740.0;
        d.sell = 700000.0;
        d.buyVol = 2940.0;
        d.sellVol = 1670.0;
        d.purchasedFor = 480000.0;
        return d;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // only write when something actually changed opening and closing or just
        // flipping tabs must not fire a full cofl lore write verifier and chat line.
        if (dirty) {
            saveAll();
        }
        current = null;
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
