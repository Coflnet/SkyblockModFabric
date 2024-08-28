package com.coflnet.CoflCore.events;

import com.coflnet.CoflCore.classes.JsonStringCommand;

public class ReceiveCommand {
    public final JsonStringCommand command;

    public ReceiveCommand(JsonStringCommand data) {
        this.command = data;
    }
}
