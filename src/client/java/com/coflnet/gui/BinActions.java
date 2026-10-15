package com.coflnet.gui;

/**
 * Decides which chest slot the BIN overlays click for their primary and secondary (cancel) actions.
 * Pure logic, kept free of Minecraft types so it can be unit tested.
 * <p>
 * In the player's own auction the primary action must never cancel the auction; only the explicit
 * secondary "Cancel Auction" control may do so.
 */
public final class BinActions {
    public static final int AUCTION_BUY_SLOT = 31;
    /** "Go Back" slot of the BIN Auction View. */
    public static final int AUCTION_GO_BACK_SLOT = 49;
    public static final int AUCTION_CONFIRM_SLOT = 11;
    public static final int AUCTION_CONFIRMATION_CANCEL_SLOT = 15;
    public static final int OWN_AUCTION_CLAIM_SLOT = 31;
    public static final int OWN_AUCTION_CANCEL_SLOT = 33;

    private BinActions() {
    }

    /** Slot clicked by the primary action (big button, click anywhere), or -1 if there is none. */
    public static int primarySlot(AuctionStatus status) {
        if (status == null) {
            return -1;
        }
        switch (status) {
            case INIT:
            case AUCTION_BUYING:
            case AUCTION_WAITING:
                return AUCTION_BUY_SLOT;
            case AUCTION_CONFIRMING:
                return AUCTION_CONFIRM_SLOT;
            case OWN_AUCTION_CLAIMING:
                return OWN_AUCTION_CLAIM_SLOT;
            case OWN_AUCTION_CANCELING:
                // never the cancel slot: the primary action only goes back
                return AUCTION_GO_BACK_SLOT;
            default:
                return -1;
        }
    }

    /** Slot clicked by the secondary action. In an own cancelable auction this is the explicit cancel-auction control. */
    public static int secondarySlot(AuctionStatus status) {
        if (status == AuctionStatus.AUCTION_CONFIRMING) {
            return AUCTION_CONFIRMATION_CANCEL_SLOT;
        }
        if (status == AuctionStatus.OWN_AUCTION_CANCELING) {
            return OWN_AUCTION_CANCEL_SLOT;
        }
        return AUCTION_GO_BACK_SLOT;
    }

    /** Whether the secondary action cancels the player's own auction (and so needs an explicit label). */
    public static boolean secondaryCancelsOwnAuction(AuctionStatus status) {
        return status == AuctionStatus.OWN_AUCTION_CANCELING;
    }
}
