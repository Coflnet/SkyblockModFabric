package com.coflnet.gui.bazaar;

import com.coflnet.CoflModClient;
import com.coflnet.config.CoflModConfig;
import com.coflnet.core.BazaarChart;
import com.coflnet.core.BazaarGraphTarget;
import com.coflnet.core.BazaarHistoryClient;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public final class BazaarCharts {
    private static final Map<ItemStack, Cached> CACHE = new WeakHashMap<>();
    private record Cached(Component name, ItemLore lore, BazaarChartTooltip chart) { }
    private static KeyMapping graphsKey;
    private static boolean held;
    private BazaarCharts() {
    }

    public static void initialize(KeyMapping.Category category) {
        BazaarHistoryClient.warmup(FabricLoader.getInstance().getConfigDir());
        graphsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "keybinding.coflmod.bazaargraphs", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, category));
    }

    public static void tick(Minecraft client) {
        if (!graphsKey.isUnbound()) {
            var bound = InputConstants.getKey(graphsKey.saveString());
            if (bound.getType() == InputConstants.Type.KEYBOARD && !InputConstants.isKeyDown(bound.getValue())) held = false;
        }
        while (graphsKey.consumeClick()) { }
    }

    public static boolean handleKey(AbstractContainerScreen<?> screen, KeyEvent event, ItemStack hovered) {
        Minecraft client = Minecraft.getInstance();
        if (!graphsKey.matches(event) || conflicts(event)
                || screen.getFocused() instanceof EditBox edit && edit.canConsumeInput()
                || client.player == null || client.gameMode == null || client.getConnection() == null
                || client.player.containerMenu != screen.getMenu() || !screen.getMenu().getCarried().isEmpty()) return false;
        if (held) return true;
        var target = BazaarHistoryClient.resolve(target(hovered));
        if (target == null) return false;
        held = true;
        client.gui.setScreen(new BazaarHistoryScreen(screen, target));
        return true;
    }

    private static boolean conflicts(KeyEvent event) {
        return CoflModClient.bestflipsKeyBinding.matches(event)
                || CoflModClient.uploadItemKeyBinding.matches(event)
                || CoflModClient.openSettingsKeyBinding.matches(event)
                || CoflModClient.additionalKeyBindings.stream().anyMatch(key -> key.matches(event));
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

    public static BazaarChartTooltip chart(ItemStack stack) {
        if (!CoflModConfig.get().readableBazaarCharts || stack.isEmpty()
                || InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT)) return null;
        var window = Minecraft.getInstance().getWindow();
        if (window.getGuiScaledWidth() < 180 || window.getGuiScaledHeight() < 150) return null;
        var name = stack.getHoverName();
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return null;
        Cached previous = CACHE.get(stack);
        if (previous != null && Objects.equals(previous.name(), name) && previous.lore().equals(lore)) return previous.chart();
        if (!BazaarChart.isChart(name.getString())) return null;
        var model = BazaarChart.parse(name.getString(), lore.lines().stream().map(Component::getString).toList());
        String title = BazaarChart.clean(name.getString());
        var chart = model == null ? null : new BazaarChartTooltip(model, title.contains("Buy"), title.contains("Volume"));
        if (CACHE.size() >= 128) CACHE.clear();
        CACHE.put(stack, new Cached(name, lore, chart));
        return chart;
    }
}
