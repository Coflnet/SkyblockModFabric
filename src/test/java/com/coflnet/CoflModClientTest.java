package com.coflnet;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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

    private static String countdownText(double seconds, int precision) throws Exception {
        Method format = CoflModClient.class.getDeclaredMethod("getStringFromDouble", double.class, int.class);
        format.setAccessible(true);
        return (String) format.invoke(null, seconds, precision);
    }
}
