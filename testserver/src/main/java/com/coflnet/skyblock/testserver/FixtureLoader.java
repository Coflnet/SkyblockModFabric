package com.coflnet.skyblock.testserver;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Set;

public final class FixtureLoader {
    private static final String MENU_FILE = "menu.json";
    private static final long MAX_BYTES = 1_048_576;

    private FixtureLoader() {
    }

    public static FixtureMenu selectedOrDefault(HolderLookup.Provider registries, String scenarioId) {
        var configured = System.getProperty("skycofl.fixtures");
        String selected = ScenarioHostContract.selectedScenario();
        if (configured == null || configured.isBlank() || !ScenarioHostContract.fixtureSelected()
                || (selected != null && !selected.equals(scenarioId))) {
            return defaults(scenarioId);
        }
        Path directory = Path.of(configured).normalize();
        Path requested = directory.resolve(MENU_FILE);
        validateStagedPath(directory, requested);
        try {
            byte[] bytes = Files.readAllBytes(requested);
            if (bytes.length > MAX_BYTES) throw new IllegalStateException("staged menu.json exceeds 1 MiB");
            return parseConverted(registries,
                    new StringReader(new String(bytes, StandardCharsets.UTF_8)), sha256(bytes));
        } catch (IOException exception) {
            throw new IllegalStateException("unable to read staged menu.json", exception);
        }
    }

    static FixtureMenu parseConverted(HolderLookup.Provider registries, Reader input, String digest) {
        return parseConverted(input, digest, encoded -> decodeItemStack(registries, encoded));
    }

    static FixtureMenu parseConverted(Reader input, String digest, StackDecoder decoder) {
        try {
            JsonElement document = JsonParser.parseReader(input);
            if (!document.isJsonObject()) throw new IllegalStateException("converted menu must be a JSON object");
            JsonObject root = document.getAsJsonObject();
            if (!root.keySet().equals(Set.of("name", "items"))) {
                throw new IllegalStateException("converted menu must contain exactly name and items");
            }
            if (!root.get("name").isJsonPrimitive()) {
                throw new IllegalStateException("converted menu name must be text");
            }
            String name = root.get("name").getAsString();
            if (name.isBlank() || name.length() > 128) {
                throw new IllegalStateException("converted menu name must contain 1..128 characters");
            }
            if (!root.get("items").isJsonArray()
                    || root.getAsJsonArray("items").isEmpty()
                    || root.getAsJsonArray("items").size() > 54) {
                throw new IllegalStateException("converted menu items must contain 1..54 slots");
            }
            var items = new ArrayList<ItemStack>();
            for (JsonElement encoded : root.getAsJsonArray("items")) {
                if (encoded == null || encoded.isJsonNull()) {
                    items.add(ItemStack.EMPTY);
                    continue;
                }
                ItemStack stack = decoder.decode(encoded);
                validateConvertedStack(stack);
                items.add(stack);
            }
            while (items.size() < 54) items.add(ItemStack.EMPTY);
            return new FixtureMenu(name, items, digest);
        } catch (JsonSyntaxException exception) {
            throw new IllegalStateException("invalid converted menu.json", exception);
        }
    }

    private static ItemStack decodeItemStack(HolderLookup.Provider registries, JsonElement encoded) {
        var errors = new ArrayList<String>();
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        return ItemStack.CODEC.parse(ops, encoded).resultOrPartial(errors::add)
                .orElseThrow(() -> new IllegalStateException(
                        "invalid ItemStack.CODEC slot: " + String.join("; ", errors)));
    }

    private static void validateConvertedStack(ItemStack stack) {
        if (stack.isEmpty()) return;
        if (stack.getItem() != Items.PAPER) {
            throw new IllegalStateException("converted menu item must use the minecraft:paper placeholder");
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            throw new IllegalStateException("converted minecraft:paper item lacks minecraft:custom_data");
        }
        CompoundTag tag = customData.copyTag();
        if (!tag.contains("hypixel_tag") && !tag.contains("hypixel_item_id")) {
            throw new IllegalStateException(
                    "converted minecraft:paper item lacks hypixel_tag or hypixel_item_id");
        }
    }

    private static void validateStagedPath(Path directory, Path requested) {
        if (Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(requested)
                || !Files.isRegularFile(requested, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("skycofl.fixtures must contain only the staged menu.json");
        }
        try (var entries = Files.list(directory)) {
            if (!entries.allMatch(requested::equals)) {
                throw new IllegalStateException("skycofl.fixtures must contain only the staged menu.json");
            }
            if (Files.size(requested) > MAX_BYTES) {
                throw new IllegalStateException("staged menu.json exceeds 1 MiB");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("unable to inspect staged menu.json", exception);
        }
    }

    static ItemStack skyblockMenuItem() {
        var stack = named(Items.NETHER_STAR, "SkyBlock Menu");
        stack.set(DataComponents.CUSTOM_DATA, skyblockMenuData());
        return stack;
    }

    static CustomData skyblockMenuData() {
        var tag = new CompoundTag();
        tag.putString("id", "SKYBLOCK_MENU");
        return CustomData.of(tag);
    }

    private static FixtureMenu defaults(String scenarioId) {
        String title = switch (scenarioId) {
            case "ender-chest-sequence" -> "Ender Chest (1/9)";
            case "bazaar-menu" -> "Bazaar ➜ Products";
            case "bazaar-orders" -> "Co-op Bazaar Orders";
            case "auction-house-menu" -> "Auction House";
            case "trade-divider" -> "You     Scenario Partner";
            default -> "SkyBlock Menu";
        };
        var items = new ArrayList<ItemStack>();
        for (int slot = 0; slot < 54; slot++) items.add(ItemStack.EMPTY);
        if (scenarioId.equals("ender-chest-sequence")) populateEnderChest(items);
        if (scenarioId.equals("bazaar-menu")) items.set(13, named(Items.EMERALD, "Bazaar product"));
        if (scenarioId.equals("bazaar-orders")) items.set(13, bazaarOrder());
        if (scenarioId.equals("auction-house-menu")) items.set(13, named(Items.GOLD_INGOT, "Auction listing"));
        if (scenarioId.equals("trade-divider")) {
            for (int slot : new int[]{4, 13, 22, 31, 40}) {
                items.set(slot, named(registryItem("black_stained_glass_pane"), "Trade divider"));
            }
            items.set(0, named(Items.DIAMOND, "Offered item"));
        }
        return new FixtureMenu(title, items, "default:" + scenarioId);
    }

    private static void populateEnderChest(ArrayList<ItemStack> items) {
        items.set(0, named(Items.BARRIER, "Close"));
        items.set(1, named(Items.ARROW, "Back to Storage"));
        for (int slot = 2; slot <= 6; slot++) {
            items.set(slot, named(registryItem("gray_stained_glass_pane"), "Navigation filler"));
        }
        items.set(7, named(registryItem("green_stained_glass_pane"), "Previous Storage Page"));
        items.set(8, named(registryItem("yellow_stained_glass_pane"), "Next Storage Page"));
        items.set(10, named(Items.ENDER_CHEST, "Storage sample"));
    }

    private static ItemStack bazaarOrder() {
        var stack = named(Items.PAPER, "BUY AGATHA COUPON Order");
        String description = "Order details:";
        var data = new CompoundTag();
        data.putString("Tag", "AGATHA_COUPON");
        data.putString("Description", description);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        stack.set(DataComponents.LORE, new ItemLore(java.util.List.of(
                Component.literal(description),
                Component.literal("Price per unit: 1,250 coins"))));
        return stack;
    }

    private static ItemStack named(Item item, String name) {
        var stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static Item registryItem(String path) {
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(path));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @FunctionalInterface
    interface StackDecoder {
        ItemStack decode(JsonElement encoded);
    }
}
