package com.coflnet;

import CoflCore.commands.CommandType;
import CoflCore.commands.JsonStringCommand;
import CoflCore.configuration.LocalConfig;
import CoflCore.events.ReceiveCommand;
import CoflCore.events.SocketClose;
import com.coflnet.config.TradeGuiManager;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.SharedConstants;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises client helpers against the real registries, without a GL client. */
class CoflModClientTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void backendSoundNamesResolveToTheirSoundInsteadOfTheFallbackBell() {
        assertSame(SoundEvents.NOTE_BLOCK_BASS.value(), CoflModClient.findByName("note.bass"));
        assertSame(SoundEvents.NOTE_BLOCK_PLING.value(), CoflModClient.findByName("note.pling"));
        assertSame(SoundEvents.NOTE_BLOCK_HAT.value(), CoflModClient.findByName("note.hat"));
        assertSame(SoundEvents.EXPERIENCE_ORB_PICKUP, CoflModClient.findByName("random.orb"));
        assertSame(SoundEvents.NOTE_BLOCK_BELL.value(), CoflModClient.findByName("unknown.sound"));
    }

    @Test void countdownTextSurvivesBackendPrecisionOutsideTheText() throws Exception {
        assertEquals("9.5s", countdownText(9.512, 3));
        assertEquals("9s", countdownText(9.512, 2));
        assertEquals("120s", countdownText(120.4, 3));
        // "9.512" has five characters: a larger or zero precision used to throw every frame.
        assertEquals("9.512s", countdownText(9.512, 6));
        assertEquals("9s", countdownText(9.512, 0));
    }

    @Test void premiumTierSurvivesAWorldJoinWhileTheBackendStaysConnected() throws Exception {
        logInWithTier("PREMIUM_PLUS");
        assertTrue(TradeGuiManager.hasPremium());

        // Switching islands fires JOIN again, but the still-running backend never resends loggedIn.
        worldJoinHook().invoke(new CoflModClient(), null, null, null);
        assertTrue(TradeGuiManager.hasPremium(), "lowball helper locked itself on a world join");
    }

    @Test void premiumTierLastsUntilTheBackendSessionCloses() {
        EventSubscribers subscribers = logInWithTier("PREMIUM_PLUS");
        assertTrue(TradeGuiManager.hasPremium());

        // World joins no longer clear the tier, so the closed backend session has to.
        subscribers.onSocketClose(new SocketClose());
        assertFalse(TradeGuiManager.hasPremium());
    }

    @Test void bundledCommandAutocompleteOffersShareitem() {
        // /cofl suggestions read CoflCore.config.knownCommands, which starts from the core's bundled defaults.
        assertTrue(LocalConfig.createDefaultConfig().knownCommands.containsKey("shareitem"));
    }

    @Test void enablingACustomPurchaseOverlayWarnsAboutHypixelRules() throws Exception {
        assertTrue(chatAfter("cofl setgui cofl").stream().anyMatch(line -> line.contains("Hypixel rules")),
                "no Hypixel rules note after /cofl setgui cofl");
        assertTrue(chatAfter("cofl setgui off").isEmpty(), "disabling the overlay should not warn");
    }

    /** Runs a client command against a headless client whose chat records every system message. */
    private static List<String> chatAfter(String command) throws Exception {
        Minecraft previous = Minecraft.getInstance();
        List<String> chat = new ArrayList<>();
        Minecraft minecraft = allocate(Minecraft.class);
        Gui gui = allocate(Gui.class);
        setFieldOfType(Gui.class, gui, ChatListener.class, allocate(RecordingChatListener.class));
        RecordingChatListener.lines = chat;
        set(Minecraft.class, minecraft, "gui", gui);
        set(Minecraft.class, minecraft, "gameThread", Thread.currentThread()); // execute() runs inline
        set(Minecraft.class, null, "instance", minecraft);
        try {
            CommandDispatcher<FabricClientCommandSource> dispatcher = new CommandDispatcher<>();
            Method register = CoflModClient.class.getDeclaredMethod("registerDefaultCommands", CommandDispatcher.class, String.class);
            register.setAccessible(true);
            register.invoke(new CoflModClient(), dispatcher, "cofl");
            dispatcher.execute(command, null);
        } finally {
            set(Minecraft.class, null, "instance", previous);
        }
        return chat;
    }

    /** Collects the chat lines the mod displays; created without its constructor, which needs a client. */
    static class RecordingChatListener extends ChatListener {
        static List<String> lines;

        RecordingChatListener() {
            super(null);
        }

        @Override
        public void handleSystemMessage(Component message, boolean overlay) {
            lines.add(message.getString());
        }
    }

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

    private static void setFieldOfType(Class<?> owner, Object target, Class<?> type, Object value) throws Exception {
        for (Field field : owner.getDeclaredFields()) {
            if (field.getType() == type) {
                field.setAccessible(true);
                field.set(target, value);
                return;
            }
        }
        throw new NoSuchFieldException(type.getSimpleName() + " on " + owner.getSimpleName());
    }

    private static EventSubscribers logInWithTier(String tier) {
        EventSubscribers subscribers = new EventSubscribers();
        subscribers.onReceiveCommand(new ReceiveCommand(new JsonStringCommand(CommandType.LoggedIn,
                "{\"uuid\":\"abc\",\"verified\":true,\"tier\":\"" + tier + "\"}")));
        return subscribers;
    }

    /**
     * The handler registered on {@code ClientPlayConnectionEvents.JOIN}, found by the
     * {@code Join.onPlayReady} signature so the test binds to the hook itself rather
     * than to its name (it was an inline lambda before it became a named method).
     */
    private static Method worldJoinHook() {
        Class<?>[] joinSignature = {ClientPacketListener.class, PacketSender.class, Minecraft.class};
        Method hook = null;
        for (Method candidate : CoflModClient.class.getDeclaredMethods()) {
            if (Arrays.equals(candidate.getParameterTypes(), joinSignature)) {
                assertNull(hook, "more than one world-join hook on CoflModClient");
                hook = candidate;
            }
        }
        assertNotNull(hook, "no world-join hook on CoflModClient");
        hook.setAccessible(true);
        return hook;
    }

    private static String countdownText(double seconds, int precision) throws Exception {
        Method format = CoflModClient.class.getDeclaredMethod("getStringFromDouble", double.class, int.class);
        format.setAccessible(true);
        return (String) format.invoke(null, seconds, precision);
    }
}
