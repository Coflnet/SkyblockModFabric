package com.coflnet;

import CoflCore.CoflCore;
import CoflCore.classes.ChatMessage;
import CoflCore.CoflSkyCommand;
import CoflCore.events.OnChatMessageReceive;
import CoflCore.events.OnModChatMessage;
import CoflCore.events.OnWriteToChatReceive;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.greenrobot.eventbus.Subscribe;

import java.nio.file.Path;

import static com.coflnet.utils.ChatComponent;

public class CoflModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
        String username = MinecraftClient.getInstance().getSession().getUsername();
        Path configDir = FabricLoader.getInstance().getConfigDir();
        CoflCore cofl = new CoflCore();
        cofl.init(configDir);
        cofl.registerEventFile(this);

		ClientPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if(MinecraftClient.getInstance() != null && MinecraftClient.getInstance().getCurrentServerEntry() != null && MinecraftClient.getInstance().getCurrentServerEntry().address.contains("hypixel.net")){
				System.out.println("Connected to Hypixel");
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
}