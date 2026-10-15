package com.coflnet.skyblock.testserver;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixtureLoaderTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        bindEmptyItemComponentsForUnitTests();
    }

    @AfterEach
    void clearFixtureSelection() {
        System.clearProperty("skycofl.fixtures");
        System.clearProperty("coflnet.scenario.selected_id");
        System.clearProperty("coflnet.scenario.fixture_sha256");
    }

    @Test
    void selectedOrDefaultUsesTheOwnedScenarioFixtureWhenNothingIsStaged() {
        var menu = FixtureLoader.selectedOrDefault(registries(), "bazaar-menu");

        assertEquals("Bazaar ➜ Products", menu.title());
        assertEquals("default:bazaar-menu", menu.digest());
        assertSame(Items.EMERALD, menu.items().get(13).getItem());
    }

    @Test
    void bazaarOrderDefaultPreservesSanitizedTagAndVisibleDescription() {
        var menu = FixtureLoader.selectedOrDefault(registries(), "bazaar-orders");
        var order = menu.items().get(13);
        var data = order.get(DataComponents.CUSTOM_DATA).copyTag();

        assertEquals("Co-op Bazaar Orders", menu.title());
        assertEquals("AGATHA_COUPON", data.getStringOr("Tag", ""));
        assertEquals("Order details:", data.getStringOr("Description", ""));
        assertEquals("Order details:", order.get(DataComponents.LORE).lines().getFirst().getString());
    }

    @Test
    void enderChestDefaultHasRepresentativeNavigationRow() {
        var menu = FixtureLoader.selectedOrDefault(registries(), "ender-chest-sequence");
        String[] expectedNames = {"Close", "Back to Storage", "Navigation filler", "Navigation filler",
                "Navigation filler", "Navigation filler", "Navigation filler", "Previous Storage Page",
                "Next Storage Page"};
        var expectedItems = java.util.List.of(Items.BARRIER, Items.ARROW,
                BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("gray_stained_glass_pane")),
                BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("gray_stained_glass_pane")),
                BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("gray_stained_glass_pane")),
                BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("gray_stained_glass_pane")),
                BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("gray_stained_glass_pane")),
                BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("green_stained_glass_pane")),
                BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("yellow_stained_glass_pane")));

        for (int slot = 0; slot < 9; slot++) {
            assertSame(expectedItems.get(slot), menu.items().get(slot).getItem(), "item at top-row slot " + slot);
            assertEquals(expectedNames[slot], menu.items().get(slot).getCustomName().getString(),
                    "name at top-row slot " + slot);
        }
        assertSame(Items.ENDER_CHEST, menu.items().get(10).getItem());
    }

    @Test
    void zeroFixtureDigestUsesDefaultWhenHostFixtureDirectoryIsAbsent(@TempDir Path temporaryDirectory) {
        System.setProperty("skycofl.fixtures", temporaryDirectory.resolve("fixtures").toString());
        System.setProperty("coflnet.scenario.selected_id", "bazaar-menu");
        System.setProperty("coflnet.scenario.fixture_sha256", "0".repeat(64));

        var menu = FixtureLoader.selectedOrDefault(registries(), "bazaar-menu");

        assertEquals("Bazaar ➜ Products", menu.title());
        assertEquals("default:bazaar-menu", menu.digest());
    }

    @Test
    void selectedFixtureDirectoryUsesMenuJsonAndPreservesPlaceholderEvidence(@TempDir Path fixtureDirectory)
            throws Exception {
        byte[] fixture = """
                {"name":"Ender Chest (1/9)","items":[
                  {"id":"minecraft:paper","count":1,"components":{
                    "minecraft:custom_data":{
                      "hypixel_tag":"ENDER_CHEST",
                      "hypixel_item_id":"ENDER_CHEST",
                      "hypixel_color":"#112233"
                    }
                  }},null
                ]}
                """.getBytes(StandardCharsets.UTF_8);
        Files.write(fixtureDirectory.resolve("menu.json"), fixture);
        System.setProperty("skycofl.fixtures", fixtureDirectory.toString());
        System.setProperty("coflnet.scenario.selected_id", "ender-chest-sequence");

        FixtureMenu menu = FixtureLoader.selectedOrDefault(registries(), "ender-chest-sequence");

        assertEquals("Ender Chest (1/9)", menu.title());
        assertEquals(54, menu.items().size());
        assertSame(Items.PAPER, menu.items().getFirst().getItem());
        var evidence = menu.items().getFirst().get(DataComponents.CUSTOM_DATA).copyTag();
        assertEquals("ENDER_CHEST", evidence.getStringOr("hypixel_item_id", ""));
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fixture)), menu.digest());
        assertTrue(menu.items().get(1).isEmpty());
    }

    @Test
    void selectedFixtureDirectoryRejectsAdjacentFiles(@TempDir Path fixtureDirectory) throws Exception {
        Files.writeString(fixtureDirectory.resolve("menu.json"), "{}");
        Files.writeString(fixtureDirectory.resolve("adjacent.json"), "{}");
        System.setProperty("skycofl.fixtures", fixtureDirectory.toString());
        System.setProperty("coflnet.scenario.selected_id", "ender-chest-sequence");

        assertThrows(IllegalStateException.class,
                () -> FixtureLoader.selectedOrDefault(registries(), "ender-chest-sequence"));
    }

    @Test
    void aliasesAndNonPlaceholderItemsAreRejected() {
        assertThrows(IllegalStateException.class, () -> FixtureLoader.parseConverted(
                registries(), new StringReader("{\"title\":\"Chest\",\"items\":[null]}"), "digest"));
        assertThrows(IllegalStateException.class, () -> FixtureLoader.parseConverted(
                registries(), new StringReader("""
                        {"name":"Chest","items":[{"id":"minecraft:diamond","count":1,"components":{
                          "minecraft:custom_data":{"hypixel_item_id":"DIAMOND"}
                        }}]}
                        """), "digest"));
    }

    @Test
    void placeholderWithoutSanitizedHypixelIdentityIsRejected() {
        assertThrows(IllegalStateException.class, () -> FixtureLoader.parseConverted(
                registries(), new StringReader("""
                        {"name":"Chest","items":[{"id":"minecraft:paper","count":1}]}
                        """), "digest"));
    }

    private static RegistryAccess.Frozen registries() {
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private static void bindEmptyItemComponentsForUnitTests() {
        try {
            var bindComponents = net.minecraft.core.Holder.Reference.class
                    .getDeclaredMethod("bindComponents", DataComponentMap.class);
            bindComponents.setAccessible(true);
            for (var item : BuiltInRegistries.ITEM) {
                var holder = BuiltInRegistries.ITEM.wrapAsHolder(item);
                if (holder instanceof net.minecraft.core.Holder.Reference<?> reference
                        && !reference.areComponentsBound()) {
                    bindComponents.invoke(reference, DataComponentMap.EMPTY);
                }
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("unable to bind unit-test item components", exception);
        }
    }
}
