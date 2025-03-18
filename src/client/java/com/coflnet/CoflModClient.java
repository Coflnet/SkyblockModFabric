package com.coflnet;

import CoflCore.CoflCore;
import CoflCore.classes.ChatMessage;
import CoflCore.CoflSkyCommand;
import CoflCore.events.OnChatMessageReceive;
import CoflCore.events.OnModChatMessage;
import CoflCore.events.OnWriteToChatReceive;
import com.coflnet.gui.RenderUtils;
import com.coflnet.gui.cofl.CoflBinGUI;
import com.coflnet.gui.tfm.TfmBinGUI;
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
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.greenrobot.eventbus.Subscribe;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

import static com.coflnet.Utils.ChatComponent;

public class CoflModClient implements ClientModInitializer {
    private  KeyBinding bestflipsKeyBinding;
	@Override
	public void onInitializeClient() {
        String username = MinecraftClient.getInstance().getSession().getUsername();
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
            while (bestflipsKeyBinding.wasPressed()) {
                //client.player.sendMessage(Text.literal(), false);
                client.getServer().getCommandManager().executeWithPrefix(client.getServer().getCommandSource(), "cofl bestflips");
            }
        });

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

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof GenericContainerScreen gcs) {
                ScreenEvents.beforeRender(gcs).register((screen1, drawContext, mouseX, mouseY, tickDelta) -> {
                    GenericContainerScreen gcs1 = (GenericContainerScreen) screen1;
                    if (CoflCore.config.purchaseOverlay != null
                            && (gcs.getTitle().getLiteralString().contains("BIN Auction View")
                                && gcs.getScreenHandler().getInventory().size() == 9 * 6
                            || gcs.getTitle().getLiteralString().contains("Confirm Purchase")
                                && gcs.getScreenHandler().getInventory().size() == 9 * 3)
                    ) {
                        if (!(client.currentScreen instanceof CoflBinGUI || client.currentScreen instanceof TfmBinGUI)) {
                            switch (CoflCore.config.purchaseOverlay) {
                                case COFL: client.setScreen(new CoflBinGUI(Items.BREAD, gcs1));break;
                                case TFM: client.setScreen(new TfmBinGUI(Items.BREAD));break;
                                case null: default: break;
                            }
                        }
                    }
                });
            }
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