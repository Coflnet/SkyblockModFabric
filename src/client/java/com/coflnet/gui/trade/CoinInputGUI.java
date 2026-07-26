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

    private long lbinSuggestion;
    private long medianSuggestion;
    // Worth of items already on MY side (per basis), subtracted from suggestions.
    private long myItemsLbin;
    private long myItemsMedian;

    private EditBox input;
    private int panelX, panelY, panelW, panelH;
    private int lbinBtnX, lbinBtnY, medBtnX, medBtnY, sugBtnW, sugBtnH;
    private int confirmX, confirmY, confirmW, confirmH;
    private int cancelX, cancelY, cancelW, cancelH;

    // premium access controls the slider and suggestion buttons.
    private int sliderX, sliderY, sliderW, sliderH;
    private int lowballPercent = 70;
    private boolean draggingSlider = false;
    private static final int MIN_PCT = 10;
    private static final int MAX_PCT = 100;

    private static final int LOCKED_TINT = 0xFF3A3F46;

    public CoinInputGUI(ContainerScreen backing, TradeGUI parent) {
        super(Component.literal("Coins Input"));
        this.backing = backing;
        this.menu = backing.getMenu();
        this.parent = parent;
        refreshSuggestions();
    }

    public ContainerScreen getBacking() {
        return backing;
    }

    private void refreshSuggestions() {
        var container = menu.getContainer();
        lbinSuggestion = TradePriceCache.valueSlots(
                container, CoflModClient.TRADE_THEIR_SLOTS, WorthBasis.LBIN, true).total();
        medianSuggestion = TradePriceCache.valueSlots(
                container, CoflModClient.TRADE_THEIR_SLOTS, WorthBasis.MEDIAN, true).total();
        myItemsLbin = TradePriceCache.valueSlots(
                container, CoflModClient.TRADE_YOUR_SLOTS, WorthBasis.LBIN, false).total();
        myItemsMedian = TradePriceCache.valueSlots(
                container, CoflModClient.TRADE_YOUR_SLOTS, WorthBasis.MEDIAN, false).total();
    }

    @Override
    protected void init() {
        panelW = 220;
        panelH = 164;
        panelX = this.width / 2 - panelW / 2;
        panelY = this.height / 2 - panelH / 2;

        Font font = Minecraft.getInstance().font;
        String prev = input == null ? "" : input.getValue();
        input = new EditBox(font, panelX + PAD, panelY + 28, panelW - PAD * 2, 16, Component.literal("amount"));
        input.setMaxLength(20);
        input.setValue(prev);
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
        boolean premium = com.coflnet.config.TradeGuiManager.hasPremium();
        if (!premium) {
            draggingSlider = false;
        }
        refreshSuggestions();

        RenderUtils.drawRoundedRect(context, panelX, panelY, panelW, panelH, RADIUS, CoflColConfig.BACKGROUND_PRIMARY);
        RenderUtils.drawString(context, "§lAdd Coins", panelX + PAD, panelY + PAD, CoflColConfig.TEXT_PRIMARY);

        Font font = Minecraft.getInstance().font;

        if (premium) {
            RenderUtils.drawString(context, "§7lowball: §f" + lowballPercent + "% §r§8§o(skycofl premium)", sliderX, sliderY - 10, CoflColConfig.TEXT_PRIMARY);
            RenderUtils.drawRoundedRect(context, sliderX, sliderY, sliderW, sliderH, 2, CoflColConfig.BACKGROUND_SECONDARY);
            int knobX = sliderX + (int) ((long) (lowballPercent - MIN_PCT) * (sliderW - 6) / (MAX_PCT - MIN_PCT));
            RenderUtils.drawRoundedRect(context, knobX, sliderY - 2, 6, sliderH + 4, 2, CoflColConfig.CONFIRM);

            drawButton(context, lbinBtnX, lbinBtnY, sugBtnW, sugBtnH, mouseX, mouseY,
                    "lbin " + fmt(scaled(lbinSuggestion, myItemsLbin)), CoflColConfig.BACKGROUND_SECONDARY, CoflColConfig.CONFIRM_HOVER);
            drawButton(context, medBtnX, medBtnY, sugBtnW, sugBtnH, mouseX, mouseY,
                    "med " + fmt(scaled(medianSuggestion, myItemsMedian)), CoflColConfig.BACKGROUND_SECONDARY, CoflColConfig.CONFIRM_HOVER);

            RenderUtils.drawString(context, "§8premium suggestions enabled",
                    panelX + PAD, panelY + 116, CoflColConfig.TEXT_PRIMARY);
        } else {
            RenderUtils.drawString(context, "§7lowball: §8locked", sliderX, sliderY - 10, CoflColConfig.TEXT_PRIMARY);
            RenderUtils.drawRoundedRect(context, sliderX, sliderY, sliderW, sliderH, 2, LOCKED_TINT);
            drawButton(context, lbinBtnX, lbinBtnY, sugBtnW, sugBtnH, mouseX, mouseY,
                    "§8lbin locked", LOCKED_TINT, LOCKED_TINT);
            drawButton(context, medBtnX, medBtnY, sugBtnW, sugBtnH, mouseX, mouseY,
                    "§8med locked", LOCKED_TINT, LOCKED_TINT);
            RenderUtils.drawString(context, "§8premium suggestions locked",
                    panelX + PAD, panelY + 116, CoflColConfig.TEXT_PRIMARY);

            boolean overLocked = inRect(mouseX, mouseY, sliderX, sliderY - 12, sliderW, sliderH + 14)
                    || inRect(mouseX, mouseY, lbinBtnX, lbinBtnY, sugBtnW, sugBtnH)
                    || inRect(mouseX, mouseY, medBtnX, medBtnY, sugBtnW, sugBtnH);
            if (overLocked) {
                context.setComponentTooltipForNextFrame(font, java.util.List.of(
                        Component.literal("§6requires skycofl premium"),
                        Component.literal("§7suggests lowball prices for you, adjustable"),
                        Component.literal("§7with the lowball slider and lbin or med buttons."),
                        Component.literal("§7get it with §e/cofl buy premium§7."),
                        Component.literal("§8premium access is read from login.")),
                        mouseX, mouseY);
            }
        }

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

        boolean premium = com.coflnet.config.TradeGuiManager.hasPremium();

        // Slider grab (generous vertical hit area).
        if (inRect(mx, my, sliderX, sliderY - 4, sliderW, sliderH + 8)) {
            if (premium) {
                draggingSlider = true;
                updateSlider(mx);
            }
            return true;
        }
        if (inRect(mx, my, lbinBtnX, lbinBtnY, sugBtnW, sugBtnH)) {
            if (premium) {
                long value = scaled(lbinSuggestion, myItemsLbin);
                if (value > 0L) {
                    input.setValue(String.valueOf(value));
                }
            }
            return true;
        }
        if (inRect(mx, my, medBtnX, medBtnY, sugBtnW, sugBtnH)) {
            if (premium) {
                long value = scaled(medianSuggestion, myItemsMedian);
                if (value > 0L) {
                    input.setValue(String.valueOf(value));
                }
            }
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
