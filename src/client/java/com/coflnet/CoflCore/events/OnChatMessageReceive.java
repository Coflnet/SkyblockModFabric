package com.coflnet.CoflCore.events;

import com.coflnet.CoflCore.classes.ChatMessage;

public class OnChatMessageReceive {
    public final ChatMessage ChatMessage;

    public OnChatMessageReceive(ChatMessage chatMessage) {
        this.ChatMessage = chatMessage;
    }
}
