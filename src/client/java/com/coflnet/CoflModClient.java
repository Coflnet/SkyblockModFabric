package com.coflnet;

import CoflCore.CoflCore;
import CoflCore.classes.*;
import CoflCore.CoflSkyCommand;
import CoflCore.commands.CommandType;
import CoflCore.commands.models.ChatMessageData;
import CoflCore.commands.models.FlipData;
import CoflCore.commands.models.SoundData;
import CoflCore.events.*;
import CoflCore.handlers.EventRegistry;
import com.coflnet.gui.RenderUtils;
import com.coflnet.gui.cofl.CoflBinGUI;
import com.coflnet.gui.tfm.TfmBinGUI;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sun.java.accessibility.util.AWTEventMonitor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.impl.client.event.lifecycle.ClientLifecycleEventsImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.timer.TimerCallbackSerializer;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.lwjgl.glfw.GLFW;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import static com.coflnet.Utils.ChatComponent;

public class CoflModClient implements ClientModInitializer {
    private KeyBinding bestflipsKeyBinding;
    private boolean keyPressed = false;
    private int counter = 0;

    private String username = "";
    private static FlipData flipData = null;
    private static Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
    private static Countdown countdownData = null;
    private static float countdown = 0.0f;
    private static boolean showCountdown = false;
    private static Timer timer = new Timer();
    private static TimerTask task = new TimerTask() {
        @Override
        public void run() {
            countdown -= 0.1f;
            if(countdown < 0.0) {
                showCountdown = false;
                timer.cancel();
            }
        }
    };
	@Override
	public void onInitializeClient() {
        username = MinecraftClient.getInstance().getSession().getUsername();
        Path configDir = FabricLoader.getInstance().getConfigDir();
        CoflCore cofl = new CoflCore();
        cofl.init(configDir);
        cofl.registerEventFile(this);

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
//                    PlayerEntity player = MinecraftClient.getInstance().player;
//                    player.getWorld().playSound(player, player.getBlockPos(), findByName("BLOCK_ANVIL_PLACE"), SoundCategory.MASTER, 1f, 1f);
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
//                        if (args[0].compareToIgnoreCase("openauctiongui") == 0){
//                            flip = CoflCore.flipHandler.fds.getFlipById(args[1]);
//                        } else flip = null;
                        CoflSkyCommand.processCommand(args,username);
                        return 1;
                    })));
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof GenericContainerScreen gcs) {
                System.out.println(gcs.getTitle().getString());
                if (CoflCore.config.purchaseOverlay != null && gcs.getTitle() != null
                        && (gcs.getTitle().getString().contains("BIN Auction View")
                        && gcs.getScreenHandler().getInventory().size() == 9 * 6
                        || gcs.getTitle().getString().contains("Confirm Purchase")
                        && gcs.getScreenHandler().getInventory().size() == 9 * 3)
                ) {
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
            if (showCountdown && countdownData != null){
                //RenderUtils.drawString(drawContext, "New flips in: "+String.format("%.1f", countdown), 10, 10, 0xFFFFFFFF);
                MinecraftClient.getInstance().textRenderer.draw(
                        countdownData.getPrefix()+String.format("New flips in: %.1f", countdown),
                        MinecraftClient.getInstance().getWindow().getWidth()/countdownData.getWidthPercentage(),
                        MinecraftClient.getInstance().getWindow().getHeight()/countdownData.getHeightPercentage(),
                        0xFFFFFFFF, false,
                        drawContext.getMatrices().peek().getPositionMatrix(),
                        drawContext.getVertexConsumers(),
                        TextRenderer.TextLayerType.NORMAL,
                        0x00FFFFFF, 100
                );
            }
        });
	}

    public static FlipData popFlipData(){
        FlipData fd = flipData;
        flipData = null;
        return fd;
    }

    @Subscribe
    public void WriteToChat(OnWriteToChatReceive command){
         MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(ChatComponent(command.ChatMessage));
    }

    @Subscribe
    public void onChatMessage(OnChatMessageReceive event){
        for (ChatMessage message : event.ChatMessages) {
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(ChatComponent(message));
        }
    }

    @Subscribe
    public void onModChatMessage(OnModChatMessage event){
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.of(event.message));
    }

    @Subscribe
    public void onCountdownReceive(OnCountdownReceive event){
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.of("COUNTDOWN RECEIVED: "+event.CountdownData.getDuration()));
        countdown = event.CountdownData.getDuration();
        countdownData = event.CountdownData;
        showCountdown = true;

        System.out.println("COUNTDOWNDATA:");
        System.out.println("w%: "+countdownData.getWidthPercentage());
        System.out.println("ww: "+MinecraftClient.getInstance().getWindow().getWidth());
        System.out.println("w% of ww: "+MinecraftClient.getInstance().getWindow().getWidth()/countdownData.getWidthPercentage());
        System.out.println("h%: "+countdownData.getHeightPercentage());
        System.out.println("wh: "+MinecraftClient.getInstance().getWindow().getHeight());
        System.out.println("h% of wh: "+MinecraftClient.getInstance().getWindow().getHeight()/countdownData.getHeightPercentage());
        System.out.println("prefix: "+countdownData.getPrefix());
        System.out.println("scale: "+countdownData.getScale());

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                countdown -= 0.1f;
                if(countdown < 0.0) {
                    showCountdown = false;
                    timer.cancel();
                }
            }
        }, 1, 100);
    }

    @Subscribe
    public void onOpenAuctionGUI(OnOpenAuctionGUI event){
        flipData = event.flip;
        MinecraftClient.getInstance().getNetworkHandler().sendChatMessage(event.openAuctionCommand);
    }

    @Subscribe
    public void onFlipReceive(OnFlipReceive event){
        Flip f = event.FlipData;
        FlipData fd = new FlipData(
                Arrays.stream(f.getMessages())
                        .map(cm -> new ChatMessageData(
                                cm.getText(),
                                cm.getOnClick(),
                                cm.getHover())
                        ).toArray(ChatMessageData[]::new),
                f.getId(),
                f.getWorth(),
                new SoundData(
                        f.getSound().getSoundName(),
                        f.getSound().getSoundPitch() == null ? 0 : f.getSound().getSoundPitch()
                ),
                f.getRender()
        );

        if (bestflipsKeyBinding.isPressed()) {
            EventBus.getDefault().post(new OnOpenAuctionGUI("/viewauction "+fd.Id, fd));
            return;
        }

        EventBus.getDefault().post(new OnChatMessageReceive(f.getMessages()));
        EventBus.getDefault().post(new OnPlaySoundReceive(f.getSound()));
        CoflCore.flipHandler.fds.Insert(fd);
    }

    @Subscribe
    public void onReceiveCommand(ReceiveCommand event){
        if (event.command.getType() == CommandType.Flip){
            JsonObject jo = gson.fromJson(event.command.getData(), JsonObject.class);
            EventBus.getDefault().post(new OnFlipReceive(jsonObjToFlip(jo)));
        }
    }

    @Subscribe
    public void onPlaySoundReceive(OnPlaySoundReceive event){
        if(event.Sound == null || event.Sound.getSoundName() == null) return;

        String soundName = "";
        switch (event.Sound.getSoundName()){
            case "note.bass" -> soundName = "BLOCK_NOTE_BLOCK_BASS";
            case "note.pling" -> soundName = "BLOCK_NOTE_BLOCK_PLING";
            case "note.hat" -> soundName = "BLOCK_NOTE_BLOCK_HAT";
            case "random.orb" -> soundName = "ENTITY_EXPERIENCE_ORB_PICKUP";
            default -> soundName = "";
        }

        PlayerEntity player = MinecraftClient.getInstance().player;
        player.getWorld().playSound(
                player, player.getBlockPos(),
                findByName(soundName),
                SoundCategory.MASTER, 1f,
                event.Sound.getSoundPitch() == null ? 1f : (float) event.Sound.getSoundPitch()
        );
    }

    @Subscribe
    public void onExecuteCommand(OnExecuteCommand event){
        System.out.println("ON EXEC COMMAND:"+event.Error);
    }

    public static SoundEvent findByName(String name) {
        SoundEvent result = SoundEvents.BLOCK_NOTE_BLOCK_BELL.value();

        for (Field f : SoundEvents.class.getDeclaredFields()) {
            if (f.getName().equalsIgnoreCase(name)) {
                try {
                    SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
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

    public static Flip jsonObjToFlip(JsonObject jsonObj){
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
}