package com.coflnet.gui;

import CoflCore.configuration.Config;
import com.coflnet.CoflModClient;
import com.coflnet.config.BazaarAssistManager;
import com.coflnet.core.BazaarBook;
import com.coflnet.core.BazaarFillEstimate;
import com.coflnet.core.BazaarGuide;
import com.coflnet.core.BazaarOrderLore;
import com.coflnet.core.FetchThrottle;
import com.coflnet.core.MenuClassifier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local Bazaar order guidance: outbid marks and fill estimates on own orders, and the next-click
 * outline while placing or relisting an order. Relevant menus are rescanned on the client tick only
 * after their content, a book or the guide changed; snapshots are fetched at most once a minute per product.
 */
public final class BazaarAssist {
    private BazaarAssist() {}

    /** Rescans an unchanged menu this often, so snapshots refresh and an idle relist expires. */
    private static final long PERIODIC_RESCAN_MS = 60_000;
    private static final long SNAPSHOT_TTL_MS = 60_000;
    private static final long TAG_RETRY_MS = 10 * 60_000;
    private static final int GUIDE_OUTLINE = 0xFF55FFFF;
    private static final int RELIST_FILL = 0x30FF5555;
    private static final int OUTBID_FILL = 0x28FFAA00;
    private static final String USER_AGENT = "SkyCofl-Fabric";

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final FetchThrottle TAG_FETCHES = new FetchThrottle(TAG_RETRY_MS);
    private static final FetchThrottle SNAPSHOT_FETCHES = new FetchThrottle(SNAPSHOT_TTL_MS);
    private static final Map<String, String> TAGS = new ConcurrentHashMap<>();
    private static final Map<String, BazaarBook> BOOKS = new ConcurrentHashMap<>();
    private static final BazaarGuide GUIDE = new BazaarGuide();

    /** The last scan of the open menu; replaced as a whole on the client thread. */
    private record Scan(AbstractContainerMenu menu, boolean guidance, boolean relist, boolean goalGuides,
                        ItemStack[] stacks, BazaarOrderLore.Order[] orders,
                        BazaarFillEstimate.Estimate[] estimates, int outlineSlot) {}

    private static volatile Scan scan = null;
    /** Set when the menu content, a book or the guide changed; the next tick rescans. */
    private static volatile boolean dirty = true;
    private static volatile AbstractContainerMenu highlightMenu = null;
    private static volatile boolean backendHighlight = false;
    private static long lastScanAt = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(BazaarAssist::tick);
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> appendTooltip(stack, lines));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            long now = System.currentTimeMillis();
            BazaarGuide.Stage before = GUIDE.stage(now);
            GUIDE.chat(message.getString(), now);
            if (GUIDE.stage(now) != before) invalidate();
        });
    }

    /** Called when the open container's content changed. */
    public static void invalidate() {
        dirty = true;
    }

    /** Called by the slot renderer each frame: whether a backend (goal) highlight is visible in this menu. */
    public static void reportBackendHighlight(AbstractContainerMenu menu, boolean present) {
        if (menu == highlightMenu && present == backendHighlight) return;
        highlightMenu = menu;
        backendHighlight = present;
        invalidate();
    }

    /** @return the slot to outline as the next click in this menu, or -1 */
    public static int outlineSlot(AbstractContainerMenu menu) {
        Scan current = unsuppressedScan(menu);
        return current != null ? current.outlineSlot : -1;
    }

    public static int outlineColor() {
        return GUIDE_OUTLINE;
    }

    /** @return the translucent mark of an own outbid order, or 0 */
    public static int markColor(AbstractContainerMenu menu, int slotIndex) {
        Scan current = unsuppressedScan(menu);
        if (current == null || slotIndex >= current.estimates.length) return 0;
        BazaarFillEstimate.Estimate estimate = current.estimates[slotIndex];
        if (estimate == null || !estimate.outbid()) return 0;
        return estimate.suggestRelist() ? RELIST_FILL : OUTBID_FILL;
    }

    public static void onSlotClicked(AbstractContainerMenu menu, Slot slot) {
        Scan current = unsuppressedScan(menu);
        if (slot == null || current == null || !current.guidance) return;
        int index = menu.slots.indexOf(slot);
        if (index < 0 || index >= current.orders.length || current.orders[index] == null) return;
        GUIDE.orderClicked(current.orders[index], current.estimates[index], false, System.currentTimeMillis());
        invalidate();
    }

    /** The amount the local relist guide expects on the open sign, or null. */
    public static String expectedSignValue(String fourthLine) {
        if (!BazaarAssistManager.isGuidanceEnabled()) return null;
        return GUIDE.expectedSignValue(fourthLine, System.currentTimeMillis());
    }

    private static void tick(Minecraft client) {
        boolean guidance = BazaarAssistManager.isGuidanceEnabled();
        boolean relist = BazaarAssistManager.isRelistEnabled();
        if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen) || (!guidance && !relist)
                || !BazaarGuide.isRelevantMenu(screen.getTitle().getString())) {
            scan = null;
            return;
        }
        long now = System.currentTimeMillis();
        Scan current = scan;
        boolean unchanged = current != null && current.menu == screen.getMenu() && current.guidance == guidance
                && current.relist == relist && current.goalGuides == goalGuides(current.menu, now)
                && !dirty && now - lastScanAt < PERIODIC_RESCAN_MS;
        if (unchanged) return;
        dirty = false;
        lastScanAt = now;
        scan = scanMenu(screen, guidance, relist, now);
    }

    private static Scan scanMenu(AbstractContainerScreen<?> screen, boolean guidance, boolean relist, long now) {
        AbstractContainerMenu menu = screen.getMenu();
        String title = screen.getTitle().getString();
        boolean goalGuides = goalGuides(menu, now);
        int size = menu.slots.size();
        ItemStack[] stacks = new ItemStack[size];
        BazaarOrderLore.Order[] orders = new BazaarOrderLore.Order[size];
        BazaarFillEstimate.Estimate[] estimates = new BazaarFillEstimate.Estimate[size];
        List<String> names = new ArrayList<>(size);
        int containerSlots = 0;
        boolean ordersMenu = MenuClassifier.isBazaarOrders(title);
        for (int i = 0; i < size; i++) {
            Slot slot = menu.slots.get(i);
            stacks[i] = slot.getItem();
            names.add(stacks[i].isEmpty() ? null : stacks[i].getHoverName().getString());
            if (slot.container instanceof Inventory) continue;
            containerSlots++;
            if (!ordersMenu || stacks[i].isEmpty()) continue;
            orders[i] = BazaarOrderLore.parse(names.get(i), loreLines(stacks[i]));
            if (relist && !goalGuides && orders[i] != null) {
                estimates[i] = BazaarFillEstimate.estimate(orders[i], bookFor(orders[i].itemName(), now));
            }
        }
        int outline = guidance ? GUIDE.nextSlot(title, names, orders, containerSlots, goalGuides, now) : -1;
        return new Scan(menu, guidance, relist, goalGuides, stacks, orders, estimates, outline);
    }

    /** Checked at scan and at use time, so a goal fill armed on an unchanged menu wins at once. */
    private static boolean goalGuides(AbstractContainerMenu menu, long now) {
        return BazaarGuide.goalGuidesMenu(highlightMenu == menu && backendHighlight,
                CoflModClient.pendingSignFill, CoflModClient.pendingSignFillExpiresAt, now);
    }

    /** The scan of this menu while no goal step guides it, else null. */
    private static Scan unsuppressedScan(AbstractContainerMenu menu) {
        Scan current = scan;
        if (current == null || current.menu != menu || goalGuides(menu, System.currentTimeMillis())) return null;
        return current;
    }

    private static void appendTooltip(ItemStack stack, List<Component> lines) {
        Scan current = scan;
        if (current == null || stack.isEmpty() || !BazaarAssistManager.isRelistEnabled()) return;
        current = unsuppressedScan(current.menu);
        if (current == null) return;
        for (int i = 0; i < current.stacks.length; i++) {
            if (current.stacks[i] != stack) continue;
            for (String line : BazaarFillEstimate.tooltipLines(current.orders[i], current.estimates[i])) {
                lines.add(Component.literal(line));
            }
            return;
        }
    }

    private static List<String> loreLines(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        for (Component line : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) lines.add(line.getString());
        return lines;
    }

    /** @return the cached book, starting the tag lookup or a snapshot refresh in the background when due */
    private static BazaarBook bookFor(String itemName, long now) {
        String tag = TAGS.get(itemName);
        if (tag == null) {
            if (TAG_FETCHES.tryStart(itemName, now)) fetchTag(itemName);
            return null;
        }
        if (SNAPSHOT_FETCHES.tryStart(tag, now)) fetchSnapshot(tag);
        return BOOKS.get(tag);
    }

    private static void fetchTag(String itemName) {
        String url = Config.BaseUrl + "/api/item/search/" + URLEncoder.encode(itemName, StandardCharsets.UTF_8).replace("+", "%20");
        get(url).whenComplete((body, error) -> {
            String tag = body == null ? null : BazaarBook.tagFromSearch(body, itemName);
            if (tag != null) {
                TAGS.put(itemName, tag);
                invalidate();
            }
            TAG_FETCHES.finish(itemName);
        });
    }

    private static void fetchSnapshot(String tag) {
        get(Config.BaseUrl + "/api/bazaar/" + URLEncoder.encode(tag, StandardCharsets.UTF_8) + "/snapshot")
                .whenComplete((body, error) -> {
                    BazaarBook book = body == null ? null : BazaarBook.parse(body);
                    // An unchanged refresh keeps the scan; only new prices or volumes rescan the menu.
                    if (book != null && !book.equals(BOOKS.put(tag, book))) invalidate();
                    SNAPSHOT_FETCHES.finish(tag);
                });
    }

    private static CompletableFuture<String> get(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", USER_AGENT)
                .GET().build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200 ? response.body() : null);
    }
}
