package com.coflnet;

import CoflCore.commands.CommandType;
import CoflCore.commands.JsonStringCommand;
import CoflCore.events.ReceiveCommand;
import CoflCore.events.SocketClose;
import com.coflnet.config.TradeGuiManager;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

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
