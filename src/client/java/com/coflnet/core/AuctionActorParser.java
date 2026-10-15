package com.coflnet.core;

import com.coflnet.protection.auction.AuctionScreenMode;

/** Compares the actor field in auction lore without accepting partial usernames. */
public final class AuctionActorParser {
    private AuctionActorParser() {}

    public static boolean isOwnActor(String actor, String playerName, AuctionScreenMode mode) {
        if (actor == null || playerName == null) return false;
        String name = FormattingCodes.strip(actor).trim();
        if (name.startsWith("[")) {
            int rankEnd = name.indexOf(']');
            if (rankEnd >= 0) name = name.substring(rankEnd + 1).trim();
        }
        return name.equalsIgnoreCase(playerName)
                || mode == AuctionScreenMode.BIDDER && name.equalsIgnoreCase("You");
    }
}
