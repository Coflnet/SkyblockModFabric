package com.coflnet.config;

import com.coflnet.core.InfoDisplayDefaults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CoflModConfigTest {
    @Test
    void malformedConfigFallsBackWithoutDestroyingOriginal(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("coflmod.json");
        for (String malformed : new String[]{"{\"tradeGuiEnabled\":", "{\"tradeListColumns\":\"broken\"}"}) {
            Files.writeString(file, malformed);
            CoflModConfig config = CoflModConfig.load(file.toFile());
            assertTrue(config.sellProtectionEnabled);
            assertEquals(3, config.infoDisplays.size());
            assertEquals(InfoDisplayDefaults.CURRENT_VERSION, config.infoDisplayDefaultsVersion);
            assertEquals(malformed, Files.readString(file));
        }
    }

    @Test
    void validSettingsSurviveMigration(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("coflmod.json");
        Files.writeString(file, "{\"sellProtectionThreshold\":12345,\"tradeListColumns\":2}");
        CoflModConfig config = CoflModConfig.load(file.toFile());
        assertEquals(12345, config.sellProtectionThreshold);
        assertEquals(2, config.tradeListColumns);
        assertEquals(3, config.infoDisplays.size());
        assertEquals(12345, CoflModConfig.load(file.toFile()).sellProtectionThreshold);
    }
}
