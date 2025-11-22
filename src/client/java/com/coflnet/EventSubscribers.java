package com.coflnet;

import java.util.*;

import CoflCore.classes.*;
import CoflCore.commands.models.HotkeyRegister;
import CoflCore.events.*;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import static com.coflnet.Utils.ChatComponent;

import CoflCore.CoflCore;
import CoflCore.commands.models.ChatMessageData;
import CoflCore.commands.models.FlipData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;

public class EventSubscribers {
    public static FlipData flipData = null;
    public static Countdown countdownData = null;
    public static long countdownExpiryTime = 0L; // Target time when countdown expires (in milliseconds)
    public static boolean showCountdown = false;
    public static List<Position> positions = null;
    
    /**
     * Gets the current remaining countdown time in seconds.
     * Calculates based on the difference between the target expiry time and current time.
     */
    public static float getCountdown() {
        if (!showCountdown || countdownExpiryTime == 0L) {
            return 0.0f;
        }
        
        long remainingMs = countdownExpiryTime - System.currentTimeMillis();
        if (remainingMs <= 0) {
            showCountdown = false;
            return 0.0f;
        }
        
        return remainingMs / 1000.0f;
    }

    @Subscribe
    public void WriteToChat(OnWriteToChatReceive command){
        MinecraftClient.getInstance().execute(() -> 
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(ChatComponent(command.ChatMessage))
        );
    }

    @Subscribe
    public void onChatMessage(OnChatMessageReceive event){
        if (event.ChatMessages == null || event.ChatMessages.length == 0) {
            return;
        }

        net.minecraft.text.MutableText combinedMessage = net.minecraft.text.Text.empty();

        for (ChatMessageData message : event.ChatMessages) {
            if (message == null) {
                continue;
            } 
            net.minecraft.text.Text styledPart = ChatComponent(message);
            if (styledPart != null) {
                combinedMessage.append(styledPart);
            }
        }
        MinecraftClient.getInstance().execute(() -> 
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(combinedMessage)
        );
    }

    @Subscribe
    public void onModChatMessage(OnModChatMessage event){
        MinecraftClient.getInstance().execute(() -> 
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.of(event.message))
        );
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

        String finalSoundName = soundName;
        MinecraftClient.getInstance().execute(() -> {
            PlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            MinecraftClient.getInstance().world.playSound(
                player, player.getBlockPos(),
                CoflModClient.findByName(finalSoundName),
                SoundCategory.MASTER, 1f,
                event.Sound.getSoundPitch() == null ? 1f : (float) event.Sound.getSoundPitch()
            );
        }
        });
    }

    @Subscribe
    public void onReceiveCommand(ReceiveCommand event){
        
    }

    @Subscribe
    public void onCountdownReceive(OnCountdownReceive event){
        countdownData = event.CountdownData;
        // Calculate the target expiry time based on current time + duration
        countdownExpiryTime = System.currentTimeMillis() + (long)(event.CountdownData.getDuration() * 1000);
        showCountdown = true;
    }

    @Subscribe
    public void onFlipReceive(OnFlipReceive event){
        FlipData f = event.FlipData;

        if (CoflModClient.bestflipsKeyBinding.isPressed()) {
            EventBus.getDefault().post(new OnOpenAuctionGUI("/viewauction "+f.Id, f));
        } else 
            CoflCore.flipHandler.fds.Insert(f);

        EventBus.getDefault().post(new OnChatMessageReceive(f.Messages));
        EventBus.getDefault().post(new OnPlaySoundReceive(new Sound(f.Sound.Name, (int) f.Sound.Pitch)));
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
        System.out.println("SETTINGS RECEIVED: "+event.Settings.size());
    }

    @Subscribe
    public void onCloseGUI(OnCloseGUI event){
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?> hs) {
                System.out.println("Closing GUI: " + hs.getClass().getName());
                hs.close();
            }
        });
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

    @Subscribe
    public void onHighlightBlocks(OnHighlightBlocks event){
        positions = event.positions;
    }

    @Subscribe
    public void onHotkeyRegister(HotkeyRegister[] hotkeys){
        CoflModClient.setHotKeys(hotkeys);
    }
}
