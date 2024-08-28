package com.coflnet.CoflCore.events;

import com.coflnet.CoflCore.classes.Settings;

public class OnSettingsReceive {
    public final Settings Settings;

    public OnSettingsReceive(com.coflnet.CoflCore.classes.Settings settings) {
        Settings = settings;
    }
}
