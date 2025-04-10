package com.coflnet;

import CoflCore.CoflCore;
import CoflCore.classes.*;
import CoflCore.CoflSkyCommand;
import CoflCore.commands.models.FlipData;
import CoflCore.handlers.EventRegistry;
import com.coflnet.gui.RenderUtils;
import com.coflnet.gui.cofl.CoflBinGUI;
import com.coflnet.gui.tfm.TfmBinGUI;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.collection.DefaultedList;
import org.lwjgl.glfw.GLFW;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;

public class CoflModClient implements ClientModInitializer {
    private static Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
    private static boolean keyPressed = false;
    private static int counter = 0;
    public static KeyBinding bestflipsKeyBinding;

    private String username = "";
	@Override
	public void onInitializeClient() {
        username = MinecraftClient.getInstance().getSession().getUsername();
        Path configDir = FabricLoader.getInstance().getConfigDir();
        CoflCore cofl = new CoflCore();
        cofl.init(configDir);
        cofl.registerEventFile(new EventSubscribers());

        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            RenderUtils.init();
        });

        bestflipsKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "keybinding.coflmod.bestflips",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                ""
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(bestflipsKeyBinding.isPressed()) {
                if(counter == 0){
                    EventRegistry.onOpenBestFlip(username, true);
                }
                if(counter < 2) counter++;
            } else {
                counter = 0;
            }
        });

		ClientPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if(MinecraftClient.getInstance() != null && MinecraftClient.getInstance().getCurrentServerEntry() != null && MinecraftClient.getInstance().getCurrentServerEntry().address.contains("hypixel.net")){
				System.out.println("Connected to Hypixel");
                username = MinecraftClient.getInstance().getSession().getUsername();
			}
		});

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("cofl")
                    .then(ClientCommandManager.argument("args", StringArgumentType.greedyString()).executes(context -> {
                        String[] args = context.getArgument("args", String.class).split(" ");
                        CoflSkyCommand.processCommand(args,username);
                        return 1;
                    })));
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof GenericContainerScreen gcs) {
                System.out.println(gcs.getTitle().getString());
                System.out.println("INV: "+gcs.getScreenHandler().getInventory().getClass().getName());
                if (CoflCore.config.purchaseOverlay != null && gcs.getTitle() != null
                        && (gcs.getTitle().getString().contains("BIN Auction View")
                        && gcs.getScreenHandler().getInventory().size() == 9 * 6
                        || gcs.getTitle().getString().contains("Confirm Purchase")
                        && gcs.getScreenHandler().getInventory().size() == 9 * 3)
                ){
                    if (!(client.currentScreen instanceof CoflBinGUI || client.currentScreen instanceof TfmBinGUI)) {
                        switch (CoflCore.config.purchaseOverlay) {
                            case COFL: client.setScreen(new CoflBinGUI(gcs));break;
                            case TFM: client.setScreen(new TfmBinGUI(gcs));break;
                            case null: default: break;
                        }
                    }
                }
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (EventSubscribers.showCountdown && EventSubscribers.countdownData != null
                    && (MinecraftClient.getInstance().currentScreen == null
                        || MinecraftClient.getInstance().currentScreen instanceof ChatScreen)){
                RenderUtils.drawStringWithShadow(
                        drawContext, EventSubscribers.countdownData.getPrefix()+"New flips in: "+String.format("%.1f", EventSubscribers.countdown),
                        MinecraftClient.getInstance().getWindow().getWidth()/EventSubscribers.countdownData.getWidthPercentage(),
                        MinecraftClient.getInstance().getWindow().getHeight()/EventSubscribers.countdownData.getHeightPercentage(),
                        0xFFFFFFFF, EventSubscribers.countdownData.getScale()
                );
            }
        });
	}

    public static FlipData popFlipData(){
        FlipData fd = EventSubscribers.flipData;
        EventSubscribers.flipData = null;
        return fd;
    }

    public static SoundEvent findByName(String name) {
        SoundEvent result = SoundEvents.BLOCK_NOTE_BLOCK_BELL.value();

        for (Field f : SoundEvents.class.getDeclaredFields()) {
            if (f.getName().equalsIgnoreCase(name)) {
                try {
                    try {
                        result = (SoundEvent) f.get(SoundEvent.class);
                    } catch (ClassCastException e){
                        result = (SoundEvent) ((RegistryEntry.Reference) f.get(RegistryEntry.Reference.class)).value();
                    }
                } catch (IllegalAccessException e) {
                    System.out.println("SoundEvent inaccessible. This shouldn't happen");
                }
                break;
            }
        }
        return result;
    }

    public static Flip jsonToFlip(String json){
        JsonObject jsonObj = gson.fromJson(json, JsonObject.class);
        JsonObject[] chatMessagesObj = gson.fromJson(jsonObj.get("messages"), JsonObject[].class);
        ChatMessage[] chatMessages = Arrays.stream(chatMessagesObj).map(jsonObject -> new ChatMessage(
                jsonObject.get("text").getAsString(),
                jsonObject.get("onClick").getAsString(),
                jsonObject.get("hover").isJsonNull() ? null : jsonObject.get("hover").getAsString()
        )).toArray(ChatMessage[]::new);

        String id = gson.fromJson(jsonObj.get("id"), String.class);
        int worth = gson.fromJson(jsonObj.get("worth"), Integer.class);
        Sound sound = gson.fromJson(jsonObj.get("sound"), Sound.class);
        AuctionItem auction = gson.fromJson(jsonObj.get("auction"), AuctionItem.class);
        String render = gson.fromJson(jsonObj.get("render"), String.class);
        String target = gson.fromJson(jsonObj.get("target"), String.class);
        return new Flip(chatMessages, id, worth, sound, auction, render, target);
    }

    public static String inventoryToNBT(Inventory inventory){
        NbtCompound nbtCompound = new NbtCompound();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PlayerEntity player = MinecraftClient.getInstance().player;
        DefaultedList<ItemStack> itemStacks = DefaultedList.of();

        for (int i = 0; i < inventory.size(); i++) {
            itemStacks.add(inventory.getStack(i));
        }

        try {
            Inventories.writeNbt(nbtCompound, itemStacks, player.getRegistryManager());
            nbtCompound.put("i", nbtCompound.get("Items"));
            nbtCompound.remove("Items");

            System.out.println(nbtCompound.get("i").asString());

            NbtIo.writeCompressed(nbtCompound, baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());

        } catch (IOException e){}
        return "";
    }

    public static String[] getItemIdsFromInventory(PlayerInventory inventory){
        ArrayList<String> res = new ArrayList<>();

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem() != Items.AIR) res.add(Registries.ITEM.getId(stack.getItem()).toString());
        }

        return res.toArray(String[]::new);
    }
}

