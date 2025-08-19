package com.coflnet;

import CoflCore.commands.models.ChatMessageData;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import java.net.URI;

public class Utils {
    public static  MutableText ChatComponent(ChatMessageData cmd) {
        MutableText message = Text.literal(cmd.Text);
        if (cmd.OnClick != null) {
            if (cmd.OnClick.startsWith("http")) {
                message.styled((style) -> {
                    try {
                        return style.withClickEvent(new ClickEvent.OpenUrl(URI.create(cmd.OnClick)));
                    } catch (Exception e) {
                        System.out.println("Error occurred while parsing URI");
                        return style;
                    }
                });
            }
            else if(cmd.OnClick.startsWith("suggest:")){
                String suggestion = cmd.OnClick.substring("suggest:".length());
                message.styled((style) -> style.withClickEvent(new ClickEvent.SuggestCommand(suggestion)));
            } else if(cmd.OnClick.startsWith("copy:")) {
                String copyText = cmd.OnClick.substring("copy:".length());
                message.styled((style) -> style.withClickEvent(new ClickEvent.CopyToClipboard(copyText)));
            } else {
                message.styled((style) -> style.withClickEvent(new ClickEvent.RunCommand(cmd.OnClick)));
            }
        }

        if (cmd.Hover != null && !cmd.Hover.isEmpty()) {
            message.styled((style) -> style.withHoverEvent(new HoverEvent.ShowText(Text.of(cmd.Hover))));
        }
        return message;
    }
}
