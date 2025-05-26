package com.coflnet;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import static com.coflnet.Utils.ChatComponent;

import CoflCore.CoflCore;
import CoflCore.classes.ChatMessage;
import CoflCore.classes.Countdown;
import CoflCore.classes.Flip;
import CoflCore.commands.CommandType;
import CoflCore.commands.models.ChatMessageData;
import CoflCore.commands.models.FlipData;
import CoflCore.commands.models.SoundData;
import CoflCore.events.OnChatMessageReceive;
import CoflCore.events.OnCountdownReceive;
import CoflCore.events.OnExecuteCommand;
import CoflCore.events.OnFlipReceive;
import CoflCore.events.OnModChatMessage;
import CoflCore.events.OnOpenAuctionGUI;
import CoflCore.events.OnPlaySoundReceive;
import CoflCore.events.OnWriteToChatReceive;
import CoflCore.events.ReceiveCommand;
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
        if (event.command.getType() == CommandType.Flip){
            EventBus.getDefault().post(new OnFlipReceive(CoflModClient.jsonToFlip(event.command.getData())));
        }
    }

    @Subscribe
    public void onCountdownReceive(OnCountdownReceive event){
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.of("COUNTDOWN RECEIVED: "+event.CountdownData.getDuration()));
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

        if (CoflModClient.bestflipsKeyBinding.isPressed()) {
            EventBus.getDefault().post(new OnOpenAuctionGUI("/viewauction "+fd.Id, fd));
            return;
        }

        EventBus.getDefault().post(new OnChatMessageReceive(f.getMessages()));
        EventBus.getDefault().post(new OnPlaySoundReceive(f.getSound()));
        CoflCore.flipHandler.fds.Insert(fd);
    }

    @Subscribe
    public void onOpenAuctionGUI(OnOpenAuctionGUI event){
        flipData = event.flip;
        MinecraftClient.getInstance().getNetworkHandler().sendChatMessage(event.openAuctionCommand);
    }

    @Subscribe
    public void onExecuteCommand(OnExecuteCommand event){
        System.out.println("ON EXEC COMMAND:"+event.Error);
    }
}
