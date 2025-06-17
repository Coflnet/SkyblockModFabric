package com.coflnet;

import CoflCore.classes.ChatMessage;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class Utils {
    public static  MutableText ChatComponent(ChatMessage cmd) {
        MutableText message = Text.literal(cmd.getText());
        if (cmd.getOnClick() != null) {
            if (cmd.getOnClick().startsWith("http")) {
                message.styled((style) -> {
                    try {
                        return style.withClickEvent(new ClickEvent.OpenUrl(URI.create(cmd.getOnClick())));
                    } catch (Exception e) {
                        System.out.println("Error occurred while parsing URI");
                        return style;
                    }
                });
            } else {
                message.styled((style) -> style.withClickEvent(new ClickEvent.RunCommand(cmd.getOnClick())));
            }
        }

        if (cmd.getHover() != null && !cmd.getHover().isEmpty()) {
            message.styled((style) -> style.withHoverEvent(new HoverEvent.ShowText(Text.of(cmd.getHover()))));
        }
        return message;
    }
}
