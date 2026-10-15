package com.coflnet.skyblock.testserver;

public record Scenario(
        String id,
        String title,
        int seed,
        Room room,
        boolean manual,
        boolean automated) {
    public record Room(int x, int y, int z) {
    }
}
