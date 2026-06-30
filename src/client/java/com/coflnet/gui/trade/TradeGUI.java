package com.coflnet.gui.trade;

import com.coflnet.CoflModClient;
import com.coflnet.CoflModClient.WorthBasis;
import com.coflnet.gui.RenderUtils;
import com.coflnet.gui.cofl.CoflColConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-replacement overlay for the Hypixel trade window.
 * <p>
 * Layout: a wide trade panel (two scrollable item tables separated by a vertical
 * splitter) on top. Below it the player inventory is right-aligned to the panel,
 * with the profit box (gray outline), Accept button, and the other player's
 * pending-confirm status stacked to the LEFT of the inventory. Item rows are
 * gray; only the YOU / other-player headers are colored. Tables are scissor-
 * clipped so scrolled items never bleed over the headers or buttons.
 */
public class TradeGUI extends Screen {
    private static final int PAD = 10;
    private static final int RADIUS = 4;
    private static final int ROW_H = 20;
    private static final int ITEM = 16;
    private static final int INV_CELL = 25;

    private static final int SEPARATOR = 0xFF55606B;
    private static final int ROW_TINT = 0xFF2C323B;
    private static final int DISABLED = 0xFF555B63;

    private final ContainerScreen backing;
    private final ChestMenu menu;
    private final String otherName;

    private WorthBasis basis = WorthBasis.LBIN;

    private int panelX, panelY, panelW, panelH;
    private int colYouX, colThemX, colW;
    private int splitterX;
    private int tableTop, tableBottom;
    private int invGridX, invGridY;
    private int invBlockX, invBlockY, invBlockW, invBlockH;
    private int basisBtnX, basisBtnY, basisBtnW, basisBtnH;
    private int acceptBtnX, acceptBtnY, acceptBtnW, acceptBtnH;
    private int coinsBtnX, coinsBtnY, coinsBtnW, coinsBtnH;
    private int profitX, profitY, profitW, profitH;
    private int ctrlX, ctrlY, ctrlW, ctrlH;
    private int statusX, statusY;
    private int settBtnX, settBtnY, settBtnW, settBtnH;

    // Layout option (changed via the control-panel settings button): how many
    // columns the item list uses. Default 1 (the original look). 2–3 columns use
    // compact, shorter rows so many items are easier to see at once. Persisted in
    // CoflModConfig so the choice survives across trades and restarts.
    private int listColumns = com.coflnet.config.TradeGuiManager.getListColumns();
    private static final int MAX_COLUMNS = 3;
    private boolean settingsOpen = false;

    private int scrollYou = 0;
    private int scrollThem = 0;

    // Lag fix: instead of polling the heavy backend pipeline every second from
    // the render thread, we detect changes with a CHEAP signature of just the
    // trade slots and only re-price OFF-thread when that signature changes.
    private String lastTradeSig = null;
    private volatile boolean repriceInFlight = false;

    private record Row(int slotId, ItemStack stack, boolean isCoins, long coinAmount) {}

    public TradeGUI(ContainerScreen backing) {
        super(Component.literal("SkyCofl Trade"));
        this.backing = backing;
        this.menu = backing.getMenu();
        this.otherName = resolveOtherName(backing.getTitle().getString());
    }

    /**
     * Resolves the other player's name. Prefers the full IGN captured from the
     * trade-request chat line; falls back to the (possibly truncated) title name.
     */
    private static String resolveOtherName(String title) {
        String fromChat = CoflModClient.lastTradePartner;
        if (fromChat != null && !fromChat.isEmpty()) {
            return fromChat;
        }
        return extractOtherName(title);
    }

    private static String extractOtherName(String title) {
        if (title == null) {
            return "THEM";
        }
        String t = ChatStrip(title).trim();
        if (t.startsWith("You")) {
            t = t.substring(3);
        }
        t = t.trim();
        return t.isEmpty() ? "THEM" : t;
    }

    /**
     * Cheap per-frame change detection over just the trade slots (both sides).
     * Builds a tiny string of itemId×count per non-empty trade slot — no NBT,
     * no gzip, no base64. Only when this signature changes do we fire the heavy
     * backend re-price, and we do it on a virtual thread so the render thread
     * never blocks (root cause of the add/remove FPS spikes).
     */
    private void maybeReprice() {
        StringBuilder sig = new StringBuilder();
        appendSig(sig, CoflModClient.TRADE_YOUR_SLOTS);
        appendSig(sig, CoflModClient.TRADE_THEIR_SLOTS);
        String current = sig.toString();
        if (current.equals(lastTradeSig)) {
            return;
        }
        // Check the in flight flag before we advance lastTradeSig, so a change
        // that lands while a reprice is running is not swallowed. We leave
        // lastTradeSig on the old value and just bail, and then the next frame
        // still sees a mismatch and fires the reprice once the slot is free.
        if (repriceInFlight) {
            return;
        }
        lastTradeSig = current;
        repriceInFlight = true;
        final String title = backing.getTitle().getString();
        final NonNullList<ItemStack> snapshot = NonNullList.create();
        snapshot.addAll(menu.getItems());
        Thread.startVirtualThread(() -> {
            try {
                CoflModClient.loadDescriptionsForItems(title, snapshot);
            } catch (Exception ignored) {
            } finally {
                repriceInFlight = false;
            }
        });
    }

    private void appendSig(StringBuilder sig, int[] slots) {
        for (int slot : slots) {
            if (slot >= menu.slots.size()) {
                continue;
            }
            ItemStack stack = menu.slots.get(slot).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            sig.append(slot).append(':')
               .append(CoflModClient.getIdFromStack(stack)).append('x')
               .append(stack.getCount()).append(';');
        }
    }

    @Override
    protected void init() {
        panelW = Math.min(this.width - 24, 520);
        panelX = this.width / 2 - panelW / 2;

        int innerW = panelW - PAD * 2;
        colW = (innerW - PAD) / 2;
        colYouX = panelX + PAD;
        colThemX = colYouX + colW + PAD;
        splitterX = colYouX + colW + PAD / 2;

        int invRows = 4;
        int invW = 9 * INV_CELL + 8;
        int invH = invRows * INV_CELL + 8;

        int headerH = 32;   // smaller now that the basis toggle moves to the control bar
        // Bottom bar inside the panel: totals row + margin + Add Coins button,
        // with equal padding all around (fixes the lopsided bottom gap).
        int totalsH = 12;
        int coinsMargin = 8;
        int coinsH = 18;
        int bottomBarH = totalsH + coinsMargin + coinsH;

        int belowBlockH = invH;  // inventory + side controls share this height
        int fixed = headerH + bottomBarH + PAD * 2 + 10 + belowBlockH + 12;
        int avail = this.height - fixed;
        int maxTablesH = 5 * ROW_H + 6;
        int tablesH = Math.max(2 * ROW_H, Math.min(maxTablesH, avail));
        panelH = headerH + tablesH + bottomBarH + PAD;

        int totalH = panelH + 10 + belowBlockH;
        panelY = Math.max(6, this.height / 2 - totalH / 2);

        tableTop = panelY + headerH;
        tableBottom = panelY + headerH + tablesH;

        // Add Coins button — equal padding (PAD) on sides and bottom, margin above.
        coinsBtnW = panelW - PAD * 2;
        coinsBtnH = coinsH;
        coinsBtnX = panelX + PAD;
        coinsBtnY = panelY + panelH - PAD - coinsH;

        // --- Below the panel ---
        // Inventory right-aligned to the panel's right edge.
        invBlockW = invW;
        invBlockH = invH;
        invBlockX = panelX + panelW - invW;
        invBlockY = panelY + panelH + 10;
        invGridX = invBlockX + 4;
        invGridY = invBlockY + 4;

        // Control bar: its own gray bordered box to the LEFT of the inventory,
        // spanning from the panel's left edge up to (not merging with) the
        // inventory. Holds profit, basis toggle, Accept, and pending status.
        ctrlX = panelX;
        ctrlY = invBlockY;
        ctrlW = invBlockX - 8 - panelX;   // reach the inventory but keep an 8px gap
        ctrlH = invBlockH;

        // Settings button in the control panel's top-right corner.
        settBtnW = 14;
        settBtnH = 14;
        settBtnX = ctrlX + ctrlW - settBtnW - 4;
        settBtnY = ctrlY + 4;

        int inX = ctrlX + 6;
        int inW = ctrlW - 12;

        profitX = inX;
        profitY = ctrlY + 6;
        profitW = inW;
        profitH = 32;

        // Basis toggle moved here (was top-right of the panel).
        basisBtnX = inX;
        basisBtnY = profitY + profitH + 4;
        basisBtnW = inW;
        basisBtnH = 14;

        acceptBtnX = inX;
        acceptBtnY = basisBtnY + basisBtnH + 4;
        acceptBtnW = inW;
        acceptBtnH = 20;

        statusX = inX;
        statusY = acceptBtnY + acceptBtnH + 4;
    }

    private List<Row> buildRows(int[] slots) {
        List<Row> rows = new ArrayList<>();
        for (int slot : slots) {
            if (slot >= menu.slots.size()) {
                continue;
            }
            ItemStack stack = menu.slots.get(slot).getItem();
            if (stack.isEmpty() || stack.getItem() == Items.AIR) {
                continue;
            }
            Long coins = CoflModClient.parseCoinStack(stack);
            rows.add(new Row(slot, stack, coins != null, coins == null ? 0L : coins));
        }
        return rows;
    }

    private long rowWorth(Row row) {
        if (row.isCoins()) {
            return row.coinAmount();
        }
        String id = CoflModClient.getIdFromStack(row.stack());
        Long w = CoflModClient.parseWorthFromTips(
                CoflCore.handlers.DescriptionHandler.getTooltipData(id), basis);
        return (w == null ? 0L : w) * row.stack().getCount();
    }

    private long sideTotal(List<Row> rows) {
        long total = 0;
        for (Row r : rows) {
            total += rowWorth(r);
        }
        return total;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (Minecraft.getInstance().getConnection() == null || backing.getMenu() != this.menu) {
            onClose();
            return;
        }
        Font font = Minecraft.getInstance().font;

        // Re-price ONLY when the trade contents actually change, and do the heavy
        // backend call OFF the render thread. The old code serialized all 45 slots
        // to NBT+gzip+base64 and hit the backend synchronously every second on the
        // render thread → multi-second FPS-to-0 spikes on every add/remove.
        maybeReprice();

        List<Row> youRows = buildRows(CoflModClient.TRADE_YOUR_SLOTS);
        List<Row> themRows = buildRows(CoflModClient.TRADE_THEIR_SLOTS);
        long youTotal = sideTotal(youRows);
        long themTotal = sideTotal(themRows);

        RenderUtils.drawRoundedRect(context, panelX, panelY, panelW, panelH, RADIUS, CoflColConfig.BACKGROUND_PRIMARY);
        RenderUtils.drawString(context, "§lSkyCofl Trade", panelX + PAD, panelY + PAD, CoflColConfig.TEXT_PRIMARY);

        // Header bars gray; YOU label green (left), other name red + RIGHT-aligned, full name.
        int barY = panelY + PAD + 8;
        RenderUtils.drawRoundedRect(context, colYouX, barY, colW, 12, 2, CoflColConfig.BACKGROUND_SECONDARY);
        RenderUtils.drawRoundedRect(context, colThemX, barY, colW, 12, 2, CoflColConfig.BACKGROUND_SECONDARY);
        RenderUtils.drawString(context, "§aYou", colYouX + 4, barY + 2, CoflColConfig.TEXT_PRIMARY);
        int nameW = font.width(otherName);
        RenderUtils.drawString(context, "§c" + otherName, colThemX + colW - nameW - 4, barY + 2, CoflColConfig.TEXT_PRIMARY);

        RenderUtils.drawRect(context, splitterX, tableTop - 2, 1, (tableBottom - tableTop) + 4, SEPARATOR);

        // Scissor-clip each table so scrolled items stay inside the viewport.
        context.enableScissor(colYouX, tableTop, colYouX + colW, tableBottom);
        drawSide(context, font, youRows, colYouX, scrollYou, mouseX, mouseY);
        context.disableScissor();
        context.enableScissor(colThemX, tableTop, colThemX + colW, tableBottom);
        drawSide(context, font, themRows, colThemX, scrollThem, mouseX, mouseY);
        context.disableScissor();

        // Scrollbars (outside the scissor so they always show).
        drawScrollbar(context, colYouX, scrollYou, youRows.size());
        drawScrollbar(context, colThemX, scrollThem, themRows.size());

        // Totals row under the tables.
        RenderUtils.drawString(context, "§7You: §f" + fmt(youTotal), colYouX, tableBottom + 3, CoflColConfig.TEXT_PRIMARY);
        RenderUtils.drawString(context, "§7" + otherName + ": §f" + fmt(themTotal), colThemX, tableBottom + 3, CoflColConfig.TEXT_PRIMARY);

        // Add Coins button (clear of the items thanks to the margin + clip).
        boolean coinsHover = inRect(mouseX, mouseY, coinsBtnX, coinsBtnY, coinsBtnW, coinsBtnH);
        RenderUtils.drawRoundedRect(context, coinsBtnX, coinsBtnY, coinsBtnW, coinsBtnH, 2,
                coinsHover ? CoflColConfig.CONFIRM_HOVER : CoflColConfig.BACKGROUND_SECONDARY);
        RenderUtils.drawCenteredString(context, "Add Coins", coinsBtnX + coinsBtnW / 2, coinsBtnY + 5, CoflColConfig.TEXT_PRIMARY);

        // --- Below the panel: control bar (left) + inventory (right) ---
        TradeState state = readTradeState();
        long net = themTotal - youTotal;
        String netStr = (net >= 0) ? "§aProfit +" + fmt(net) : "§cLosing -" + fmt(-net);

        // Control bar: its own gray box (NO border — borders only used on the
        // profit box, matching the rest of the trade UI).
        RenderUtils.drawRoundedRect(context, ctrlX, ctrlY, ctrlW, ctrlH, RADIUS, CoflColConfig.BACKGROUND_PRIMARY);

        // Settings button (top-right of the control panel).
        boolean settHover = inRect(mouseX, mouseY, settBtnX, settBtnY, settBtnW, settBtnH);
        RenderUtils.drawRoundedRect(context, settBtnX, settBtnY, settBtnW, settBtnH, 2,
                settHover ? CoflColConfig.CONFIRM_HOVER : CoflColConfig.BACKGROUND_SECONDARY);
        RenderUtils.drawCenteredString(context, "⚙", settBtnX + settBtnW / 2, settBtnY + 3, CoflColConfig.TEXT_PRIMARY);

        // Net profit/loss only (no outline, no You/They give lines — those are
        // visible in the trade tables' totals already, and the box was crowding
        // the settings gear). Just the bottom line, the thing that matters.
        RenderUtils.drawString(context, netStr, profitX, profitY + 2, CoflColConfig.TEXT_PRIMARY);

        // Basis toggle (moved here from the top-right).
        boolean basisHover = inRect(mouseX, mouseY, basisBtnX, basisBtnY, basisBtnW, basisBtnH);
        RenderUtils.drawRoundedRect(context, basisBtnX, basisBtnY, basisBtnW, basisBtnH, 2,
                basisHover ? CoflColConfig.CONFIRM_HOVER : CoflColConfig.BACKGROUND_SECONDARY);
        RenderUtils.drawCenteredString(context, (basis == WorthBasis.LBIN ? "Basis: LBIN (click)" : "Basis: Med (click)"),
                basisBtnX + basisBtnW / 2, basisBtnY + 3, CoflColConfig.TEXT_PRIMARY);

        // Accept button.
        boolean acceptHover = inRect(mouseX, mouseY, acceptBtnX, acceptBtnY, acceptBtnW, acceptBtnH);
        int acceptCol = state.onCooldown ? DISABLED : (acceptHover ? CoflColConfig.CONFIRM_HOVER : CoflColConfig.CONFIRM);
        RenderUtils.drawRoundedRect(context, acceptBtnX, acceptBtnY, acceptBtnW, acceptBtnH, 2, acceptCol);
        RenderUtils.drawCenteredString(context, "Accept", acceptBtnX + acceptBtnW / 2, acceptBtnY + 6, 0xFF222831);

        // Pending confirmation row.
        String statusLine = "§7" + otherName + ": " + state.themStatus;
        RenderUtils.drawString(context, truncate(font, ChatStrip(statusLine), ctrlW - 12), statusX, statusY, CoflColConfig.TEXT_PRIMARY);
        if (!state.youRaw.isEmpty()) {
            RenderUtils.drawString(context, "§8" + truncate(font, state.youRaw, ctrlW - 12), statusX, statusY + 9, CoflColConfig.TEXT_PRIMARY);
        }

        // Inventory block (right-aligned). Items scaled up to fill the bigger cells.
        RenderUtils.drawRoundedRect(context, invBlockX, invBlockY, invBlockW, invBlockH, RADIUS, CoflColConfig.BACKGROUND_PRIMARY);
        float itemScale = (INV_CELL - 4) / (float) ITEM;   // grow the 16px icon to ~the cell
        for (int i = 45; i < menu.slots.size(); i++) {
            int[] rect = invSlotRect(i);
            if (rect == null) {
                continue;
            }
            RenderUtils.drawRect(context, rect[0], rect[1], INV_CELL - 3, INV_CELL - 3, CoflColConfig.BACKGROUND_SECONDARY);
            ItemStack stack = menu.slots.get(i).getItem();
            if (!stack.isEmpty() && stack.getItem() != Items.AIR) {
                drawScaledItem(context, stack, rect[0] + 1, rect[1] + 1, itemScale);
                if (inRect(mouseX, mouseY, rect[0], rect[1], INV_CELL - 3, INV_CELL - 3)) {
                    context.setTooltipForNextFrame(font, stack, mouseX, mouseY);
                }
            }
        }

        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            RenderUtils.drawItemStack(context, carried, mouseX - 8, mouseY - 8, 1);
        }

        // Settings dropdown — drawn LAST so it sits on top of everything.
        if (settingsOpen) {
            int[] r = settingsRowRect();
            int boxY = settBtnY + settBtnH + 2;
            RenderUtils.drawRoundedRect(context, r[0], boxY, r[2], r[3] + 4, 3, CoflColConfig.BACKGROUND_SECONDARY);
            boolean rowHover = inRect(mouseX, mouseY, r[0], r[1], r[2], r[3]);
            RenderUtils.drawRoundedRect(context, r[0] + 2, r[1], r[2] - 4, r[3], 2,
                    rowHover ? CoflColConfig.CONFIRM_HOVER : CoflColConfig.BACKGROUND_PRIMARY);
            String label = "Columns: " + listColumns + " (click)";
            RenderUtils.drawString(context, label, r[0] + 6, r[1] + 3, CoflColConfig.TEXT_PRIMARY);
        }
    }

    /** Rect [x,y,w,h] of the single settings dropdown row (the 1/2-column toggle). */
    private int[] settingsRowRect() {
        int w = Math.max(110, settBtnW + 96);
        int x = settBtnX + settBtnW - w;     // right-aligned under the gear
        int y = settBtnY + settBtnH + 4;
        return new int[]{x, y, w, 13};
    }

    /** Draws an item icon scaled up via the pose matrix (vanilla render is fixed 16px). */
    private void drawScaledItem(GuiGraphicsExtractor context, ItemStack stack, int x, int y, float scale) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        RenderUtils.drawItemStack(context, stack, 0, 0, 1);
        pose.popMatrix();
    }

    private void drawScrollbar(GuiGraphicsExtractor context, int x, int scroll, int rowCount) {
        int contentH = contentHeight(rowCount);
        int viewH = tableBottom - tableTop;
        if (contentH <= viewH) {
            return;
        }
        int trackX = x + colW - 3;
        int barH = Math.max(12, (int) ((long) viewH * viewH / contentH));
        int maxScroll = contentH - viewH;
        int barY = tableTop + (maxScroll <= 0 ? 0 : (int) ((long) scroll * (viewH - barH) / maxScroll));
        RenderUtils.drawRect(context, trackX, tableTop, 3, viewH, CoflColConfig.BACKGROUND_PRIMARY);
        RenderUtils.drawRect(context, trackX, barY, 3, barH, CoflColConfig.TEXT_PRIMARY);
    }

    /** Total pixel height of the item list for a row count, accounting for columns. */
    private int contentHeight(int rowCount) {
        int lines = (rowCount + cols() - 1) / cols();
        return lines * cellH();
    }

    // --- Item list grid metrics (depend on listColumns) ---
    private boolean compact() {
        return listColumns > 1;
    }

    private int cols() {
        return listColumns;
    }

    private int cellW() {
        return (colW - (cols() - 1) * 2) / cols();
    }

    private int cellH() {
        return compact() ? 14 : ROW_H;
    }

    private void drawSide(GuiGraphicsExtractor context, Font font, List<Row> rows, int x, int scroll, int mouseX, int mouseY) {
        int cols = cols();
        int cellW = cellW();
        int cellH = cellH();
        int idx = 0;
        for (Row row : rows) {
            int col = idx % cols;
            int line = idx / cols;
            int cx = x + col * (cellW + 2);
            int cy = tableTop - scroll + line * cellH;
            idx++;
            if (cy + cellH < tableTop || cy > tableBottom) {
                continue;
            }
            boolean hover = inRect(mouseX, mouseY, cx, cy, cellW, cellH) && mouseY >= tableTop && mouseY <= tableBottom;
            RenderUtils.drawRect(context, cx, cy, cellW, cellH - 1, hover ? CoflColConfig.BACKGROUND_SECONDARY : ROW_TINT);
            RenderUtils.drawRect(context, cx, cy + cellH - 1, cellW, 1, SEPARATOR);

            if (!row.isCoins()) {
                if (compact()) {
                    // shrink the 16px icon to fit the shorter row
                    drawScaledItem(context, row.stack(), cx + 1, cy + 1, (cellH - 3) / (float) ITEM);
                } else {
                    RenderUtils.drawItemStack(context, row.stack(), cx + 1, cy + 2, 1);
                }
            }
            int iconW = compact() ? (cellH - 2) : ITEM;
            String name = row.isCoins() ? "Coins" : ChatStrip(row.stack().getHoverName().getString());
            String worth = row.isCoins() ? fmtFull(row.coinAmount()) : fmt(rowWorth(row));
            int worthW = font.width(worth);
            int textX = cx + iconW + 3;
            int worthCol = row.isCoins() ? 0xFFFFD24A : 0xFFFFC832;

            if (compact()) {
                // One compact line: name (truncated) then worth right-aligned.
                int maxNameW = cellW - iconW - 6 - worthW - 4;
                RenderUtils.drawString(context, truncate(font, name, maxNameW), textX, cy + 3, CoflColConfig.TEXT_PRIMARY);
                RenderUtils.drawString(context, worth, cx + cellW - worthW - 3, cy + 3, worthCol);
            } else {
                int maxNameW = cellW - iconW - 8 - worthW - 8;
                RenderUtils.drawString(context, truncate(font, name, maxNameW), textX, cy + 2, CoflColConfig.TEXT_PRIMARY);
                RenderUtils.drawString(context, worth, cx + cellW - worthW - 6, cy + 11, worthCol);
            }

            if (hover && !row.isCoins()) {
                context.setTooltipForNextFrame(font, row.stack(), mouseX, mouseY);
            }
        }
    }

    private TradeState readTradeState() {
        TradeState s = new TradeState();
        String you = slotName(39);
        String them = slotName(41);
        s.youRaw = you == null ? "" : you;
        s.onCooldown = you != null && you.toLowerCase().contains("timer");

        if (them != null && them.toLowerCase().contains("confirmed")) {
            s.themStatus = "§aThey accepted";
        } else if (them != null && them.toLowerCase().contains("timer")) {
            s.themStatus = "§7Cooldown" + extractParens(them);
        } else if (them != null && !them.isEmpty()) {
            s.themStatus = "§7" + them;
        } else {
            s.themStatus = "§7Waiting…";
        }
        return s;
    }

    private static class TradeState {
        boolean onCooldown = false;
        String youRaw = "";
        String themStatus = "§7Waiting…";
    }

    private String slotName(int slot) {
        if (slot >= menu.slots.size()) {
            return null;
        }
        ItemStack stack = menu.slots.get(slot).getItem();
        if (stack.isEmpty()) {
            return null;
        }
        return ChatStrip(stack.getHoverName().getString());
    }

    private static String extractParens(String s) {
        int a = s.indexOf('(');
        int b = s.indexOf(')');
        return (a >= 0 && b > a) ? " " + s.substring(a, b + 1) : "";
    }

    private int[] invSlotRect(int slotId) {
        int inv = slotId - 45;
        if (inv < 0) {
            return null;
        }
        if (inv < 27) {
            return new int[]{invGridX + (inv % 9) * INV_CELL, invGridY + (inv / 9) * INV_CELL};
        }
        int hot = inv - 27;
        return new int[]{invGridX + hot * INV_CELL, invGridY + 3 * INV_CELL + 4};
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        int button = click.button();
        boolean shift = (click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;

        // Settings dropdown (drawn on top, so handle its hits first).
        if (settingsOpen) {
            int[] r = settingsRowRect();
            if (inRect(mx, my, r[0], r[1], r[2], r[3])) {
                listColumns = (listColumns % MAX_COLUMNS) + 1;  // cycle 1 -> 2 -> 3 -> 1
                // Persist the choice so it sticks across trades and restarts.
                com.coflnet.config.TradeGuiManager.setListColumns(listColumns);
                scrollYou = 0;
                scrollThem = 0;
                return true;
            }
            // Click anywhere else closes the dropdown.
            settingsOpen = false;
        }
        if (inRect(mx, my, settBtnX, settBtnY, settBtnW, settBtnH)) {
            settingsOpen = !settingsOpen;
            return true;
        }

        if (inRect(mx, my, basisBtnX, basisBtnY, basisBtnW, basisBtnH)) {
            basis = (basis == WorthBasis.LBIN) ? WorthBasis.MEDIAN : WorthBasis.LBIN;
            return true;
        }
        if (inRect(mx, my, acceptBtnX, acceptBtnY, acceptBtnW, acceptBtnH)) {
            // The button renders greyed out while the deal timer is running, so
            // the click is gated on the same cooldown flag and we just eat it
            // here rather than forwarding a packet the server would ignore.
            if (!readTradeState().onCooldown) {
                forwardSlot(39, 0, ContainerInput.PICKUP);
            }
            return true;
        }
        if (inRect(mx, my, coinsBtnX, coinsBtnY, coinsBtnW, coinsBtnH)) {
            Minecraft.getInstance().gui.setScreen(new CoinInputGUI(backing, this));
            return true;
        }
        Integer rowSlot = rowSlotAt(mx, my);
        if (rowSlot != null) {
            forwardSlot(rowSlot, button, shift ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP);
            return true;
        }
        for (int i = 45; i < menu.slots.size(); i++) {
            int[] rect = invSlotRect(i);
            if (rect != null && inRect(mx, my, rect[0], rect[1], INV_CELL - 3, INV_CELL - 3)) {
                forwardSlot(i, button, shift ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP);
                return true;
            }
        }
        if (!menu.getCarried().isEmpty()) {
            int empty = firstEmptyInventorySlot();
            if (empty >= 0) {
                forwardSlot(empty, 0, ContainerInput.PICKUP);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private int firstEmptyInventorySlot() {
        for (int i = 45; i < menu.slots.size(); i++) {
            if (menu.slots.get(i).getItem().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private Integer rowSlotAt(double mx, double my) {
        if (my < tableTop || my > tableBottom) {
            return null;
        }
        if (mx >= colYouX && mx < colYouX + colW) {
            return rowSlotInList(buildRows(CoflModClient.TRADE_YOUR_SLOTS), colYouX, scrollYou, mx, my);
        }
        if (mx >= colThemX && mx < colThemX + colW) {
            return rowSlotInList(buildRows(CoflModClient.TRADE_THEIR_SLOTS), colThemX, scrollThem, mx, my);
        }
        return null;
    }

    private Integer rowSlotInList(List<Row> rows, int x, int scroll, double mx, double my) {
        int cols = cols();
        int cellW = cellW();
        int cellH = cellH();
        for (int idx = 0; idx < rows.size(); idx++) {
            int col = idx % cols;
            int line = idx / cols;
            int cx = x + col * (cellW + 2);
            int cy = tableTop - scroll + line * cellH;
            if (inRect(mx, my, cx, cy, cellW, cellH)) {
                return rows.get(idx).slotId();
            }
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = (int) (-scrollY * cellH());
        if (mouseX >= colThemX) {
            scrollThem = clampScroll(scrollThem + delta, buildRows(CoflModClient.TRADE_THEIR_SLOTS).size());
        } else {
            scrollYou = clampScroll(scrollYou + delta, buildRows(CoflModClient.TRADE_YOUR_SLOTS).size());
        }
        return true;
    }

    private int clampScroll(int value, int rowCount) {
        int contentH = contentHeight(rowCount);
        int viewH = tableBottom - tableTop;
        int max = Math.max(0, contentH - viewH);
        return Math.max(0, Math.min(value, max));
    }

    private void forwardSlot(int slotId, int button, ContainerInput type) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Minecraft.getInstance().gameMode.handleContainerInput(menu.containerId, slotId, button, type, player);
    }

    private static boolean inRect(double px, double py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private static String ChatStrip(String s) {
        return net.minecraft.ChatFormatting.stripFormatting(s);
    }

    private static String truncate(Font font, String text, int maxW) {
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

    private static String fmt(long coins) {
        if (coins >= 1_000_000_000L) {
            return String.format(java.util.Locale.US, "%.1fB", coins / 1_000_000_000.0);
        } else if (coins >= 1_000_000L) {
            return String.format(java.util.Locale.US, "%.1fM", coins / 1_000_000.0);
        } else if (coins >= 1_000L) {
            return String.format(java.util.Locale.US, "%.1fK", coins / 1_000.0);
        }
        return String.valueOf(coins);
    }

    private static String fmtFull(long coins) {
        return String.format(java.util.Locale.US, "%,d", coins);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        backing.onClose();
        super.onClose();
    }
}
