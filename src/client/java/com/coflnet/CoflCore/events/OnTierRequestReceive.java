package com.coflnet.CoflCore.events;

import com.coflnet.CoflCore.classes.Tier;

public class OnTierRequestReceive {
    public final Tier Tier;

    public OnTierRequestReceive(com.coflnet.CoflCore.classes.Tier tier) {
        this.Tier = tier;
    }
}
