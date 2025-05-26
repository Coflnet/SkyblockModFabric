package com.coflnet;

import CoflCore.CoflCore;
import CoflCore.classes.*;
import CoflCore.CoflSkyCommand;
import CoflCore.commands.models.FlipData;
import CoflCore.configuration.Config;
import CoflCore.handlers.DescriptionHandler;
import CoflCore.handlers.EventRegistry;
import CoflCore.network.QueryServerCommands;
import CoflCore.network.WSClient;
import com.coflnet.gui.RenderUtils;
import com.coflnet.gui.cofl.CoflBinGUI;
import com.coflnet.gui.tfm.TfmBinGUI;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.minecraft.client.ObjectMapper;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancement.criterion.InventoryChangedCriterion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.tooltip.HoveredTooltipPositioner;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.Component;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.InventoryChangedListener;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerListener;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

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
    public static HashMap<String, String> itemIds = new HashMap<>();

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

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof HandledScreen hs) {
                DefaultedList<ItemStack> itemStacks = ((HandledScreen<?>) screen).getScreenHandler().getStacks();

                if (!client.player.getInventory().getStack(8).getComponents().toString().contains("minecraft:custom_data=>{id:\"SKYBLOCK_MENU\"}")) return;
                DescriptionHandler.emptyTooltipData();
                DescriptionHandler.loadDescriptionForInventory(
                        getItemIdsFromInventory(itemStacks),
                        screen.getTitle().getLiteralString(),
                        inventoryToNBT(itemStacks),
                        MinecraftClient.getInstance().getSession().getUsername()
                );

//                hs.getScreenHandler().addListener(new ScreenHandlerListener() {
//                    @Override
//                    public void onSlotUpdate(ScreenHandler handler, int slotId, ItemStack stack) {
//                        if (DescriptionHandler.getTooltipData(CoflModClient.itemIds.get(getIdFromStack(stack))).length == 0){
//                            System.out.println("NO DESC FOUND");
//                        }
//                    }
//                    @Override
//                    public void onPropertyUpdate(ScreenHandler handler, int property, int value) {}
//                });
            }
        });

        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {
            if (itemIds.isEmpty()) return;
            DescriptionHandler.DescModification[] tooltips = DescriptionHandler.getTooltipData(itemIds.get(getIdFromStack(stack)));
            //System.out.println("Tooltips anz: "+ tooltips.length);
            for (DescriptionHandler.DescModification tooltip : tooltips) {
                switch (tooltip.type){
                    case "APPEND":
                        lines.add(Text.of(tooltip.value+" "));
                        break;
                    case "REPLACE":
                        lines.remove(tooltip.line);
                        lines.add(tooltip.line, Text.of(tooltip.value));
                        break;
                    case "INSERT":
                        lines.add(tooltip.line, Text.of(tooltip.value));
                        break;
                    case "DELETE":
                        lines.remove(tooltip.line);
                        break;
                    case "HIGHLIGHT":
                        if (MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?> hs){
                            //hs.getScreenHandler().getSlot(hs.getScreenHandler().getStacks().indexOf(stack));
                        }
                        break;
                    default: System.out.println("Unknown type: "+tooltip.type);
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

    public static DefaultedList<ItemStack> inventoryToItemStacks(Inventory inventory){
        DefaultedList<ItemStack> itemStacks = DefaultedList.of();

        for (int i = 0; i < inventory.size(); i++) {
            itemStacks.add(inventory.getStack(i));
        }

        return itemStacks;
    }

    public static String inventoryToNBT(Inventory inventory){
        return inventoryToNBT(inventoryToItemStacks(inventory));
    }

    public static String[] getItemIdsFromInventory(Inventory inventory){
        return getItemIdsFromInventory(inventoryToItemStacks(inventory));
    }

    public static String inventoryToNBT(DefaultedList<ItemStack> itemStacks){
        NbtCompound nbtCompound = new NbtCompound();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PlayerEntity player = MinecraftClient.getInstance().player;

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

    public static String[] getItemIdsFromInventory(DefaultedList<ItemStack> itemStacks){
        ArrayList<String> res = new ArrayList<>();
        itemIds.clear();

        for (int i = 0; i < itemStacks.size(); i++) {
            ItemStack stack = itemStacks.get(i);
            if (stack.getItem() != Items.AIR) {
                String id = getIdFromStack(stack);
                itemIds.put(id, id);
                res.add(id);
                System.out.println(id);
            }
        }

        return res.toArray(String[]::new);
    }

    public static String getIdFromStack(ItemStack stack){
        JsonObject stackJson = null;
        for (ComponentType<?> type : stack.getComponents().getTypes()) {
            if (type.toString().contains("minecraft:custom_data")){
                stackJson = gson.fromJson(stack.get(type).toString(), JsonObject.class);
            }
        }
        if (stackJson == null) return "";

        JsonElement uuid = stackJson.get("uuid");
        if (uuid != null) return uuid.getAsString();
        return stackJson.get("id").getAsString()+";"+stack.getCount();
    }

}

