package com.coflnet.CoflCore.events;

import com.coflnet.CoflCore.classes.ChatMessage;

public class OnWriteToChatReceive {
    public final ChatMessage[] ChatMessages;

    public OnWriteToChatReceive(ChatMessage[] chatMessage) {
        this.ChatMessages = chatMessage;
    }
}

