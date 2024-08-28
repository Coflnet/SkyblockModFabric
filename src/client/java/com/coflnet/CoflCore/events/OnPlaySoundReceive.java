package com.coflnet.CoflCore.events;

import com.coflnet.CoflCore.classes.Sound;

public class OnPlaySoundReceive {
    public final Sound Sound;

    public OnPlaySoundReceive(Sound soundData) {
        this.Sound = soundData;
    }
}
