package com.coflnet.gui.cofl;

import CoflCore.CoflCore;
import CoflCore.commands.RawCommand;
import CoflCore.network.WSClient;
import com.coflnet.CoflModClient;
import com.coflnet.core.LoreLayout;
import com.coflnet.core.LorePreview;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Server-backed draft editor. Only Save uploads; preview is isolated from the live tooltip cache. */
public class LoreEditorScreen extends Screen {
    private final Screen parent;
    private LoreLayout layout;
    private JsonObject saved;
    private JsonObject pendingSave;
    private String status = "Loading saved lore layout...";
    private boolean started, waiting, previewLoading;
    private long requestTime;
    private int revision, selectedRow, paletteScroll, layoutScroll, previewScroll, itemIndex;
    private final List<ItemStack> items = new ArrayList<>();
    private final NonNullList<ItemStack> inventory = NonNullList.create();
    private List<Component> preview = List.of();
    private Chip dragging;
    private boolean moved;
    private int dragRow = -1;
    private double startX, startY;
    private final List<Chip> chips = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private Button saveButton, previewButton, clearButton, reloadButton, itemButton;
    private int paletteRight, layoutRight;
    private static final int TOP = 50;

    private record Chip(String field, int row, int index, int x, int y, int w) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + 18; }
    }
    private record Row(int index, int y, int h) {}

    public LoreEditorScreen(Screen parent) {
        super(Component.literal("Lore fields & layout"));
        this.parent = parent;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            var held = player.getMainHandItem();
            for (var stack : CoflModClient.inventoryToItemStacks(player.getInventory())) {
                ItemStack copy = stack.copy();
                inventory.add(copy);
                if (!stack.isEmpty()) {
                    if (stack == held) itemIndex = items.size();
                    items.add(copy);
                }
            }
        }
    }

    @Override
    protected void init() {
        paletteRight = Math.min(145, width / 4);
        layoutRight = width - Math.min(250, width / 3);
        int buttonWidth = Math.min(95, (width - 36) / 5);
        reloadButton = button("Reload", 8, height - 25, buttonWidth, this::load);
        clearButton = button("Clear layout", 12 + buttonWidth, height - 25, buttonWidth, () -> {
            layout.rows.clear(); selectedRow = 0; changed();
        });
        previewButton = button("Preview", 16 + buttonWidth * 2, height - 25, buttonWidth, this::preview);
        saveButton = button("Save", 20 + buttonWidth * 3, height - 25, buttonWidth, this::save);
        button("Close", 24 + buttonWidth * 4, height - 25, buttonWidth, this::onClose);
        itemButton = button("Next item", layoutRight + 8, 27, width - layoutRight - 16, () -> {
            itemIndex = (itemIndex + 1) % items.size();
            preview = List.of(); revision++; previewLoading = false;
            status = "Preview uses the selected item; nothing is saved.";
        });
        if (!started) { started = true; load(); }
        updateButtons();
    }

    private Button button(String label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, w, 20).build());
    }

    @Override
    protected void repositionElements() { rebuildWidgets(); }

    private void load() {
        pendingSave = null;
        waiting = true; requestTime = System.currentTimeMillis();
        status = "Loading saved lore layout...";
        updateButtons();
        // LoreCommand also accepts the original unquoted 'json' argument on older servers.
        send("json");
    }

    private void send(String argument) {
        Thread.ofVirtual().name("CoflSky-LoreSync").start(() -> {
            try {
                var wrapper = CoflCore.getWrapper();
                if (!wrapper.isRunning) throw new IllegalStateException("Connect to SkyCofl first");
                wrapper.SendMessage(new RawCommand("lore", argument));
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> {
                    waiting = false; pendingSave = null;
                    status = "Could not sync lore: " + e.getMessage();
                });
            }
        });
    }

    public void receiveSettings(JsonObject settings) {
        if (!waiting) return;
        try {
            LoreLayout received = new LoreLayout(settings);
            if (pendingSave != null && !received.settings().equals(pendingSave)) return;
            boolean wasSave = pendingSave != null;
            saved = settings.deepCopy(); layout = received;
            waiting = false; pendingSave = null;
            selectedRow = 0; layoutScroll = 0;
            revision++; previewLoading = false; preview = List.of();
            status = wasSave ? "Saved to your SkyCofl account." : "Drag fields or click + to add. Drag row numbers to reorder.";
            if (wasSave) CoflModClient.invalidateLoreDescriptions();
            updateButtons();
        } catch (RuntimeException e) {
            waiting = false;
            status = "Invalid lore layout received. Reload to try again.";
        }
    }

    private void save() {
        pendingSave = layout.settings(); waiting = true;
        requestTime = System.currentTimeMillis(); status = "Saving layout...";
        updateButtons();
        send(WSClient.gson.toJson(pendingSave.toString()));
    }

    private void changed() {
        revision++; previewLoading = false; preview = List.of(); previewScroll = 0;
        selectedRow = Math.max(0, Math.min(selectedRow, layout.rows.size()));
        arrange();
        int size = rows.getLast().y + rows.getLast().h + layoutScroll - TOP;
        layoutScroll = Math.min(layoutScroll, Math.max(0, size - (bottom() - TOP)));
        status = "Unsaved changes. Preview to try them; Save to sync.";
        updateButtons();
    }

    private void preview() {
        ItemStack item = items.get(itemIndex);
        // The API requires the actual SkyBlock menu item to recognize a SkyBlock inventory.
        String nbt = CoflModClient.inventoryToNBT(inventory);
        int slot = inventory.indexOf(item);
        String username = Minecraft.getInstance().getUser().getName();
        JsonObject settings = layout.settings();
        int generation = ++revision;
        previewLoading = true; status = "Loading preview...";
        List<Component> original = new ArrayList<>();
        original.add(item.getHoverName());
        var lore = item.get(DataComponents.LORE);
        if (lore != null) original.addAll(lore.lines());
        Thread.ofVirtual().name("CoflSky-LorePreview").start(() -> {
            try {
                var modifications = LorePreview.load(nbt, username, settings, slot);
                for (var modification : modifications) {
                    Component text = Component.literal(modification.value == null ? "" : modification.value);
                    switch (modification.type) {
                        case "APPEND" -> original.add(text);
                        case "INSERT" -> original.add(Math.clamp(modification.line, 0, original.size()), text);
                        case "REPLACE" -> { if (modification.line >= 0 && modification.line < original.size()) original.set(modification.line, text); }
                        case "DELETE" -> { if (modification.line >= 0 && modification.line < original.size()) original.remove(modification.line); }
                        default -> { } // Non-lore instructions (e.g. highlighting) have no preview side effects.
                    }
                }
                Minecraft.getInstance().execute(() -> {
                    if (revision != generation) return;
                    preview = original; previewScroll = 0; previewLoading = false;
                    status = "Temporary preview only. Save to apply the layout.";
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> {
                    if (revision != generation) return;
                    previewLoading = false; status = "Preview failed. Check connection and try again.";
                });
            }
        });
    }

    @Override
    public void tick() {
        if (waiting && System.currentTimeMillis() - requestTime > 10000) {
            status = pendingSave == null ? "No layout received. Check connection, then Reload."
                    : "Save not confirmed. Reload to check the server before retrying.";
            waiting = false; pendingSave = null;
        }
        updateButtons();
    }

    private void updateButtons() {
        boolean editable = layout != null && !waiting;
        clearButton.active = editable;
        saveButton.active = editable && !layout.settings().equals(saved);
        reloadButton.active = !waiting;
        previewButton.active = editable && !previewLoading && !items.isEmpty();
        itemButton.active = !items.isEmpty();
    }

    private int bottom() { return height - 47; }

    /** Chips wrap within their row. Hit targets are recomputed on scroll, resize and edits. */
    private void arrange() {
        chips.clear(); rows.clear();
        if (layout == null) return;
        int y = TOP - layoutScroll;
        for (int r = 0; r <= layout.rows.size(); r++) {
            int start = y, x = paletteRight + 27;
            if (r < layout.rows.size()) {
                var fields = layout.rows.get(r);
                for (int c = 0; c < fields.size(); c++) {
                    int w = Math.min(layoutRight - paletteRight - 38, font.width(label(fields.get(c))) + 23);
                    if (x + w > layoutRight - 6 && x > paletteRight + 27) { x = paletteRight + 27; y += 22; }
                    chips.add(new Chip(fields.get(c), r, c, x, y + 3, w));
                    x += w + 4;
                }
            }
            rows.add(new Row(r, start, y - start + 26));
            y += 30;
        }
    }

    private static String label(String field) {
        return field.replace('_', ' ').replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xF0181C24);
        text(g, "Lore fields & layout", 8, 8, 0xFFFFCC55);
        text(g, "Add fields", 8, 32, 0xFFFFFFFF);
        text(g, "Layout", paletteRight + 8, 32, 0xFFFFFFFF);
        g.fill(paletteRight, 27, paletteRight + 1, bottom(), 0xFF566070);
        g.fill(layoutRight, 27, layoutRight + 1, bottom(), 0xFF566070);
        g.enableScissor(4, TOP, paletteRight - 3, bottom());
        for (int i = 0; i < LoreLayout.FIELDS.size(); i++) {
            int y = TOP + i * 22 - paletteScroll;
            g.fill(6, y, paletteRight - 5, y + 19, 0xFF303948);
            text(g, font.plainSubstrByWidth("+ " + label(LoreLayout.FIELDS.get(i)), paletteRight - 20), 10, y + 5, 0xFFDDDDDD);
        }
        g.disableScissor();
        arrange();
        g.enableScissor(paletteRight + 2, TOP, layoutRight - 2, bottom());
        for (Row row : rows) {
            g.fill(paletteRight + 4, row.y, layoutRight - 4, row.y + row.h,
                    (dragging != null || dragRow >= 0) && mx > paletteRight && mx < layoutRight
                            && my >= row.y && my < row.y + row.h ? 0xFF526840
                            : row.index == selectedRow ? 0xFF394554 : 0xFF252D38);
            text(g, row.index == layout.rows.size() ? "+" : String.valueOf(row.index + 1), paletteRight + 8, row.y + 8, 0xFFFFCC55);
            if (row.index == layout.rows.size()) text(g, "New row", paletteRight + 28, row.y + 8, 0xFFAAAAAA);
        }
        for (Chip chip : chips) {
            g.fill(chip.x, chip.y, chip.x + chip.w, chip.y + 18, chip.contains(mx, my) ? 0xFF526880 : 0xFF3B4D62);
            text(g, font.plainSubstrByWidth(label(chip.field), chip.w - 20), chip.x + 4, chip.y + 5, 0xFFFFFFFF);
            text(g, "x", chip.x + chip.w - 10, chip.y + 5, 0xFFFF8888);
        }
        g.disableScissor();
        int py = TOP - previewScroll;
        g.enableScissor(layoutRight + 4, TOP, width - 4, bottom());
        List<Component> shown = preview.isEmpty() ? List.of(Component.literal(items.isEmpty()
                ? "Join a world with an item in your inventory to preview."
                : "Selected: " + items.get(itemIndex).getHoverName().getString()),
                Component.literal("Click Preview to try this draft without saving.")) : preview;
        for (var line : shown) {
            for (var wrapped : font.split(line, width - layoutRight - 16)) {
                g.text(font, wrapped, layoutRight + 8, py, 0xFFFFFFFF, false); py += 11;
            }
        }
        g.disableScissor();
        text(g, font.plainSubstrByWidth(status, width - 16), 8, height - 39, 0xFFCCDDEE);
        if (dragging != null && moved) {
            g.fill(mx + 3, my - 4, mx + font.width(label(dragging.field)) + 12, my + 13, 0xEE536A83);
            text(g, label(dragging.field), mx + 7, my, 0xFFFFFFFF);
        }
        if (dragging == null && dragRow < 0 && my >= TOP && my < bottom()) {
            String hint = null;
            if (mx < paletteRight) {
                int i = (my - TOP + paletteScroll) / 22;
                if (i >= 0 && i < LoreLayout.FIELDS.size()) {
                    String field = LoreLayout.FIELDS.get(i);
                    hint = label(field) + "\nClick + to add to the selected row, or drag into the layout.";
                    if (field.equals("DefaultLore")) hint += "\nMarks where the original item lore belongs.";
                    if (field.equals("FinderEstimates")) hint += "\nThis field can slow description loading.";
                    if (field.equals("NONE")) hint += "\nEmpty placeholder.";
                }
            } else for (Chip chip : chips) if (chip.contains(mx, my))
                hint = mx >= chip.x + chip.w - 14 ? "Remove " + label(chip.field) : label(chip.field) + "\nDrag to move. Click x to remove.";
            if (hint != null) g.setTooltipForNextFrame(font, font.split(Component.literal(hint), 230),
                    DefaultTooltipPositioner.INSTANCE, mx, my, true);
        }
    }

    private void text(GuiGraphicsExtractor g, String text, int x, int y, int color) { g.text(font, text, x, y, color, false); }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT || click.y() < TOP || click.y() >= bottom() || layout == null || waiting)
            return super.mouseClicked(click, doubleClick);
        arrange();
        moved = false; startX = click.x(); startY = click.y();
        if (click.x() < paletteRight) {
            int i = ((int) click.y() - TOP + paletteScroll) / 22;
            if (i >= 0 && i < LoreLayout.FIELDS.size()) {
                dragging = new Chip(LoreLayout.FIELDS.get(i), -1, i, 0, 0, 0); return true;
            }
        }
        for (Chip chip : chips) if (chip.contains(click.x(), click.y())) {
            selectedRow = chip.row;
            if (click.x() >= chip.x + chip.w - 14) { layout.remove(chip.row, chip.index); changed(); }
            else dragging = chip;
            return true;
        }
        if (click.x() > paletteRight && click.x() < layoutRight) {
            for (Row row : rows) if (click.y() >= row.y && click.y() < row.y + row.h) {
                selectedRow = row.index;
                if (click.x() < paletteRight + 25 && row.index < layout.rows.size()) dragRow = row.index;
                return true;
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        if (dragging != null || dragRow >= 0) {
            moved |= Math.abs(click.x() - startX) + Math.abs(click.y() - startY) > 4;
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT || (dragging == null && dragRow < 0)) return super.mouseReleased(click);
        arrange();
        if (!moved && dragging != null && dragging.row == -1) {
            int index = selectedRow == layout.rows.size() ? 0 : layout.rows.get(selectedRow).size();
            layout.add(dragging.field, selectedRow, index); changed();
        } else if (moved && click.x() > paletteRight && click.x() < layoutRight && click.y() >= TOP && click.y() < bottom()) {
            for (Row row : rows) if (click.y() >= row.y && click.y() < row.y + row.h + 4) {
                selectedRow = row.index;
                if (dragRow >= 0) {
                    var fields = layout.rows.remove(dragRow);
                    selectedRow = row.index > dragRow ? row.index - 1 : row.index;
                    layout.rows.add(selectedRow, fields);
                } else {
                    int index = row.index == layout.rows.size() ? 0 : layout.rows.get(row.index).size();
                    for (Chip chip : chips) if (chip.row == row.index &&
                            (click.y() < chip.y || click.y() < chip.y + 18 && click.x() < chip.x + chip.w / 2.0)) {
                        index = chip.index; break;
                    }
                    if (dragging.row == -1) layout.add(dragging.field, row.index, index);
                    else selectedRow = layout.move(dragging.row, dragging.index, row.index, index);
                }
                changed(); break;
            }
        }
        dragging = null; dragRow = -1;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (my < TOP || my >= bottom()) return super.mouseScrolled(mx, my, sx, sy);
        int delta = (int) (-sy * 22);
        if (mx < paletteRight) paletteScroll = Math.clamp(paletteScroll + delta, 0, Math.max(0, LoreLayout.FIELDS.size() * 22 - (bottom() - TOP)));
        else if (mx < layoutRight) {
            arrange();
            int size = rows.isEmpty() ? 0 : rows.getLast().y + rows.getLast().h + layoutScroll - TOP;
            layoutScroll = Math.clamp(layoutScroll + delta, 0, Math.max(0, size - (bottom() - TOP)));
        } else {
            int lines = preview.stream().mapToInt(line -> font.split(line, width - layoutRight - 16).size()).sum();
            previewScroll = Math.clamp(previewScroll + delta, 0, Math.max(0, lines * 11 - (bottom() - TOP)));
        }
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { revision++; Minecraft.getInstance().setScreen(parent); }
}
