package com.coflnet.gui.trade;

import com.coflnet.CoflModClient;
import com.coflnet.CoflModClient.WorthBasis;
import com.coflnet.gui.RenderUtils;
import com.coflnet.gui.cofl.CoflColConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Coins input dialog for the trade overlay. Lets the user type any amount
 * (2m, 1.5b, 80000000) OR pick one of two suggestion buttons (their side total
 * at full LBIN value, or full Median value). On confirm, the chosen amount is
 * stashed in {@link CoflModClient#pendingCoinAmount} and the real trade
 * Coins-transaction slot (36) is clicked, which opens Hypixel's coin sign;
 * the {@code openTextEdit} mixin then auto-fills that sign with the amount.
 * <p>
 * Returns to the {@link TradeGUI} when closed.
 */
public class CoinInputGUI extends Screen {
    private static final int PAD = 10;
    private static final int RADIUS = 4;
    private static final int COINS_SLOT = 36;

    private final ContainerScreen backing;
    private final ChestMenu menu;
    private final TradeGUI parent;

    private final long lbinSuggestion;
    private final long medianSuggestion;
    // Worth of items already on MY side (per basis), subtracted from suggestions.
    private final long myItemsLbin;
    private final long myItemsMedian;

    private EditBox input;
    private int panelX, panelY, panelW, panelH;
    private int lbinBtnX, lbinBtnY, medBtnX, medBtnY, sugBtnW, sugBtnH;
    private int confirmX, confirmY, confirmW, confirmH;
    private int cancelX, cancelY, cancelW, cancelH;

    // Lowball slider: 10%–100%, default 70%. The LBIN/Med suggestion buttons
    // multiply their full total by this percent.
    private int sliderX, sliderY, sliderW, sliderH;
    private int lowballPercent = 70;
    private boolean draggingSlider = false;
    private static final int MIN_PCT = 10;
    private static final int MAX_PCT = 100;

    public CoinInputGUI(ContainerScreen backing, TradeGUI parent) {
        super(Component.literal("Coins Input"));
        this.backing = backing;
        this.menu = backing.getMenu();
        this.parent = parent;
        this.lbinSuggestion = sideTotal(CoflModClient.TRADE_THEIR_SLOTS, WorthBasis.LBIN);
        this.medianSuggestion = sideTotal(CoflModClient.TRADE_THEIR_SLOTS, WorthBasis.MEDIAN);
        // Value of items I've ALREADY put on my side (excludes coins — those are
        // what this dialog adds). Subtracted from the suggestion so e.g. a 4m item
        // already offered lowers a 32m lowball suggestion to 28m of coins.
        this.myItemsLbin = sideItemsOnly(CoflModClient.TRADE_YOUR_SLOTS, WorthBasis.LBIN);
        this.myItemsMedian = sideItemsOnly(CoflModClient.TRADE_YOUR_SLOTS, WorthBasis.MEDIAN);
    }

    /** Full-value total of a side for a basis (offered coins count at face value). */
    private long sideTotal(int[] slots, WorthBasis basis) {
        long total = 0;
        for (int slot : slots) {
            if (slot >= menu.slots.size()) {
                continue;
            }
            ItemStack stack = menu.slots.get(slot).getItem();
            if (stack.isEmpty() || stack.getItem() == Items.AIR) {
                continue;
            }
            Long coins = CoflModClient.parseCoinStack(stack);
            if (coins != null) {
                total += coins;
                continue;
            }
            String id = CoflModClient.getIdFromStack(stack);
            Long worth = CoflModClient.parseWorthFromTips(
                    CoflCore.handlers.DescriptionHandler.getTooltipData(id), basis);
            if (worth != null) {
                total += worth * stack.getCount();
            }
        }
        return total;
    }

    /** Worth of a side's ITEMS only (ignores any coins already in those slots). */
    private long sideItemsOnly(int[] slots, WorthBasis basis) {
        long total = 0;
        for (int slot : slots) {
            if (slot >= menu.slots.size()) {
                continue;
            }
            ItemStack stack = menu.slots.get(slot).getItem();
            if (stack.isEmpty() || stack.getItem() == Items.AIR) {
                continue;
            }
            if (CoflModClient.parseCoinStack(stack) != null) {
                continue; // skip coins
            }
            String id = CoflModClient.getIdFromStack(stack);
            Long worth = CoflModClient.parseWorthFromTips(
                    CoflCore.handlers.DescriptionHandler.getTooltipData(id), basis);
            if (worth != null) {
                total += worth * stack.getCount();
            }
        }
        return total;
    }

    @Override
    protected void init() {
        panelW = 220;
        panelH = 164;
        panelX = this.width / 2 - panelW / 2;
        panelY = this.height / 2 - panelH / 2;

        Font font = Minecraft.getInstance().font;
        input = new EditBox(font, panelX + PAD, panelY + 28, panelW - PAD * 2, 16, Component.literal("amount"));
        input.setMaxLength(20);
        input.setValue("");
        addRenderableWidget(input);
        setInitialFocus(input);

        // Lowball slider — pushed below the input box (input ends at +44; its
        // label sits at +52, the track at +64) so they no longer overlap.
        sliderX = panelX + PAD;
        sliderY = panelY + 68;
        sliderW = panelW - PAD * 2;
        sliderH = 8;

        sugBtnW = (panelW - PAD * 3) / 2;
        sugBtnH = 18;
        lbinBtnX = panelX + PAD;
        lbinBtnY = panelY + 90;
        medBtnX = lbinBtnX + sugBtnW + PAD;
        medBtnY = lbinBtnY;

        confirmW = (panelW - PAD * 3) / 2;
        confirmH = 18;
        confirmX = panelX + PAD;
        confirmY = panelY + panelH - PAD - confirmH;
        cancelW = confirmW;
        cancelH = confirmH;
        cancelX = confirmX + confirmW + PAD;
        cancelY = confirmY;
    }

    /**
     * Coins to suggest = (their total × lowball%) − value of items I already
     * offered on my side, clamped at 0. So if I add a 4m item, the coin
     * suggestion drops by 4m. {@code myItems} must match the {@code fullTotal}
     * basis (LBIN total ⇄ LBIN of my items).
     */
    private long scaled(long fullTotal, long myItems) {
        long target = fullTotal * lowballPercent / 100L;
        return Math.max(0L, target - myItems);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        RenderUtils.drawRoundedRect(context, panelX, panelY, panelW, panelH, RADIUS, CoflColConfig.BACKGROUND_PRIMARY);
        RenderUtils.drawString(context, "§lAdd Coins", panelX + PAD, panelY + PAD, CoflColConfig.TEXT_PRIMARY);

        // Lowball slider.
        RenderUtils.drawString(context, "§7Lowball: §f" + lowballPercent + "%", sliderX, sliderY - 10, CoflColConfig.TEXT_PRIMARY);
        RenderUtils.drawRoundedRect(context, sliderX, sliderY, sliderW, sliderH, 2, CoflColConfig.BACKGROUND_SECONDARY);
        int knobX = sliderX + (int) ((long) (lowballPercent - MIN_PCT) * (sliderW - 6) / (MAX_PCT - MIN_PCT));
        RenderUtils.drawRoundedRect(context, knobX, sliderY - 2, 6, sliderH + 4, 2, CoflColConfig.CONFIRM);

        // Suggestion buttons (scaled by the lowball percent).
        drawButton(context, lbinBtnX, lbinBtnY, sugBtnW, sugBtnH, mouseX, mouseY,
                "LBIN " + fmt(scaled(lbinSuggestion, myItemsLbin)), CoflColConfig.BACKGROUND_SECONDARY, CoflColConfig.CONFIRM_HOVER);
        drawButton(context, medBtnX, medBtnY, sugBtnW, sugBtnH, mouseX, mouseY,
                "Med " + fmt(scaled(medianSuggestion, myItemsMedian)), CoflColConfig.BACKGROUND_SECONDARY, CoflColConfig.CONFIRM_HOVER);

        // Confirm / cancel.
        drawButton(context, confirmX, confirmY, confirmW, confirmH, mouseX, mouseY,
                "Confirm", CoflColConfig.CONFIRM, CoflColConfig.CONFIRM_HOVER);
        drawButton(context, cancelX, cancelY, cancelW, cancelH, mouseX, mouseY,
                "Cancel", CoflColConfig.CANCEL, CoflColConfig.CANCEL_HOVER);
    }

    private void drawButton(GuiGraphicsExtractor context, int x, int y, int w, int h, int mx, int my,
                            String label, int base, int hover) {
        boolean over = mx >= x && mx < x + w && my >= y && my < y + h;
        RenderUtils.drawRoundedRect(context, x, y, w, h, 2, over ? hover : base);
        RenderUtils.drawCenteredString(context, label, x + w / 2, y + 5, CoflColConfig.TEXT_PRIMARY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x();
        double my = click.y();

        // Slider grab (generous vertical hit area).
        if (inRect(mx, my, sliderX, sliderY - 4, sliderW, sliderH + 8)) {
            draggingSlider = true;
            updateSlider(mx);
            return true;
        }
        if (inRect(mx, my, lbinBtnX, lbinBtnY, sugBtnW, sugBtnH)) {
            input.setValue(String.valueOf(scaled(lbinSuggestion, myItemsLbin)));
            return true;
        }
        if (inRect(mx, my, medBtnX, medBtnY, sugBtnW, sugBtnH)) {
            input.setValue(String.valueOf(scaled(medianSuggestion, myItemsMedian)));
            return true;
        }
        if (inRect(mx, my, confirmX, confirmY, confirmW, confirmH)) {
            submit();
            return true;
        }
        if (inRect(mx, my, cancelX, cancelY, cancelW, cancelH)) {
            returnToTrade();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dragX, double dragY) {
        if (draggingSlider) {
            updateSlider(click.x());
            return true;
        }
        return super.mouseDragged(click, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        draggingSlider = false;
        return super.mouseReleased(click);
    }

    /** Maps a mouse-x onto the slider track and snaps the lowball percent. */
    private void updateSlider(double mx) {
        double frac = (mx - sliderX) / (double) (sliderW - 6);
        frac = Math.max(0.0, Math.min(1.0, frac));
        lowballPercent = MIN_PCT + (int) Math.round(frac * (MAX_PCT - MIN_PCT));
        // Snap to the nearest 5% for tidy values.
        lowballPercent = Math.round(lowballPercent / 5.0f) * 5;
        lowballPercent = Math.max(MIN_PCT, Math.min(MAX_PCT, lowballPercent));
    }

    /** Parses the typed value, stashes it, and clicks the real coins slot. */
    private void submit() {
        String raw = input.getValue() == null ? "" : input.getValue().trim();
        Long amount = parseAmount(raw);
        if (amount == null || amount <= 0) {
            return; // invalid input — leave dialog open
        }
        CoflModClient.pendingCoinAmount = String.valueOf(amount);
        // Return to trade first so the sign editor (opened by the click) layers
        // over the trade, then click the real coins-transaction slot.
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        returnToTrade();
        if (player != null) {
            client.gameMode.handleContainerInput(menu.containerId, COINS_SLOT, 0, ContainerInput.PICKUP, player);
        }
    }

    private void returnToTrade() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    /** Accepts plain digits and k/m/b suffixes (2m, 1.5b, 80000000). */
    static Long parseAmount(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        String in = s.toLowerCase().replace(",", "").trim();
        try {
            if (in.matches("^[0-9]+$")) {
                return Long.parseLong(in);
            }
            if (in.matches("^[0-9]*\\.?[0-9]+[kmb]$")) {
                char suf = in.charAt(in.length() - 1);
                double val = Double.parseDouble(in.substring(0, in.length() - 1));
                return switch (suf) {
                    case 'k' -> (long) (val * 1_000L);
                    case 'm' -> (long) (val * 1_000_000L);
                    case 'b' -> (long) (val * 1_000_000_000L);
                    default -> null;
                };
            }
            if (in.matches("^[0-9]+\\.[0-9]+$")) {
                return (long) Double.parseDouble(in);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    private static boolean inRect(double px, double py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // Esc returns to the trade rather than closing the whole trade.
        returnToTrade();
    }
}
