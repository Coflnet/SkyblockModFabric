package com.coflnet.gui.hud;

import CoflCore.CoflCore;
import CoflCore.commands.RawCommand;
import com.coflnet.CoflModClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;

/** Uploads changed order overviews once per second; inventory access stays on the client thread. */
public final class BazaarOrderUpload implements ClientTickEvents.EndTick {
    private int ticks;
    private String lastSnapshot;

    @Override
    public void onEndTick(Minecraft client) {
        if (!(client.gui.screen() instanceof ContainerScreen screen)
                || !screen.getTitle().getString().equals("Your Bazaar Orders")) {
            lastSnapshot = null;
            ticks = 0;
            return;
        }
        if (++ticks < 20) {
            return;
        }
        ticks = 0;
        var container = screen.getMenu().getContainer();
        JsonArray slots = new JsonArray();
        for (int index = 0; index < container.getContainerSize(); index++) {
            var stack = container.getItem(index);
            JsonObject slot = new JsonObject();
            slot.addProperty("slot", index);
            slot.addProperty("empty", stack.isEmpty());
            slot.addProperty("displayName", stack.getHoverName().getString());
            JsonArray lore = new JsonArray();
            var itemLore = stack.get(DataComponents.LORE);
            if (itemLore != null) {
                itemLore.lines().forEach(line -> lore.add(line.getString()));
            }
            slot.add("lore", lore);
            slots.add(slot);
        }
        JsonObject snapshot = new JsonObject();
        // The backend counts menu slots by subtracting the 36 player inventory slots.
        snapshot.addProperty("slotCount", container.getContainerSize() + 36);
        snapshot.add("slots", slots);
        String json = snapshot.toString();
        if (json.equals(lastSnapshot)) {
            return;
        }
        lastSnapshot = json;
        CoflModClient.backgroundQueue.submit(() -> {
            var wrapper = CoflCore.getWrapper();
            if (wrapper != null) {
                wrapper.SendMessage(new RawCommand("uploadbazaarorders", json));
            }
        });
    }
}
