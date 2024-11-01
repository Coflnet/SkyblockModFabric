package com.coflnet;

import CoflCore.CoflCore;
import CoflCore.events.ReceiveCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

public class CoflModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {

		ClientPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if(MinecraftClient.getInstance() != null && MinecraftClient.getInstance().getCurrentServerEntry() != null && MinecraftClient.getInstance().getCurrentServerEntry().address.contains("hypixel.net")){
				System.out.println("Connected to Hypixel");
				CoflCore cofl = new CoflCore();
				Path configDir = FabricLoader.getInstance().getConfigDir();
				EventBus.getDefault().register(cofl);
				cofl.init(configDir);
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(ClientCommandManager.literal("cofl")
				.executes(context -> {
							context.getSource().sendFeedback(Text.literal("The command is executed in the client!"));
							return 1;
						})));

	}

	@Subscribe
	public void onMessage(ReceiveCommand event){
		System.out.println("Command received");
	}

}