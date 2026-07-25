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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class TradePriceCache {
    private static final long DEBOUNCE_MS = 75L;
    private static final AtomicLong generation = new AtomicLong();
    private static final AtomicLong sequence = new AtomicLong();
    private static final AtomicBoolean workerRunning = new AtomicBoolean();
    private static final AtomicReference<Request> queued = new AtomicReference<>();

    private static volatile Map<String, Prices> prices = Map.of();
    private static final List<ItemStack> seenItems = new ArrayList<>();

    private TradePriceCache() {
    }

    public static void clear() {
        generation.incrementAndGet();
        sequence.incrementAndGet();
        queued.set(null);
        prices = Map.of();
        seenItems.clear();
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

    /** Queue one debounced refresh when a previously unseen exact item enters the offer. */
    public static void request(ContainerScreen screen) {
        if (screen == null || !CoflModClient.isTradeScreen(screen)) {
            return;
        }

        NonNullList<ItemStack> snapshot = copyItems(screen.getMenu().getItems());
        List<ItemStack> offered = tradeItems(snapshot);
        long requestGeneration = generation.get();
        boolean hasNewItem = false;
        for (ItemStack item : offered) {
            if (shouldPrice(item) && !containsExact(seenItems, item)) {
                seenItems.add(item.copy());
                hasNewItem = true;
            }
        }
        if (!hasNewItem) {
            return;
        }
        prices = Map.copyOf(readPrices(offered, false));
        long requestSequence = sequence.incrementAndGet();
        queued.set(new Request(
                screen.getTitle().getString(), snapshot, offered,
                requestGeneration, requestSequence));
        drain();
    }

    public static Long worth(ItemStack stack, WorthBasis basis) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Prices value = prices.get(CoflModClient.getIdFromStack(stack));
        if (value == null) {
            return null;
        }
        long worth = basis == WorthBasis.LBIN ? value.lbin : value.median;
        return worth > 0L ? worth : null;
    }

    public static Long stackWorth(ItemStack stack, WorthBasis basis) {
        Long coins = CoflModClient.parseCoinStack(stack);
        if (coins != null) {
            return coins;
        }
        Long unitWorth = worth(stack, basis);
        if (unitWorth == null) {
            return null;
        }
        int count = stack.getCount();
        return count > 0 && unitWorth > Long.MAX_VALUE / count
                ? Long.MAX_VALUE
                : unitWorth * count;
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
                    total = addClamped(total, coins);
                }
                continue;
            }
            Long worth = stackWorth(stack, basis);
            if (worth == null) {
                unpriced++;
            } else {
                total = addClamped(total, worth);
            }
        }
        return new SideValue(total, unpriced);
    }

    private static long addClamped(long current, long value) {
        if (value <= 0L) {
            return current;
        }
        return current > Long.MAX_VALUE - value
                ? Long.MAX_VALUE
                : current + value;
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
                Thread.sleep(DEBOUNCE_MS);
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
            if (!isCurrent(request)) {
                return;
            }
            CoflModClient.loadDescriptionsForItemsBlocking(request.title, request.items);
            Map<String, Prices> loaded = readPrices(request.offered, true);
            if (isCurrent(request)) {
                prices = Map.copyOf(loaded);
            }
        } catch (Exception exception) {
            System.out.println("[trade] description refresh failed, " + exception);
        }
    }

    private static Map<String, Prices> readPrices(List<ItemStack> items, boolean preferCurrentId) {
        Map<String, Prices> result = new HashMap<>();
        for (ItemStack stack : items) {
            if (!shouldPrice(stack)) {
                continue;
            }
            String id = CoflModClient.getIdFromStack(stack);
            DescriptionHandler.DescModification[] tips = preferCurrentId
                    ? DescriptionHandler.getTooltipData(id)
                    : CoflModClient.getMappedTooltipData(id);
            if (tips == null && preferCurrentId) {
                tips = CoflModClient.getMappedTooltipData(id);
            }
            Long lbin = CoflModClient.parseWorthFromTips(tips, WorthBasis.LBIN);
            Long median = CoflModClient.parseWorthFromTips(tips, WorthBasis.MEDIAN);
            if (lbin != null || median != null) {
                result.put(id, new Prices(value(lbin), value(median)));
            }
        }
        return result;
    }

    private static boolean shouldPrice(ItemStack stack) {
        return stack != null && !stack.isEmpty() && CoflModClient.parseCoinStack(stack) == null;
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
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

    private static List<ItemStack> tradeItems(NonNullList<ItemStack> items) {
        List<ItemStack> result = new ArrayList<>(
                CoflModClient.TRADE_YOUR_SLOTS.length + CoflModClient.TRADE_THEIR_SLOTS.length);
        appendSlots(result, items, CoflModClient.TRADE_YOUR_SLOTS);
        appendSlots(result, items, CoflModClient.TRADE_THEIR_SLOTS);
        return List.copyOf(result);
    }

    private static void appendSlots(List<ItemStack> result, NonNullList<ItemStack> items, int[] slots) {
        for (int slot : slots) {
            result.add(slot < items.size() ? items.get(slot).copy() : ItemStack.EMPTY);
        }
    }

    private static boolean containsExact(List<ItemStack> items, ItemStack candidate) {
        return items.stream().anyMatch(item -> ItemStack.matches(item, candidate));
    }

    public record SideValue(long total, int unpriced) {
    }

    private record Request(
            String title,
            NonNullList<ItemStack> items,
            List<ItemStack> offered,
            long generation,
            long sequence) {
    }

    private record Prices(long lbin, long median) {
    }
}
