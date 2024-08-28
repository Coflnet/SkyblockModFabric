package com.coflnet;

import com.coflnet.CoflCore.CoflCore;
import com.coflnet.CoflCore.events.ReceiveCommand;
import com.coflnet.CoflCore.events.SocketClose;
import com.coflnet.CoflCore.events.SocketError;
import com.coflnet.CoflCore.events.SocketOpen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.text.Text;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

public class CoflModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {

		ClientPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if(MinecraftClient.getInstance() != null && MinecraftClient.getInstance().getCurrentServerEntry() != null && MinecraftClient.getInstance().getCurrentServerEntry().address.contains("hypixel.net")){
				System.out.println("Connected to Hypixel");
				CoflCore cofl = new CoflCore();
				try {
					Path configDir = FabricLoader.getInstance().getConfigDir();

					Path p = configDir.resolve("CoflSky");
					if (!p.toFile().exists()) {
						p.toFile().mkdir();
					}
					File configFile = new File(p.toFile(), "config.json");

					cofl.setupSocket(MinecraftClient.getInstance().getSession().getUsername(), configFile.toPath());
					cofl.registerEventFile(this);
				} catch (IOException e) {
					throw new RuntimeException(e);
				} catch (URISyntaxException e) {
					throw new RuntimeException(e);
				}
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