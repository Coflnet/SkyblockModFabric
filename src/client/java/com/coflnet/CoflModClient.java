package com.coflnet;

import CoflCore.CoflCore;
import CoflCore.classes.AuctionItem;
import CoflCore.classes.ChatMessage;
import CoflCore.CoflSkyCommand;
import CoflCore.classes.Flip;
import CoflCore.classes.Sound;
import CoflCore.commands.CommandType;
import CoflCore.commands.JsonStringCommand;
import CoflCore.commands.models.ChatMessageData;
import CoflCore.commands.models.FlipData;
import CoflCore.commands.models.SoundData;
import CoflCore.events.*;
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
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

import static com.coflnet.Utils.ChatComponent;

public class CoflModClient implements ClientModInitializer {
    private  KeyBinding bestflipsKeyBinding;

    public static long LastClick = System.currentTimeMillis();
    public static final ExecutorService chatThreadPool = Executors.newFixedThreadPool(2);
    public static final ExecutorService tickThreadPool = Executors.newFixedThreadPool(2);
    public static long LastViewAuctionInvocation = Long.MIN_VALUE;
    public static String LastViewAuctionUUID = null;
    public static Pattern chatpattern = Pattern.compile("a^", 2);
    private static LinkedBlockingQueue<String> chatBatch = new LinkedBlockingQueue();
    private static LocalDateTime lastBatchStart = LocalDateTime.now();

    private String username = "";
    private static FlipData flipData = null;
    private static Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
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
            if(bestflipsKeyBinding.wasPressed()) {
                onOpenBestFlip(username, true);
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

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

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
	}

    public static FlipData popFlipData(){
        FlipData fd = flipData;
        flipData = null;
        return fd;
    }

    public static void onOpenBestFlip(String username, boolean isInitialKeypress) {
        if (System.currentTimeMillis() - LastClick >= 300L) {
            FlipData f = CoflCore.flipHandler.fds.GetHighestFlip();
            System.out.println(f);
            if (f != null) {
                CoflSkyCommand.processCommand(new String[]{"openauctiongui", f.Id, "true"}, username);
                LastViewAuctionUUID = f.Id;
                LastViewAuctionInvocation = System.currentTimeMillis();
                LastClick = System.currentTimeMillis();
                String command = (new Gson()).toJson("/viewauction " + f.Id);
                CoflCore.Wrapper.SendMessage(new JsonStringCommand(CommandType.Clicked, command));
                CoflSkyCommand.processCommand(new String[]{"track", "besthotkey", f.Id, username}, username);
            } else if (isInitialKeypress) {
                CoflSkyCommand.processCommand(new String[]{"dialog", "nobestflip", username}, username);
            }
        }
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
        //MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.of("COUNTDOWN RECEIVED: "+event.CountdownData.getDuration()));
    }

    @Subscribe
    public void onOpenAuctionGUI(OnOpenAuctionGUI event){
        flipData = event.flip;
        MinecraftClient.getInstance().getNetworkHandler().sendChatMessage(event.openAuctionCommand);
    }

    @Subscribe
    public void onFlipReceive(OnFlipReceive event){
        Flip f = event.FlipData;
        EventBus.getDefault().post(new OnChatMessageReceive(f.getMessages()));
        CoflCore.flipHandler.fds.Insert(new FlipData(
                Arrays.stream(f.getMessages())
                         .map(cm -> new ChatMessageData(cm.getText(), cm.getOnClick(), cm.getHover()))
                        .toArray(ChatMessageData[]::new),
                f.getId(),
                f.getWorth(),
                new SoundData(
                        f.getSound().getSoundName(),
                        f.getSound().getSoundPitch() == null ? 0 : f.getSound().getSoundPitch()
                ),
                f.getRender()
        ));
    }

    @Subscribe
    public void onReceiveCommand(ReceiveCommand event){
        if (event.command.getType() == CommandType.Flip){
            JsonObject jo = gson.fromJson(event.command.getData(), JsonObject.class);
            EventBus.getDefault().post(new OnFlipReceive(jsonObjToFlip(jo)));
        }
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