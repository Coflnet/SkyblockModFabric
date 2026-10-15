package com.coflnet.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CorePurityTest {
    @Test void coreHasNoMinecraftOrMojangReferences() throws IOException {
        List<String> violations = new ArrayList<>();
        scan(Path.of("src/client/java/com/coflnet/core"), List.of(".java"), violations);
        scan(Path.of("build/classes/java/client/com/coflnet/core"), List.of(".class"), violations);
        assertTrue(violations.isEmpty(), "Forbidden Minecraft dependency in core: " + violations);
    }

    private static void scan(Path root, List<String> extensions, List<String> violations) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (extensions.stream().noneMatch(extension -> path.toString().endsWith(extension))) continue;
                String content = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
                if (content.contains("net.minecraft") || content.contains("com.mojang")) {
                    violations.add(path.toString());
                }
            }
        }
    }
}
