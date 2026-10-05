package com.coflnet.protection.auction;

/**
 * Identifies the Hypixel auction screen that is being protected.
 *
 * <p>This must stay outside {@code com.coflnet.mixin}. Classes in that package
 * are owned by Sponge Mixin and must not be referenced directly by injected
 * target methods.</p>
 */
public enum AuctionScreenMode {
    SELLER,
    BIDDER
}
