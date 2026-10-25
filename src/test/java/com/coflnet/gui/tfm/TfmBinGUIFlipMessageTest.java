package com.coflnet.gui.tfm;

import CoflCore.commands.models.ChatMessageData;
import CoflCore.commands.models.FlipData;
import CoflCore.events.OnOpenAuctionGUI;
import com.coflnet.EventSubscribers;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.font.GlyphInfo;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.util.RandomSource;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Builds the real TFM overlay the way the auction screen hook does, without a GL client. */
class TfmBinGUIFlipMessageTest {
    private static Minecraft previousInstance;
    private static Minecraft client;
    private static int lastContainerId;

    @BeforeAll
    static void bootstrap() throws Exception {
        previousInstance = Minecraft.getInstance();
        installClient();
    }

    @AfterAll
    static void restoreClient() throws Exception {
        set(Minecraft.class, null, "instance", previousInstance);
    }

    /** Installs a client with a font and an open screen, which is all the overlay constructor reads. */
    static void installClient() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Holder.Reference<Item> air = BuiltInRegistries.ITEM.getOrThrow(BuiltInRegistries.ITEM.getResourceKey(Items.AIR).orElseThrow());
        if (!air.areComponentsBound()) {
            air.bindComponents(DataComponentMap.EMPTY); // the overlay's empty item slot
        }
        Holder.Reference<Item> sword = BuiltInRegistries.ITEM.getOrThrow(BuiltInRegistries.ITEM.getResourceKey(Items.DIAMOND_SWORD).orElseThrow());
        if (!sword.areComponentsBound()) {
            sword.bindComponents(DataComponentMap.EMPTY); // the auctioned item
        }
        client = allocate(Minecraft.class);
        set(Minecraft.class, client, "font", new Font(new FixedWidthFont()));
        set(Minecraft.class, null, "instance", client);
    }

    /** Gives every character the same width, so text widgets can lay out their message without font textures. */
    static class FixedWidthFont implements Font.Provider, GlyphSource, BakedGlyph {
        @Override
        public GlyphSource glyphs(FontDescription font) {
            return this;
        }

        @Override
        public EffectGlyph effect() {
            return null;
        }

        @Override
        public BakedGlyph getGlyph(int codePoint) {
            return this;
        }

        @Override
        public BakedGlyph getRandomGlyph(RandomSource random, int width) {
            return this;
        }

        @Override
        public GlyphInfo info() {
            return GlyphInfo.simple(6);
        }

        @Override
        public TextRenderable.Styled createGlyph(float x, float y, int color, int shadowColor, Style style, float boldOffset, float shadowOffset) {
            return null;
        }
    }

    @Test
    void flipMessageBelongsToItsAuctionOnEveryScreenOfIt() throws Exception {
        openFlip(flip("auction-a", "§6Hyperion §7sellers ah, §a+120M"));
        TfmBinGUI view = open("BIN Auction View", 6, item("Hyperion", "item-a"));
        TfmBinGUI refreshed = reopen(view); // the same screen re-created
        clickBuy(refreshed);
        TfmBinGUI confirm = open("Confirm Purchase", 3, item("Hyperion", "item-a")); // Hypixel's answer to the buy click

        String expected = "§6Hyperion §7, §a+120M";
        assertEquals(expected, message(view));
        assertEquals(expected, message(refreshed));
        assertEquals(expected, message(confirm));

        assertEquals("", message(open("BIN Auction View", 6, item("Hyperion", "item-b")))); // another Hyperion auction
        openFlip(flip("auction-c", "§5Necron's Blade §a+40M"));
        assertEquals("§5Necron's Blade §a+40M", message(open("BIN Auction View", 6, item("Necron's Blade", "item-c"))));
    }

    @Test
    void itemWithoutUuidShowsTheFlipOnlyOnTheRequestedScreen() throws Exception {
        openFlip(flip("auction-d", "§9Enchanted Book §a+2M"));

        assertEquals("§9Enchanted Book §a+2M", message(open("BIN Auction View", 6, item("Enchanted Book", null))));
        assertEquals("", message(open("BIN Auction View", 6, item("Enchanted Book", null)))); // a later auction
    }

    @Test
    void newContainerReusingTheContainerIdShowsTheNewlyRequestedFlip() throws Exception {
        openFlip(flip("auction-f", "§6Hyperion §a+50M"));
        TfmBinGUI first = open("BIN Auction View", 6, item("Hyperion", "item-f"));
        int reusedId = first.getBacking().getMenu().containerId;
        openFlip(flip("auction-g", "§5Necron's Blade §a+30M")); // after a reconnect the server counts ids from the start again

        assertEquals("§5Necron's Blade §a+30M", message(open("BIN Auction View", 6, item("Necron's Blade", "item-g"), reusedId)));
        assertEquals("§5Necron's Blade §a+30M", message(open("Confirm Purchase", 3, item("Necron's Blade", "item-g"))));
    }

    @Test
    void placeholderItemBeforeTheAuctionItemKeepsTheFlip() throws Exception {
        openFlip(flip("auction-e", "§6Shadow Fury §a+9M"));
        OfflineTfmBinGUI view = open("BIN Auction View", 6, item("Shadow Fury", null)); // before the real item arrives
        view.showInContainer(item("Shadow Fury", "item-e"));

        assertEquals("§6Shadow Fury §a+9M", message(view));
        assertEquals("§6Shadow Fury §a+9M", message(open("Confirm Purchase", 3, item("Shadow Fury", "item-e"))));
    }

    /** flip received -> /viewauction: the subscriber records the flip before the auction screen opens. */
    static void openFlip(FlipData flip) throws Exception {
        set(Minecraft.class, Minecraft.getInstance(), "gameThread", Thread.currentThread());
        new EventSubscribers().onOpenAuctionGUI(new OnOpenAuctionGUI("/viewauction " + flip.Id, flip));
    }

    static String message(TfmBinGUI gui) {
        return gui.loreMultilineTextWidget.getMessage().getString();
    }

    /** Clicks the overlay's buy area, away from the cancel button. */
    static void clickBuy(TfmBinGUI gui) {
        gui.confirmClickableWidget.onClick(new MouseButtonEvent(0, 0,
                new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
    }

    /** The auction item as the overlay reads it from the container. */
    static ItemStack item(String name, String uuid) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        if (uuid != null) {
            CompoundTag tag = new CompoundTag();
            tag.putString("uuid", uuid);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return stack;
    }

    static FlipData flip(String auctionId, String text) {
        return new FlipData(new ChatMessageData[]{new ChatMessageData(text, null, null)}, auctionId, 0, null, null);
    }

    /** Opens an auction container screen showing {@code item}, runs the hook's overlay constructor and one frame's refresh. */
    static OfflineTfmBinGUI open(String title, int rows, ItemStack item) throws Exception {
        return open(title, rows, item, ++lastContainerId); // the server opens a new container
    }

    /** Opens a new container with the given id; the server counts ids per connection and reuses them. */
    static OfflineTfmBinGUI open(String title, int rows, ItemStack item, int containerId) throws Exception {
        ChestMenu menu = allocate(ChestMenu.class);
        set(AbstractContainerMenu.class, menu, "containerId", containerId);
        SimpleContainer container = new SimpleContainer(rows * 9);
        container.setItem(13, item);
        set(ChestMenu.class, menu, "container", container);
        ContainerScreen screen = allocate(ContainerScreen.class);
        set(Screen.class, screen, "title", Component.literal(title));
        set(Screen.class, screen, "width", 400);
        set(Screen.class, screen, "height", 300);
        set(AbstractContainerScreen.class, screen, "menu", menu);
        return overlay(screen);
    }

    /** Creates the overlay again for the screen {@code gui} is shown on, as a re-initialisation does. */
    static OfflineTfmBinGUI reopen(TfmBinGUI gui) throws Exception {
        return overlay(gui.getBacking());
    }

    private static OfflineTfmBinGUI overlay(ContainerScreen screen) throws Exception {
        set(Minecraft.class, client, "screen", screen);
        OfflineTfmBinGUI overlay = new OfflineTfmBinGUI(screen);
        overlay.refreshFromContainer();
        return overlay;
    }

    /** The overlay with the server round trip of a slot click cut off. */
    static class OfflineTfmBinGUI extends TfmBinGUI {
        OfflineTfmBinGUI(ContainerScreen gcs) {
            super(gcs);
        }

        void refreshFromContainer() {
            syncAuctionItemFromContainer();
        }

        /** The server updates the auctioned item slot of the open screen. */
        void showInContainer(ItemStack item) {
            gcsh.getContainer().setItem(13, item);
            refreshFromContainer();
        }

        @Override
        protected void clickSlot(int slotId) {
            // the click goes to the server, which answers by opening the next screen
        }
    }

    /** Creates an instance without running its constructors, which need a running client. */
    static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeType.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return type.cast(unsafeType.getMethod("allocateInstance", Class.class).invoke(theUnsafe.get(null), type));
    }

    static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
