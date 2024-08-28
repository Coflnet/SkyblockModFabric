package com.coflnet.CoflCore.events;

import com.coflnet.CoflCore.classes.Countdown;

public class OnCountdownReceive {
    public final Countdown CountdownData;

    public OnCountdownReceive(Countdown countdownData) {
        this.CountdownData = countdownData;
    }
}
