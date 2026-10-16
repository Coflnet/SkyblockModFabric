package com.coflnet.gui.bazaar;

import com.coflnet.CoflModClient;
import com.coflnet.config.CoflModConfig;
import com.coflnet.core.BazaarChart;
import com.coflnet.core.BazaarGraphButton;
import com.coflnet.core.BazaarGraphTarget;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public final class BazaarCharts {
    private static final Map<ItemStack, Cached> CACHE = new WeakHashMap<>();
    private record Cached(Object name, ItemLore lore, BazaarChartTooltip chart) { }
    private static KeyMapping graphsKey;
    private static boolean held;
    private static Pending pending;
    private static final class Pending {
        final BazaarGraphTarget target;
        final Object player, connection, origin;
        final long deadline = System.nanoTime() + 10_000_000_000L;
        boolean selected;
        Pending(BazaarGraphTarget target, Minecraft client, Object origin) {
            this.target = target; player = client.player; connection = client.getConnection(); this.origin = origin;
        }
    }

    private BazaarCharts() { }

    public static void initialize(KeyMapping.Category category) {
        graphsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "keybinding.coflmod.bazaargraphs", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!graphsKey.isUnbound()) {
                var bound = InputConstants.getKey(graphsKey.saveString());
                if (bound.getType() == InputConstants.Type.KEYBOARD && !InputConstants.isKeyDown(bound.getValue())) held = false;
            }
            while (graphsKey.consumeClick()) { }
            navigate(client);
        });
    }

    public static boolean handleKey(AbstractContainerScreen<?> screen, KeyEvent event, ItemStack hovered) {
        Minecraft client = Minecraft.getInstance();
        if (!graphsKey.matches(event) || conflicts(event) || !onHypixel(client)
                || screen.getFocused() instanceof EditBox edit && edit.canConsumeInput()
                || client.player == null || client.gameMode == null || client.getConnection() == null
                || client.player.containerMenu != screen.getMenu() || !screen.getMenu().getCarried().isEmpty()) return false;
        if (held) return true;
        var target = target(hovered);
        if (screen instanceof ContainerScreen chest && graphSlot(chest) >= 0
                && (target == null || contains(chest, target))) {
            held = true; pending = null;
            click(client, chest, graphSlot(chest));
            return true;
        }
        if (target == null) return false;
        held = true;
        pending = new Pending(target, client, screen);
        client.player.connection.sendCommand("bz " + target.query());
        return true;
    }

    private static boolean conflicts(KeyEvent event) {
        return CoflModClient.bestflipsKeyBinding.matches(event)
                || CoflModClient.uploadItemKeyBinding.matches(event)
                || CoflModClient.openSettingsKeyBinding.matches(event)
                || CoflModClient.additionalKeyBindings.stream().anyMatch(key -> key.matches(event));
    }

    private static boolean onHypixel(Minecraft client) {
        var server = client.getCurrentServer();
        if (server == null) return false;
        String host = server.ip.toLowerCase(Locale.ROOT).split(":")[0];
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }

    private static BazaarGraphTarget target(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var custom = stack.get(DataComponents.CUSTOM_DATA);
        var enchants = new HashMap<String, Integer>();
        String id = "";
        if (custom != null) {
            var root = custom.copyTag();
            var extra = root.getCompound("ExtraAttributes").orElse(root);
            id = extra.getStringOr("id", "");
            var data = extra.getCompoundOrEmpty("enchantments");
            for (String name : data.keySet()) enchants.put(name, data.getIntOr(name, 0));
        }
        return BazaarGraphTarget.from(id, stack.getHoverName().getString(), enchants);
    }

    private static int graphSlot(ContainerScreen screen) {
        var container = screen.getMenu().getContainer();
        int size = container.getContainerSize();
        var names = new ArrayList<String>(size);
        for (int i = 0; i < size; i++) names.add(container.getItem(i).getHoverName().getString());
        return BazaarGraphButton.find(size, names);
    }

    private static boolean contains(ContainerScreen screen, BazaarGraphTarget wanted) {
        var container = screen.getMenu().getContainer();
        for (int i = 0; i < container.getContainerSize(); i++) {
            var candidate = target(container.getItem(i));
            // Product-menu icons can carry a contextual ID instead of the traded item's ID.
            if (wanted.matches(candidate) || candidate != null && wanted.query().equalsIgnoreCase(candidate.query())) return true;
        }
        return false;
    }

    private static void navigate(Minecraft client) {
        Pending request = pending;
        if (request == null) return;
        if (System.nanoTime() > request.deadline || client.player != request.player
                || client.getConnection() != request.connection || !onHypixel(client)) { pending = null; return; }
        if (!(client.gui.screen() instanceof ContainerScreen screen) || screen == request.origin) return;
        // A navigation click predicts a carried icon until the server replaces the menu.
        // Wait through that transient state; never issue another click with a carried stack.
        if (client.gameMode == null || client.player.containerMenu != screen.getMenu()
                || !screen.getMenu().getCarried().isEmpty()) return;
        int graph = graphSlot(screen);
        if (graph >= 0) {
            if (!contains(screen, request.target)) return;
            pending = null;
            click(client, screen, graph);
            return;
        }
        String title = BazaarChart.clean(screen.getTitle().getString());
        if (!title.startsWith("Bazaar") || request.selected) return;
        var container = screen.getMenu().getContainer();
        int candidate = -1;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!request.target.matches(target(container.getItem(i)))) continue;
            if (candidate >= 0) return;
            candidate = i;
        }
        if (candidate >= 0) {
            request.selected = true;
            click(client, screen, candidate);
        }
    }

    private static void click(Minecraft client, ContainerScreen screen, int slot) {
        client.gameMode.handleContainerInput(screen.getMenu().containerId, slot, 0, ContainerInput.PICKUP, client.player);
    }

    public static BazaarChartTooltip chart(ItemStack stack) {
        if (!CoflModConfig.get().readableBazaarCharts || stack.isEmpty()
                || InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT)) return null;
        var window = Minecraft.getInstance().getWindow();
        if (window.getGuiScaledWidth() < 180 || window.getGuiScaledHeight() < 150) return null;
        var name = stack.getHoverName();
        if (!BazaarChart.isChart(name.getString())) return null;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return null;
        Cached previous = CACHE.get(stack);
        if (previous != null && Objects.equals(previous.name(), name) && previous.lore().equals(lore)) return previous.chart();
        var model = BazaarChart.parse(name.getString(), lore.lines().stream().map(Component::getString).toList());
        String title = BazaarChart.clean(name.getString());
        var chart = model == null ? null : new BazaarChartTooltip(model, title.contains("Buy"), title.contains("Volume"));
        if (CACHE.size() >= 128) CACHE.clear();
        CACHE.put(stack, new Cached(name, lore, chart));
        return chart;
    }
}
