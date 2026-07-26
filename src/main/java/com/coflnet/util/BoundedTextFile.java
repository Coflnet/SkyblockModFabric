package com.coflnet.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BoundedTextFile {
    private BoundedTextFile() {
    }

    public static String readUtf8(Path path, int maximumBytes) throws IOException {
        if (maximumBytes < 0) {
            throw new IllegalArgumentException("maximum bytes must not be negative");
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] data = input.readNBytes(maximumBytes);
            return input.read() == -1
                    ? new String(data, StandardCharsets.UTF_8)
                    : null;
        }
    }
}
