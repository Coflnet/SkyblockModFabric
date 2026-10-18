package com.coflnet.gui.trade;

import CoflCore.handlers.DescriptionHandler;
import com.coflnet.CoflModClient;
import com.coflnet.CoflModClient.WorthBasis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class TradePriceCache {
    private static final long DEBOUNCE_MS = 75L;
    private static final AtomicLong generation = new AtomicLong();
    private static final AtomicLong sequence = new AtomicLong();
    private static final AtomicBoolean workerRunning = new AtomicBoolean();
    private static final AtomicReference<Request> queued = new AtomicReference<>();

    private static List<PricedItem> prices = List.of();
    private static final com.coflnet.core.TradeUploadGate<ItemStack> uploadGate =
            new com.coflnet.core.TradeUploadGate<>(ItemStack::matches, ItemStack::copy);
    private static final com.coflnet.core.TradeChatHold completionHold = new com.coflnet.core.TradeChatHold();
    private static volatile boolean flush;
    private static long revision;

    private TradePriceCache() {
    }

    public static void clear() {
        generation.incrementAndGet();
        sequence.incrementAndGet();
        queued.set(null);
        prices = List.of();
        uploadGate.reset();
        revision++;
    }

    public static long revision() {
        return revision;
    }

    /** Retry transient failures only while the trade screen remains open. */
    public static void retryIfNeeded(ContainerScreen screen) {
        if (uploadGate.isRetryDue(System.currentTimeMillis())) {
            requestCurrentTrade(screen.getMenu().containerId);
        }
    }

    /** Ignore packets unless they belong to the verified trade menu that is actually open. */
    public static void requestCurrentTrade(int packetContainerId) {
        Minecraft client = Minecraft.getInstance();
        var currentScreen = client.gui.screen();
        ContainerScreen screen = currentScreen instanceof TradeGUI trade ? trade.getBacking()
                : currentScreen instanceof CoinInputGUI coins ? coins.getBacking()
                : currentScreen instanceof ContainerScreen container ? container
                : null;

        if (screen == null
                || client.player == null
                || client.player.containerMenu != screen.getMenu()
                || screen.getMenu().containerId != packetContainerId
                || !CoflModClient.isTradeScreen(screen)) {
            return;
        }
        request(screen);
    }

    /**
     * Every changed offer (either side, coins included) is uploaded so the backend stores the final
     * trade; an unchanged offer is not re-sent. Bursts of slot packets coalesce in the debounce.
     */
    public static void request(ContainerScreen screen) {
        if (screen == null || !CoflModClient.isTradeScreen(screen)) return;
        List<ItemStack> offered = tradeItems(screen.getMenu().getItems());
        if (!uploadGate.shouldUpload(offered, System.currentTimeMillis())) return;

        // Removals must invalidate in-flight responses too. Retain only exact, still-offered quotes.
        long requestSequence = sequence.incrementAndGet();
        queued.set(null);
        prices = prices.stream().filter(price -> containsExact(offered, price.stack)).toList();
        revision++;

        NonNullList<ItemStack> items = copyItems(screen.getMenu().getItems());
        queued.set(new Request(CoflModClient.captureDescriptionRequest(screen.getTitle().getString(), items), items,
                screen.getMenu(), Minecraft.getInstance().player, CoflModClient.reserveDescriptionResponse(),
                generation.get(), requestSequence));
        completionHold.uploadQueued(requestSequence);
        drain();
    }

    /**
     * Holds a "Trade completed" chat line until the queued trade upload is sent (skipping the rest
     * of its debounce), so the backend stores the final trade instead of an earlier window.
     *
     * @return true if the line was held; {@code forward} then runs later on a background thread
     */
    public static boolean holdUntilUploaded(String chat, Runnable forward) {
        long token = completionHold.hold(chat, forward);
        if (token == com.coflnet.core.TradeChatHold.NOT_HELD) return false;
        flush = true;
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(com.coflnet.core.TradeChatHold.MAX_HOLD_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            Runnable line = completionHold.timeout(token);
            if (line != null) line.run();
        });
        return true;
    }

    public static Long worth(ItemStack stack, WorthBasis basis) {
        PricedItem value = findPrice(stack);
        return value == null ? null : switch (basis) {
            case LBIN -> value.lbin;
            case MEDIAN -> value.median;
            case AI_ESTIMATE -> value.aiEstimate;
        };
    }

    /** Offered items must use their own quote, never another same-name item's shared tooltip. */
    public static DescriptionHandler.DescModification[] tooltipData(ItemStack stack, String stackId) {
        if (!containsExact(uploadGate.lastOffered(), stack)) return CoflModClient.getMappedTooltipData(stackId);
        PricedItem price = findPrice(stack);
        return price == null ? null : price.tips;
    }

    private static PricedItem findPrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (PricedItem price : prices) {
            if (ItemStack.matches(price.stack, stack)) return price;
        }
        return null;
    }

    public static Long stackWorth(ItemStack stack, WorthBasis basis) {
        Long coins = CoflModClient.parseCoinStack(stack);
        if (coins != null) {
            return coins;
        }
        Long unitWorth = worth(stack, basis);
        return unitWorth == null ? null : unitWorth * stack.getCount();
    }

    /** Shared valuation used by the overlay, coin suggestions, and diagnostics. */
    public static SideValue valueSlots(Container container, int[] slots, WorthBasis basis, boolean includeCoins) {
        long total = 0L;
        int unpriced = 0;
        for (int slot : slots) {
            if (slot < 0 || slot >= container.getContainerSize()) {
                continue;
            }
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Long coins = CoflModClient.parseCoinStack(stack);
            if (coins != null) {
                if (includeCoins) {
                    total += coins;
                }
                continue;
            }
            Long worth = stackWorth(stack, basis);
            if (worth == null) {
                unpriced++;
            } else {
                total += worth;
            }
        }
        return new SideValue(total, unpriced);
    }

    private static void drain() {
        if (!workerRunning.compareAndSet(false, true)) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                Request request;
                while ((request = takeDebouncedRequest()) != null) {
                    load(request);
                }
            } finally {
                workerRunning.set(false);
                if (queued.get() != null) {
                    drain();
                }
            }
        });
    }

    private static Request takeDebouncedRequest() {
        Request request = queued.getAndSet(null);
        while (request != null) {
            try {
                if (!flush) Thread.sleep(DEBOUNCE_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return request;
            }
            Request newer = queued.getAndSet(null);
            if (newer == null) {
                return request;
            }
            request = newer;
        }
        return null;
    }

    private static void load(Request request) {
        try {
            upload(request);
        } finally {
            completionHold.uploadFinished(request.sequence).forEach(Runnable::run);
            flush = completionHold.isHolding();
        }
    }

    private static void upload(Request request) {
        try {
            if (!isCurrent(request)) return;
            var tips = request.description.fetch();
            List<PricedItem> loaded = new ArrayList<>();
            readPrices(loaded, request.items, tips, CoflModClient.TRADE_YOUR_SLOTS);
            readPrices(loaded, request.items, tips, CoflModClient.TRADE_THEIR_SLOTS);
            Minecraft.getInstance().execute(() -> {
                if (isCurrent(request) && Minecraft.getInstance().player == request.player
                        && request.player.containerMenu == request.menu) {
                    if (CoflModClient.isDescriptionResponseCurrent(request.displaySequence)) {
                        com.coflnet.core.DescriptionState.apply(request.description, tips);
                        CoflModClient.onDescriptionsApplied(request.description.title());
                    }
                    prices = List.copyOf(loaded);
                    revision++;
                }
            });
        } catch (Exception exception) {
            System.out.println("[trade] description refresh failed, " + exception);
            Minecraft.getInstance().execute(() -> {
                if (isCurrent(request)) uploadGate.failed(System.currentTimeMillis());
            });
        }
    }

    private static void readPrices(List<PricedItem> result, List<ItemStack> items,
                                   DescriptionHandler.DescModification[][] descriptions, int[] slots) {
        for (int slot : slots) {
            if (slot >= items.size()) continue;
            ItemStack stack = items.get(slot);
            if (!shouldPrice(stack)) continue;
            var tips = descriptions[slot];
            String[] lines = tips == null ? null : java.util.Arrays.stream(tips)
                    .map(tip -> tip == null ? null : tip.value).toArray(String[]::new);
            Long lbin = com.coflnet.core.TradeValuation.parseWorthFromTips(lines,
                    com.coflnet.core.TradeValuation.WorthBasis.LBIN, stack.getCount());
            Long median = com.coflnet.core.TradeValuation.parseWorthFromTips(lines,
                    com.coflnet.core.TradeValuation.WorthBasis.MEDIAN, stack.getCount());
            Long aiEstimate = com.coflnet.core.TradeValuation.parseWorthFromTips(lines,
                    com.coflnet.core.TradeValuation.WorthBasis.AI_ESTIMATE, stack.getCount());
            result.add(new PricedItem(stack, lbin, median, aiEstimate, tips));
        }
    }

    private static boolean shouldPrice(ItemStack stack) {
        return stack != null && !stack.isEmpty() && CoflModClient.parseCoinStack(stack) == null;
    }

    private static boolean isCurrent(Request request) {
        return request.generation == generation.get() && request.sequence == sequence.get();
    }

    private static NonNullList<ItemStack> copyItems(List<ItemStack> items) {
        NonNullList<ItemStack> copy = NonNullList.create();
        for (ItemStack stack : items) {
            copy.add(stack.copy());
        }
        return copy;
    }

    private static List<ItemStack> tradeItems(List<ItemStack> items) {
        List<ItemStack> result = new ArrayList<>(
                CoflModClient.TRADE_YOUR_SLOTS.length + CoflModClient.TRADE_THEIR_SLOTS.length);
        appendSlots(result, items, CoflModClient.TRADE_YOUR_SLOTS);
        appendSlots(result, items, CoflModClient.TRADE_THEIR_SLOTS);
        return List.copyOf(result);
    }

    private static void appendSlots(List<ItemStack> result, List<ItemStack> items, int[] slots) {
        for (int slot : slots) {
            result.add(slot < items.size() ? items.get(slot) : ItemStack.EMPTY);
        }
    }

    private static boolean containsExact(List<ItemStack> items, ItemStack candidate) {
        return items.stream().anyMatch(item -> ItemStack.matches(item, candidate));
    }

    public record SideValue(long total, int unpriced) {
    }

    private record Request(
            com.coflnet.core.DescriptionRequest description,
            NonNullList<ItemStack> items,
            net.minecraft.world.inventory.AbstractContainerMenu menu,
            net.minecraft.client.player.LocalPlayer player,
            long displaySequence,
            long generation,
            long sequence) {
    }

    private record PricedItem(ItemStack stack, Long lbin, Long median, Long aiEstimate, DescriptionHandler.DescModification[] tips) {
    }
}
