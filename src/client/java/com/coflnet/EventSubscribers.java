package com.coflnet;

import java.util.*;

import CoflCore.classes.*;
import CoflCore.configuration.ConfigurationManager;
import CoflCore.configuration.LocalConfig;
import CoflCore.events.*;
import CoflCore.handlers.EventRegistry;
import CoflCore.network.WSClient;
import net.fabricmc.fabric.impl.networking.client.ClientNetworkingImpl;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import static com.coflnet.Utils.ChatComponent;

import CoflCore.CoflCore;
import CoflCore.commands.CommandType;
import CoflCore.commands.models.ChatMessageData;
import CoflCore.commands.models.FlipData;
import CoflCore.commands.models.SoundData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;

public class EventSubscribers {
    public static FlipData flipData = null;
    public static Countdown countdownData = null;
    public static float countdown = 0.0f;
    public static boolean showCountdown = false;
    public static Timer timer = new Timer();
    public static TimerTask task = new TimerTask() {
        @Override
        public void run() {
            countdown -= 0.1f;
            if(countdown < 0.0) {
                showCountdown = false;
                timer.cancel();
            }
        }
    };

    @Subscribe
    public void WriteToChat(OnWriteToChatReceive command){
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(ChatComponent(command.ChatMessage));
    }

    @Subscribe
    public void onChatMessage(OnChatMessageReceive event){
        if (event.ChatMessages == null || event.ChatMessages.length == 0) {
            return;
        }

        net.minecraft.text.MutableText combinedMessage = net.minecraft.text.Text.empty();

        for (ChatMessage message : event.ChatMessages) {
            if (message == null) {
                continue;
            } 
            net.minecraft.text.Text styledPart = ChatComponent(message);
            if (styledPart != null) {
                combinedMessage.append(styledPart);
            }
        }
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(combinedMessage);
    }

    @Subscribe
    public void onModChatMessage(OnModChatMessage event){
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.of(event.message));
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
                CoflModClient.findByName(soundName),
                SoundCategory.MASTER, 1f,
                event.Sound.getSoundPitch() == null ? 1f : (float) event.Sound.getSoundPitch()
        );
    }

    @Subscribe
    public void onReceiveCommand(ReceiveCommand event){
        
    }

    @Subscribe
    public void onCountdownReceive(OnCountdownReceive event){
        countdown = event.CountdownData.getDuration();
        countdownData = event.CountdownData;
        showCountdown = true;

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
    public void onFlipReceive(OnFlipReceive event){
        Flip f = event.FlipData;
        FlipData fd = new FlipData(
                Arrays.stream(f.getMessages().toArray(ChatMessage[]::new))
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

        if (CoflModClient.bestflipsKeyBinding.isPressed()) {
            EventBus.getDefault().post(new OnOpenAuctionGUI("/viewauction "+fd.Id, fd));
        } else 
            CoflCore.flipHandler.fds.Insert(fd);

        EventBus.getDefault().post(new OnChatMessageReceive(f.getMessages().toArray(ChatMessage[]::new)));
        EventBus.getDefault().post(new OnPlaySoundReceive(f.getSound()));
    }

    @Subscribe
    public void onOpenAuctionGUI(OnOpenAuctionGUI event){
        flipData = event.flip;
        MinecraftClient.getInstance().getNetworkHandler().sendChatMessage(event.openAuctionCommand);
    }

    @Subscribe
    public void onExecuteCommand(OnExecuteCommand event){
        System.out.println("Skycofl executes:"+event.Command);
        String command =event.Command.substring(1);
        MinecraftClient.getInstance().getNetworkHandler().sendChatCommand(command);
    }

    @Subscribe
    public void onSettingsReceive(OnSettingsReceive event){
        System.out.println("SETTINGS RECEIVED: "+event.Settings.getSettingName());
    }

    @Subscribe
    public void onCloseGUI(OnCloseGUI event){
        if (MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?> hs) {
            System.out.println("Closing GUI: " + hs.getClass().getName());
            hs.close();
        }
    }

    @Subscribe
    public void onGetInventory(OnGetInventory event){
        try {
            DefaultedList<ItemStack> itemStacks = DefaultedList.of();
            for (Iterator<ItemStack> it = MinecraftClient.getInstance().player.getInventory().iterator(); it.hasNext(); ) {
                itemStacks.add(it.next());
            }
            CoflModClient.loadDescriptionsForItems("Inventory", itemStacks);
        } catch (Exception e){
            System.out.println(e);
        }
    }
}
