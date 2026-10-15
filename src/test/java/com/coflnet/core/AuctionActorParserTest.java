package com.coflnet.core;

import com.coflnet.protection.auction.AuctionScreenMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuctionActorParserTest {
    @Test void fullNameMatchesWithRankFormattingAndCase() {
        assertTrue(AuctionActorParser.isOwnActor("§b[MVP§c+§b] §aALex", "alex", AuctionScreenMode.SELLER));
        assertTrue(AuctionActorParser.isOwnActor(" Alex ", "alex", AuctionScreenMode.BIDDER));
    }

    @Test void similarUsernamesAndRankTextAreForeign() {
        for (AuctionScreenMode mode : AuctionScreenMode.values()) {
            assertFalse(AuctionActorParser.isOwnActor("[VIP] Alex2", "Alex", mode));
            assertFalse(AuctionActorParser.isOwnActor("SuperAlex", "Alex", mode));
            assertFalse(AuctionActorParser.isOwnActor("[MVP+] Other", "MVP", mode));
            assertFalse(AuctionActorParser.isOwnActor("", "Alex", mode));
        }
    }

    @Test void onlyExactBidderYouSentinelIsOwn() {
        assertTrue(AuctionActorParser.isOwnActor("You", "Alex", AuctionScreenMode.BIDDER));
        assertFalse(AuctionActorParser.isOwnActor("YouAreRich", "Alex", AuctionScreenMode.BIDDER));
        assertFalse(AuctionActorParser.isOwnActor("You", "Alex", AuctionScreenMode.SELLER));
    }
}
