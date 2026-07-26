package com.coflnet.gui.flip;

import com.coflnet.config.FlipHudManager;
import com.coflnet.gui.BinGUI;
import com.coflnet.gui.RenderUtils;
import com.coflnet.gui.cofl.CoflColConfig;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class FlipHud {
    public static final int WIDTH = 214;
    public static final int HEIGHT = 67;

    private static final AtomicBoolean PARSE_FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicInteger SESSION = new AtomicInteger();
    private static final Object STATE_LOCK = new Object();
    private static volatile State state;
    private static State preview;

    private FlipHud() {
    }

    public static void capture(String json) {
        if (json == null || json.length() > FlipHudData.MAX_PAYLOAD_LENGTH) {
            logParseFailure(new IllegalArgumentException("flip payload exceeded the size limit"));
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        int session = SESSION.incrementAndGet();
        FlipHudIconLoader.cancel();
        client.execute(() -> {
            if (session != SESSION.get()) {
                return;
            }
            try {
                FlipHudData data = FlipHudData.parse(json);
                State previous;
                synchronized (STATE_LOCK) {
                    if (session != SESSION.get()) {
                        return;
                    }
                    previous = state;
                    state = createState(data, new ItemStack(Items.GOLD_INGOT), "received");
                }
                releaseTexture(client, previous);
                requestIcon(client, data, session);
            } catch (RuntimeException exception) {
                logParseFailure(exception);
            }
        });
    }

    public static void markOpening(String id) {
        String normalizedId = FlipHudData.normalizeAuctionId(id);
        if (normalizedId.isBlank()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        int session = SESSION.get();
        client.execute(() -> {
            if (session != SESSION.get()) {
                return;
            }
            synchronized (STATE_LOCK) {
                State current = state;
                if (session == SESSION.get()
                        && current != null
                        && normalizedId.equals(current.data.id())) {
                    state = current.withStatus("opening");
                }
            }
        });
    }

    public static void markOpeningCommand(String command) {
        String id = FlipHudData.auctionIdFromCommand(command);
        if (!id.isBlank()) {
            markOpening(id);
        }
    }

    public static void observeCommand(String command) {
        if (FlipHudData.changesBackendSession(command)) {
            clear();
            return;
        }
        markOpeningCommand(command);
    }

    public static void observeGameMessage(String message) {
        String status = FlipHudData.statusFromGameMessage(message);
        if (status.isBlank()) {
            return;
        }
        synchronized (STATE_LOCK) {
            State current = state;
            if (current != null && isActive(current.status)) {
                state = current.withStatus(status);
            }
        }
    }

    public static void observeAuctionSlot(int containerId, int slot, ItemStack item) {
        Minecraft client = Minecraft.getInstance();
        if (!isCurrentAuctionContainer(client, containerId) || item == null || item.isEmpty()) {
            return;
        }
        boolean confirmation = isConfirmationScreen(client);
        State previous = null;
        synchronized (STATE_LOCK) {
            State current = state;
            if (current == null || !isActive(current.status)) {
                return;
            }
            if (!confirmation && slot == 13) {
                previous = current;
                String status = "opening".equals(current.status) ? "opened" : current.status;
                state = new State(current.data, item.copy(), null, true, status, current.price, current.profit);
            } else if (!confirmation && slot == 31) {
                state = current.withStatus(statusFromActionItem(item));
            } else if (confirmation && slot == 11 && "Confirm".equals(itemName(item))) {
                state = current.withStatus("confirming");
            }
        }
        releaseTexture(client, previous);
    }

    public static void observeAuctionContainer(int containerId) {
        Minecraft client = Minecraft.getInstance();
        if (!isCurrentAuctionContainer(client, containerId)
                || !(client.player.containerMenu instanceof ChestMenu menu)) {
            return;
        }
        boolean confirmation = isConfirmationScreen(client);
        if (!confirmation) {
            observeAuctionSlot(containerId, 13, menu.getContainer().getItem(13));
            observeAuctionSlot(containerId, 31, menu.getContainer().getItem(31));
        } else {
            observeAuctionSlot(containerId, 11, menu.getContainer().getItem(11));
        }
    }

    public static void clear() {
        Minecraft client = Minecraft.getInstance();
        State previous;
        synchronized (STATE_LOCK) {
            SESSION.incrementAndGet();
            previous = state;
            state = null;
        }
        FlipHudIconLoader.cancel();
        if (client != null && previous != null && previous.texture != null) {
            client.execute(() -> releaseTexture(client, previous));
        }
    }

    public static void render(GuiGraphicsExtractor context) {
        if (!FlipHudManager.isEnabled() || state == null) {
            return;
        }
        int x = FlipHudManager.getX(context.guiWidth(), WIDTH);
        int y = FlipHudManager.getY(context.guiHeight(), HEIGHT);
        renderAt(context, x, y, false);
    }

    public static void renderAt(GuiGraphicsExtractor context, int x, int y, boolean preview) {
        boolean previewing = preview && state == null;
        State current = previewing ? previewState() : state;
        if (current == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        FlipHudData data = current.data;

        RenderUtils.drawRect(context, x, y, WIDTH, HEIGHT, CoflColConfig.BACKGROUND_PRIMARY);
        RenderUtils.drawRect(context, x, y, 3, HEIGHT, CoflColConfig.CONFIRM);
        if (current.texture != null) {
            context.blit(RenderPipelines.GUI_TEXTURED, current.texture, x + 9, y + 20,
                    0.0F, 0.0F, 16, 16, 16, 16);
        } else {
            RenderUtils.drawItemStack(context, current.icon, x + 9, y + 20, 1);
        }

        String name = data.count() > 1 ? data.count() + "x " + data.itemName() : data.itemName();
        RenderUtils.drawString(context, "§bskycofl flip", x + 8, y + 6, CoflColConfig.TEXT_PRIMARY);
        RenderUtils.drawString(context, trim(font, name, 174), x + 31, y + 19, CoflColConfig.TEXT_PRIMARY);
        RenderUtils.drawString(context, trim(font, current.price, 174), x + 31, y + 30, CoflColConfig.TEXT_PRIMARY);
        if (!current.profit.isBlank()) {
            RenderUtils.drawString(context, trim(font, current.profit, 174), x + 31, y + 41,
                    CoflColConfig.TEXT_PRIMARY);
        }

        String currentStatus = current.status;
        if (!previewing && !isTerminal(currentStatus)
                && data.endsAt() > 0L
                && System.currentTimeMillis() >= data.endsAt()) {
            currentStatus = "expired";
        }
        String status = FlipHudData.displayLabel(currentStatus) + ", "
                + (previewing ? "0s ago" : age(data.receivedAt()));
        if (!data.finder().isBlank()) {
            status = FlipHudData.displayLabel(data.finder()) + ", " + status;
        }
        RenderUtils.drawString(context, trim(font, status, WIDTH - 16), x + 8, y + 54, 0xFF8E99A8);
    }

    private static void requestIcon(Minecraft client, FlipHudData data, int session) {
        FlipHudIconLoader.load(data.tag(), image ->
                client.execute(() -> installIcon(client, data, session, image)));
    }

    private static void installIcon(Minecraft client, FlipHudData data, int session, NativeImage image) {
        DynamicTexture texture;
        try {
            texture = new DynamicTexture(() -> "skycofl flip icon", image);
        } catch (RuntimeException exception) {
            image.close();
            logParseFailure(exception);
            return;
        }
        State previous;
        try {
            synchronized (STATE_LOCK) {
                State current = state;
                if (session != SESSION.get()
                        || current == null
                        || current.itemFromContainer
                        || !data.id().equals(current.data.id())) {
                    texture.close();
                    return;
                }
                Identifier textureId = textureId(data, session);
                client.getTextureManager().register(textureId, texture);
                previous = current;
                state = new State(current.data, current.icon, textureId, false,
                        current.status, current.price, current.profit);
            }
        } catch (RuntimeException exception) {
            texture.close();
            logParseFailure(exception);
            return;
        }
        releaseTexture(client, previous);
    }

    private static Identifier textureId(FlipHudData data, int session) {
        String key = data.tag() + "|" + data.id() + "|" + session;
        UUID id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
        return Identifier.fromNamespaceAndPath("coflnet", "flip_icon/" + id);
    }

    private static State previewState() {
        if (preview != null) {
            return preview;
        }
        FlipHudData data = new FlipHudData("", "preview item", 1, 12_500_000L,
                16_000_000L, "sniper", "EMERALD", "", 0L, System.currentTimeMillis());
        preview = createState(data, new ItemStack(Items.EMERALD), "received");
        return preview;
    }

    private static State createState(FlipHudData data, ItemStack icon, String status) {
        FlipHudData.PriceLines lines = FlipHudData.priceLines(data.cost(), data.target());
        return new State(data, icon, null, false, status, lines.price(), lines.profit());
    }

    private static String statusFromActionItem(ItemStack item) {
        if (item.getItem() == Items.BED.red()) {
            return "waiting";
        }
        String name = itemName(item);
        String lore = itemLore(item);
        if ("Buy Item Right Now".equals(name)) {
            if (lore.contains("Cannot afford bid!")) {
                return "insufficient coins";
            }
            if (lore.contains("Click to purchase!")) {
                return "ready";
            }
        }
        if ("Collect Auction".equals(name)
                && lore.contains("Someone else purchased the item")) {
            return "sold";
        }
        return "";
    }

    private static String itemName(ItemStack item) {
        Component customName = item.getCustomName();
        return customName == null ? "" : customName.getString();
    }

    private static String itemLore(ItemStack item) {
        var lore = item.get(DataComponents.LORE);
        if (lore == null) {
            return "";
        }
        StringBuilder value = new StringBuilder();
        for (Component line : lore.lines()) {
            value.append(line.getString()).append('\n');
        }
        return value.toString();
    }

    private static boolean isCurrentAuctionContainer(Minecraft client, int containerId) {
        if (client == null
                || client.player == null
                || client.player.containerMenu == null
                || client.player.containerMenu.containerId != containerId
                || !(client.player.containerMenu instanceof ChestMenu menu)) {
            return false;
        }
        if (client.gui.screen() instanceof BinGUI) {
            int size = menu.getContainer().getContainerSize();
            return size == 27 || size == 54;
        }
        if (client.gui.screen() instanceof ContainerScreen screen) {
            return BinGUI.isAuctionInit(screen) || BinGUI.isAuctionConfirming(screen);
        }
        return false;
    }

    private static boolean isConfirmationScreen(Minecraft client) {
        if (client.gui.screen() instanceof ContainerScreen screen) {
            return BinGUI.isAuctionConfirming(screen);
        }
        return client.player != null
                && client.player.containerMenu instanceof ChestMenu menu
                && menu.getContainer().getContainerSize() == 27;
    }

    private static boolean isActive(String status) {
        return !isTerminal(status)
                && ("opening".equals(status)
                || "opened".equals(status)
                || "ready".equals(status)
                || "waiting".equals(status)
                || "confirming".equals(status)
                || "insufficient coins".equals(status));
    }

    private static boolean isTerminal(String status) {
        return "bought".equals(status)
                || "sold".equals(status)
                || "unavailable".equals(status)
                || "failed".equals(status)
                || "expired".equals(status);
    }

    private static void releaseTexture(Minecraft client, State value) {
        if (value != null && value.texture != null) {
            client.getTextureManager().release(value.texture);
        }
    }

    private static String trim(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    private static String age(long receivedAt) {
        long seconds = Math.max(0L, (System.currentTimeMillis() - receivedAt) / 1000L);
        return seconds + "s ago";
    }

    private static void logParseFailure(RuntimeException exception) {
        if (PARSE_FAILURE_LOGGED.compareAndSet(false, true)) {
            System.out.println("Could not update flip HUD: " + exception);
        }
    }

    private record State(
            FlipHudData data,
            ItemStack icon,
            Identifier texture,
            boolean itemFromContainer,
            String status,
            String price,
            String profit) {
        private State withStatus(String value) {
            return value == null || value.isBlank()
                    ? this
                    : new State(data, icon, texture, itemFromContainer, value, price, profit);
        }
    }
}
