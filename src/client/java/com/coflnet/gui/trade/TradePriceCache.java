package com.coflnet.gui.trade;

import CoflCore.handlers.DescriptionHandler;
import com.coflnet.CoflModClient;
import com.coflnet.CoflModClient.WorthBasis;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class TradePriceCache {
    private static final AtomicLong generation = new AtomicLong();
    private static final AtomicLong requestSequence = new AtomicLong();
    private static final AtomicBoolean workerRunning = new AtomicBoolean();
    private static final AtomicReference<Request> queued = new AtomicReference<>();
    private static volatile Map<String, Prices> prices = Map.of();
    private static volatile List<ItemStack> tradeItems = List.of();

    private TradePriceCache() {
    }

    public static void clear() {
        generation.incrementAndGet();
        requestSequence.incrementAndGet();
        queued.set(null);
        prices = Map.of();
        tradeItems = List.of();
    }

    public static void request(ContainerScreen screen) {
        if (screen == null || !CoflModClient.isTradeScreenByTitle(screen)) {
            return;
        }
        NonNullList<ItemStack> snapshot = NonNullList.create();
        for (ItemStack stack : screen.getMenu().getItems()) {
            snapshot.add(stack.copy());
        }
        List<ItemStack> currentTradeItems = tradeItems(snapshot);
        if (!sameItems(currentTradeItems, tradeItems)) {
            tradeItems = currentTradeItems;
            prices = Map.of();
        }
        long sequence = requestSequence.incrementAndGet();
        queued.set(new Request(screen.getTitle().getString(), snapshot, generation.get(), sequence));
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

    private static void drain() {
        if (!workerRunning.compareAndSet(false, true)) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                Request request;
                while ((request = queued.getAndSet(null)) != null) {
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

    private static void load(Request request) {
        try {
            CoflModClient.loadDescriptionsForItems(request.title, request.items);
            if (!isCurrent(request)) {
                return;
            }
            Map<String, Prices> updated = new HashMap<>();
            for (ItemStack stack : request.items) {
                if (stack.isEmpty()) {
                    continue;
                }
                String id = CoflModClient.getIdFromStack(stack);
                DescriptionHandler.DescModification[] tips = DescriptionHandler.getTooltipData(id);
                Long lbin = CoflModClient.parseWorthFromTips(tips, WorthBasis.LBIN);
                Long median = CoflModClient.parseWorthFromTips(tips, WorthBasis.MEDIAN);
                if (lbin != null || median != null) {
                    updated.put(id, new Prices(value(lbin), value(median)));
                }
            }
            if (isCurrent(request)) {
                prices = Map.copyOf(updated);
            }
        } catch (Throwable throwable) {
            System.out.println("[trade] description refresh failed, " + throwable);
        }
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }

    private static boolean isCurrent(Request request) {
        return request.generation == generation.get() && request.sequence == requestSequence.get();
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
            if (slot < items.size()) {
                result.add(items.get(slot).copy());
            } else {
                result.add(ItemStack.EMPTY);
            }
        }
    }

    private static boolean sameItems(List<ItemStack> first, List<ItemStack> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int i = 0; i < first.size(); i++) {
            if (!ItemStack.matches(first.get(i), second.get(i))) {
                return false;
            }
        }
        return true;
    }

    private record Request(String title, NonNullList<ItemStack> items, long generation, long sequence) {
    }

    private record Prices(long lbin, long median) {
    }
}
