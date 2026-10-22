package com.coflnet.gui.cofl;

import com.coflnet.gui.AuctionStatus;
import com.coflnet.gui.BinActions;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Routes clicks through the real BIN overlay widgets, without a GL client. */
class CoflBinGUIClickTest {
    private static Minecraft previousInstance;

    static class RecordingBinGUI extends CoflBinGUI {
        List<Integer> clickedSlots;

        RecordingBinGUI() {
            super(null);
        }

        void layOut(AuctionStatus status) {
            auctionStatus = status;
            p = 5;
            r = 4;
            width = 200;
            height = 200;
            clearAndInitWidgets(400, 300);
        }

        @Override
        protected void clickSlot(int slotId) {
            clickedSlots.add(slotId);
        }
    }

    static class SilentSoundManager extends SoundManager {
        SilentSoundManager() {
            super(null);
        }

        @Override
        public SoundEngine.PlayResult play(SoundInstance sound) {
            return SoundEngine.PlayResult.NOT_STARTED;
        }
    }

    @BeforeAll
    static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        if (!Items.AIR.builtInRegistryHolder().areComponentsBound()) {
            Items.AIR.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY); // the overlay's empty item slot
        }
        previousInstance = Minecraft.getInstance();
        Minecraft minecraft = allocate(Minecraft.class);
        set(Minecraft.class, minecraft, "font", new Font(null));
        set(Minecraft.class, minecraft, "soundManager", allocate(SilentSoundManager.class));
        set(Minecraft.class, null, "instance", minecraft);
    }

    @AfterAll
    static void restoreClient() throws Exception {
        set(Minecraft.class, null, "instance", previousInstance);
    }

    @Test
    void clickOnLoreTextRunsPrimaryAction() throws Exception {
        RecordingBinGUI gui = allocate(RecordingBinGUI.class);
        gui.clickedSlots = new ArrayList<>();
        set(Screen.class, gui, "children", new ArrayList<>());
        set(Screen.class, gui, "narratables", new ArrayList<>());
        set(Screen.class, gui, "renderables", new ArrayList<>());
        gui.layOut(AuctionStatus.INIT);

        Field loreField = CoflBinGUI.class.getDeclaredField("loreScrollableTextWidget");
        loreField.setAccessible(true);
        AbstractWidget lore = (AbstractWidget) loreField.get(gui);
        MouseButtonEvent click = new MouseButtonEvent(lore.getX() + 10, lore.getY() + 10,
                new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        gui.mouseClicked(click, false);

        assertEquals(List.of(BinActions.primarySlot(AuctionStatus.INIT)), gui.clickedSlots);
    }

    /** Creates an instance without running its constructors, which need a running client. */
    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeType.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return type.cast(unsafeType.getMethod("allocateInstance", Class.class).invoke(theUnsafe.get(null), type));
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
