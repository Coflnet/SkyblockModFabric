package com.coflnet.mixin;

import com.coflnet.config.CoflModConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the container screen mixin's click, close and removal callbacks on a Hypixel "Create BIN
 * Auction" menu. The mixin is a real {@link net.minecraft.client.gui.screens.Screen}, so the click
 * callback runs to its end on both revisions and the comparison fails on the uncancelled submit.
 */
class DefaultBinPriceProtectionTest {
    private static final String CREATE_BIN = "Create BIN Auction";
    private static final int LISTED_ITEM = 13;
    private static final int DECORATION = 10;
    private static final int SUBMIT = 29;
    private static final int PRICE_BUTTON = 31;
    private static final int DURATION_BUTTON = 33;
    private static final int CLOSE_BUTTON = 49;
    private static Minecraft previousClient;
    private static Object previousConfig;

    @BeforeAll
    static void installClient() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (Item item : List.of(Items.AIR, Items.DIAMOND_SWORD, Items.GOLD_INGOT, Items.GLASS_PANE)) {
            Holder.Reference<Item> holder = BuiltInRegistries.ITEM.getOrThrow(BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow());
            if (!holder.areComponentsBound()) {
                holder.bindComponents(DataComponentMap.EMPTY);
            }
        }
        // A client with a visible, empty HUD and a task queue, which is all the click callback and the chat message need.
        previousClient = Minecraft.getInstance();
        Minecraft minecraft = allocate(Minecraft.class);
        Gui gui = allocate(Gui.class);
        set(Gui.class, gui, "hud", allocate(Hud.class));
        set(Minecraft.class, minecraft, "gui", gui);
        set(BlockableEventLoop.class, minecraft, "pendingRunnables", new ConcurrentLinkedQueue<>());
        set(Minecraft.class, null, "instance", minecraft);
        previousConfig = get(CoflModConfig.class, null, "instance");
        set(CoflModConfig.class, null, "instance", new CoflModConfig());
    }

    @AfterAll
    static void restoreClient() throws Exception {
        set(Minecraft.class, null, "instance", previousClient);
        set(CoflModConfig.class, null, "instance", previousConfig);
    }

    @BeforeEach
    void leaveEarlierMenus() throws Exception {
        setProtectionEnabled(true);
        new BinMenu("Auction House", 0).removed(); // leaving the auction house ends any tracked listing
    }

    /** Sets the new toggle when the config has it; the base config has no such setting to turn off. */
    private static void setProtectionEnabled(boolean enabled) throws Exception {
        for (Field field : CoflModConfig.class.getDeclaredFields()) {
            if (field.getName().equals("defaultBinPriceProtectionEnabled")) {
                field.set(CoflModConfig.get(), enabled);
            }
        }
    }

    @Test
    void untouchedDefaultPriceBlocksSubmitUnlessCtrlIsHeld() throws Exception {
        BinMenu menu = new BinMenu(CREATE_BIN, 500);
        assertTrue(menu.click(SUBMIT, false));
        assertTrue(menu.click(SUBMIT, false));
        assertTrue(menu.click(SUBMIT, InputConstants.MOUSE_BUTTON_RIGHT, false));
        assertFalse(menu.click(SUBMIT, true));
        assertFalse(menu.click(SUBMIT, InputConstants.MOUSE_BUTTON_RIGHT, true));
    }

    @Test
    void listingSurvivesPriceSignAndDurationSubMenu() throws Exception {
        BinMenu menu = new BinMenu(CREATE_BIN, 500);
        assertFalse(menu.click(PRICE_BUTTON, false)); // opens Hypixel's price sign ...
        menu.removed();                                // ... which replaces the menu
        BinMenu edited = new BinMenu(CREATE_BIN, 1200);
        assertFalse(edited.click(DURATION_BUTTON, false)); // opens the duration sub-menu
        edited.removed();
        new BinMenu("Auction Duration", 0).removed();      // returning to the listing
        assertFalse(new BinMenu(CREATE_BIN, 1200).click(SUBMIT, false));

        leaveEarlierMenus();
        BinMenu untouched = new BinMenu(CREATE_BIN, 500);
        assertFalse(untouched.click(DURATION_BUTTON, false));
        untouched.removed();
        new BinMenu("Auction Duration", 0).removed();
        assertTrue(new BinMenu(CREATE_BIN, 500).click(SUBMIT, false));
    }

    @Test
    void eachListingOfTheSameItemRecordsItsOwnDefault() throws Exception {
        BinMenu closed = new BinMenu(CREATE_BIN, 1000);
        assertTrue(closed.click(SUBMIT, false));
        closed.removed(); // closed without a button click
        assertTrue(new BinMenu(CREATE_BIN, 500).click(SUBMIT, false), "after closing");

        leaveEarlierMenus();
        BinMenu decorated = new BinMenu(CREATE_BIN, 1000);
        assertFalse(decorated.click(DECORATION, false)); // a glass pane is not a button
        decorated.removed();
        assertTrue(new BinMenu(CREATE_BIN, 500).click(SUBMIT, false), "after a decoration click");

        leaveEarlierMenus();
        BinMenu left = new BinMenu(CREATE_BIN, 1000);
        assertFalse(left.click(CLOSE_BUTTON, false)); // Hypixel's own close button
        left.removed();
        assertTrue(new BinMenu(CREATE_BIN, 500).click(SUBMIT, false), "after the close button");

        leaveEarlierMenus();
        BinMenu escaped = new BinMenu(CREATE_BIN, 1000);
        assertFalse(escaped.click(PRICE_BUTTON, false));
        escaped.closed(); // Escape before the sign arrived
        escaped.removed();
        assertTrue(new BinMenu(CREATE_BIN, 500).click(SUBMIT, false), "after escaping");

        leaveEarlierMenus();
        BinMenu navigated = new BinMenu(CREATE_BIN, 1000);
        assertFalse(navigated.click(PRICE_BUTTON, false));
        navigated.removed();
        new BinMenu("Auction House", 0).removed(); // went back to the auction house instead
        assertTrue(new BinMenu(CREATE_BIN, 500).click(SUBMIT, false), "after the auction house");
    }

    @Test
    void onlyDefaultsBetweenTenAndFiveThousandCoinsAreGuarded() throws Exception {
        for (long price : new long[] {10, 5000}) {
            leaveEarlierMenus();
            assertTrue(new BinMenu(CREATE_BIN, price).click(SUBMIT, false), price + " coins");
        }
        for (long price : new long[] {9, 5001, 2_000_000}) {
            leaveEarlierMenus();
            assertFalse(new BinMenu(CREATE_BIN, price).click(SUBMIT, false), price + " coins");
        }
    }

    @Test
    void normalAuctionsAndDisabledSettingAreNotGuarded() throws Exception {
        assertFalse(new BinMenu("Create Auction", 500).click(SUBMIT, false));
        setProtectionEnabled(false);
        assertFalse(new BinMenu(CREATE_BIN, 500).click(SUBMIT, false));
    }

    /** A 54-slot Hypixel menu with the listed item, the submit button carrying the price, and the edit buttons. */
    private static final class BinMenu {
        final SimpleContainer container = new SimpleContainer(54);
        final HandledScreenMixin screen;

        BinMenu(String title, long price) throws Exception {
            container.setItem(DECORATION, named(new ItemStack(Items.GLASS_PANE), " "));
            container.setItem(LISTED_ITEM, named(new ItemStack(Items.DIAMOND_SWORD), "§6Hyperion"));
            container.setItem(SUBMIT, lore(named(new ItemStack(Items.GOLD_INGOT), "§aCreate BIN Auction"),
                    "§7Item price: §6" + String.format(Locale.US, "%,d", price) + " coins",
                    "§7Duration: §b48 hours", "", "§eClick to create!"));
            container.setItem(PRICE_BUTTON, lore(named(new ItemStack(Items.GOLD_INGOT), "§aItem price"), "§7Click to edit!"));
            container.setItem(DURATION_BUTTON, lore(named(new ItemStack(Items.GOLD_INGOT), "§aDuration"), "§7Click to edit!"));
            container.setItem(CLOSE_BUTTON, named(new ItemStack(Items.GOLD_INGOT), "§cClose"));
            ChestMenu chestMenu = allocate(ChestMenu.class);
            set(ChestMenu.class, chestMenu, "container", container);
            // Plain methods on the base mixin, shadow and hook implementations on the patched one, so the
            // comparison fails on the uncancelled submit, not on a missing newly introduced API.
            screen = new HandledScreenMixin(Component.literal(title)) {
                public AbstractContainerMenu getMenu() {
                    return chestMenu;
                }

                protected Slot coflnet$clickedSlot(double mouseX, double mouseY) {
                    return new Slot(container, (int) mouseX, 0, 0); // the x coordinate names the slot
                }
            };
        }

        /** Left-clicks {@code slot} and reports whether the mixin cancelled the click. */
        boolean click(int slot, boolean ctrl) {
            return click(slot, InputConstants.MOUSE_BUTTON_LEFT, ctrl);
        }

        boolean click(int slot, int button, boolean ctrl) {
            CallbackInfoReturnable<Boolean> cir = new CallbackInfoReturnable<>("mouseClicked", true);
            MouseButtonEvent click = new MouseButtonEvent(slot, 0,
                    new MouseButtonInfo(button, ctrl ? InputConstants.MOD_CONTROL : 0));
            screen.onMouseClicked(click, false, cir);
            return cir.isCancelled();
        }

        /** The player closes the screen with Escape; the base mixin has no close callback. */
        void closed() throws Exception {
            for (Method method : HandledScreenMixin.class.getDeclaredMethods()) {
                if (method.getName().equals("coflnet$onClose")) {
                    method.invoke(screen, new CallbackInfo("onClose", false));
                }
            }
        }

        /** The screen goes away, whether closed by the player or replaced by the server. */
        void removed() {
            screen.onRemoved(new CallbackInfo("removed", false));
        }
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static ItemStack lore(ItemStack stack, String... lines) {
        stack.set(DataComponents.LORE, new ItemLore(Arrays.stream(lines).<Component>map(Component::literal).toList()));
        return stack;
    }

    /** Creates an instance without running its constructors, which need a running client. */
    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeType.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return type.cast(unsafeType.getMethod("allocateInstance", Class.class).invoke(theUnsafe.get(null), type));
    }

    private static Object get(Class<?> owner, Object target, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
