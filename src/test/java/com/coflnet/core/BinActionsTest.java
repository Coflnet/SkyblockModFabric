package com.coflnet.core;

import com.coflnet.gui.AuctionStatus;
import com.coflnet.gui.BinActions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BinActionsTest {
    @Test void ownAuctionPrimaryNeverClicksCancelSlot() {
        int primary = BinActions.primarySlot(AuctionStatus.OWN_AUCTION_CANCELING);
        assertNotEquals(BinActions.OWN_AUCTION_CANCEL_SLOT, primary);
        assertEquals(BinActions.AUCTION_GO_BACK_SLOT, primary);
    }

    @Test void ownAuctionExplicitCancelClicksCancelSlot() {
        assertEquals(BinActions.OWN_AUCTION_CANCEL_SLOT, BinActions.secondarySlot(AuctionStatus.OWN_AUCTION_CANCELING));
        assertTrue(BinActions.secondaryCancelsOwnAuction(AuctionStatus.OWN_AUCTION_CANCELING));
    }

    @Test void noStatusPrimaryClicksCancelAuctionSlot() {
        for (AuctionStatus s : AuctionStatus.values()) {
            assertNotEquals(BinActions.OWN_AUCTION_CANCEL_SLOT, BinActions.primarySlot(s), s.name());
        }
    }

    @Test void otherOverlaysUnchanged() {
        assertEquals(31, BinActions.primarySlot(AuctionStatus.INIT));
        assertEquals(31, BinActions.primarySlot(AuctionStatus.AUCTION_BUYING));
        assertEquals(11, BinActions.primarySlot(AuctionStatus.AUCTION_CONFIRMING));
        assertEquals(31, BinActions.primarySlot(AuctionStatus.OWN_AUCTION_CLAIMING));
        assertEquals(-1, BinActions.primarySlot(AuctionStatus.AUCTION_SOLD));
        assertEquals(49, BinActions.secondarySlot(AuctionStatus.INIT));
        assertEquals(15, BinActions.secondarySlot(AuctionStatus.AUCTION_CONFIRMING));
        assertEquals(49, BinActions.secondarySlot(AuctionStatus.OWN_AUCTION_CLAIMING));
        assertFalse(BinActions.secondaryCancelsOwnAuction(AuctionStatus.INIT));
    }
}
