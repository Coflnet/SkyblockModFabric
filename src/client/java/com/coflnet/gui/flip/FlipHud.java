package com.coflnet.gui.flip;

import com.coflnet.config.FlipHudManager;
import com.coflnet.gui.RenderUtils;
import com.coflnet.gui.cofl.CoflColConfig;
import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FlipHud {
    public static final int WIDTH = 214;
    public static final int HEIGHT = 56;

    private static final Gson GSON = new Gson();
    private static final AtomicBoolean PARSE_FAILURE_LOGGED = new AtomicBoolean();
    private static volatile State state;

    private FlipHud() {
    }

    public static void capture(String json) {
        final FlipHudData data;
        try {
            data = FlipHudData.parse(json);
        } catch (RuntimeException exception) {
            if (PARSE_FAILURE_LOGGED.compareAndSet(false, true)) {
                System.out.println("Could not parse flip HUD payload: " + exception);
            }
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.execute(() -> state = new State(data, createIcon(data.render()), "received"));
        }
    }

    public static void markOpening(String id) {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.execute(() -> {
                State current = state;
                if (current != null && id != null && id.equals(current.data.id())) {
                    state = new State(current.data, current.icon, "opening");
                }
            });
        }
    }

    public static void clear() {
        state = null;
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
        State current = preview && state == null ? previewState() : state;
        if (current == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        FlipHudData data = current.data;

        RenderUtils.drawRoundedRect(context, x, y, WIDTH, HEIGHT, 4, CoflColConfig.BACKGROUND_PRIMARY);
        RenderUtils.drawRect(context, x, y, 3, HEIGHT, CoflColConfig.CONFIRM);
        RenderUtils.drawItemStack(context, current.icon, x + 9, y + 20, 1);

        String name = data.count() > 1 ? data.count() + "x " + data.itemName() : data.itemName();
        RenderUtils.drawString(context, "§bskycofl flip", x + 8, y + 6, CoflColConfig.TEXT_PRIMARY);
        RenderUtils.drawString(context, trim(font, name, 174), x + 31, y + 19, CoflColConfig.TEXT_PRIMARY);

        String price = data.cost() > 0L ? "buy " + format(data.cost()) : "price unknown";
        long target = data.target() > 0L ? data.target() : data.worth();
        if (target > 0L) {
            price += ", target " + format(target);
        }
        RenderUtils.drawString(context, trim(font, price, 174), x + 31, y + 30, 0xFFB8C0CC);

        String status = current.status + ", " + age(data.receivedAt());
        if (!data.finder().isBlank()) {
            status = data.finder().toLowerCase(Locale.ROOT) + ", " + status;
        }
        RenderUtils.drawString(context, trim(font, status, WIDTH - 16), x + 8, y + 43, 0xFF8E99A8);
    }

    private static State previewState() {
        FlipHudData data = new FlipHudData("", "preview item", 1, 12_500_000L,
                16_000_000L, 16_000_000L, "sniper", "emerald", System.currentTimeMillis());
        return new State(data, new ItemStack(Items.EMERALD), "received");
    }

    private static ItemStack createIcon(String render) {
        if (render != null && !render.isBlank()) {
            String value = render.trim().toLowerCase(Locale.ROOT);
            Identifier id = Identifier.tryParse(value.contains(":") ? value : "minecraft:" + value);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                Item item = BuiltInRegistries.ITEM.getValue(id);
                if (item != null && item != Items.AIR) {
                    return new ItemStack(item);
                }
            }
            if (value.matches("[0-9a-f]{32,64}")) {
                return texturedHead(value);
            }
        }
        return new ItemStack(Items.GOLD_INGOT);
    }

    private static ItemStack texturedHead(String textureHash) {
        String url = "https://textures.minecraft.net/texture/" + textureHash;
        String payload = GSON.toJson(java.util.Map.of("textures", java.util.Map.of("SKIN", java.util.Map.of("url", url))));
        String encoded = Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(textureHash.getBytes(StandardCharsets.UTF_8)), "cofl_flip");
        profile.properties().put("textures", new Property("textures", encoded));
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        return head;
    }

    private static String trim(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    private static String format(long coins) {
        if (coins >= 1_000_000_000L) {
            return String.format(Locale.US, "%.1fb", coins / 1_000_000_000.0);
        }
        if (coins >= 1_000_000L) {
            return String.format(Locale.US, "%.1fm", coins / 1_000_000.0);
        }
        if (coins >= 1_000L) {
            return String.format(Locale.US, "%.1fk", coins / 1_000.0);
        }
        return String.valueOf(coins);
    }

    private static String age(long receivedAt) {
        long seconds = Math.max(0L, (System.currentTimeMillis() - receivedAt) / 1000L);
        return seconds + "s ago";
    }

    private record State(FlipHudData data, ItemStack icon, String status) {
    }
}
