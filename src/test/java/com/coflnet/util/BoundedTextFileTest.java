package com.coflnet.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundedTextFileTest {
    @TempDir
    Path directory;

    @Test
    void readsFilesUpToTheLimit() throws IOException {
        Path file = directory.resolve("config.json");
        Files.writeString(file, "12345");

        assertEquals("12345", BoundedTextFile.readUtf8(file, 5));
    }

    @Test
    void stopsBeforeReadingOversizedFiles() throws IOException {
        Path file = directory.resolve("config.json");
        Files.writeString(file, "123456");

        assertNull(BoundedTextFile.readUtf8(file, 5));
    }

    @Test
    void rejectsNegativeLimits() {
        assertThrows(IllegalArgumentException.class,
                () -> BoundedTextFile.readUtf8(directory.resolve("config.json"), -1));
    }
}
