package com.coflnet.CoflCore.events;

import com.coflnet.CoflCore.classes.Flip;

public class OnFlipReceive {
    public final Flip FlipData;

    public OnFlipReceive(Flip flipData) {
        this.FlipData = flipData;
    }
}
