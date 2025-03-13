package com.coflnet;

import CoflCore.classes.ChatMessage;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public class Utils {
    public static  MutableText ChatComponent(ChatMessage cmd) {
        MutableText message = Text.literal(cmd.getText());
        if (cmd.getOnClick() != null) {
            if (cmd.getOnClick().startsWith("http")) {
                message
                        .styled((style) -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, cmd.getOnClick())));
            } else {
                message
                        .styled((style) -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd.getOnClick())));
            }
        }

        if (cmd.getHover() != null && !cmd.getHover().isEmpty()) {
            message.copy()
                    .styled((style) -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of(cmd.getHover()))));
        }
        return message;
    }
}
