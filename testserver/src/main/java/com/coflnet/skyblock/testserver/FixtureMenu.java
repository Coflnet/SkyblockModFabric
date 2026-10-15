package com.coflnet.skyblock.testserver;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public record FixtureMenu(String title, List<ItemStack> items, String digest) {
    public FixtureMenu {
        items = List.copyOf(items);
    }
}
