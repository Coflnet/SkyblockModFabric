package com.coflnet.core;

import CoflCore.commands.models.ChatMessageData;
import CoflCore.commands.models.FlipData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class FlipMessagesTest {
    @AfterEach
    void clear() {
        FlipMessages.clear();
    }

    @Test
    void keepsOnlyRecentFlipsAndForgetsOldOnes() {
        for (int i = 0; i <= FlipMessages.MAX_ENTRIES + 4; i++) {
            open("auction-" + i, "item-" + i, new Object(), i);
        }

        Object later = new Object();
        assertEquals(FlipMessages.MAX_ENTRIES, FlipMessages.size());
        assertNull(FlipMessages.forScreen(later, "item-0", 30), "evicted");
        String lastItem = "item-" + (FlipMessages.MAX_ENTRIES + 4);
        assertEquals("auction-" + (FlipMessages.MAX_ENTRIES + 4), FlipMessages.forScreen(later, lastItem, 30).Id);
        assertNull(FlipMessages.forScreen(later, lastItem, 30 + FlipMessages.FLIP_TTL_MS), "expired");
    }

    @Test
    void requestedScreenOpeningLateStillShowsItsFlip() {
        FlipData flip = flip("auction-a");
        Object view = new Object();
        FlipMessages.opened(flip, 0);
        FlipMessages.containerOpened(view, true);

        assertSame(flip, FlipMessages.forScreen(view, "item-a", 60_000));
    }

    @Test
    void sameNamedAuctionOpenedAfterTheRequestOpenedNothingGetsNoFlip() {
        Object auctionHouse = new Object(), view = new Object(), reopenedView = new Object();
        FlipMessages.opened(flip("auction-a"), 0);
        FlipMessages.containerOpened(auctionHouse, false); // the request failed, the player opens the auction house
        FlipMessages.containerOpened(view, true); // another auction of the same item

        assertNull(FlipMessages.forScreen(view, "item-b", 1));
        FlipMessages.containerOpened(reopenedView, true); // that auction's screen opened again
        assertNull(FlipMessages.forScreen(reopenedView, "item-b", 2));
    }

    @Test
    void typedViewAuctionOfAnotherAuctionReplacesTheFlipRequest() {
        Object view = new Object();
        FlipMessages.opened(flip("auction-a"), 0);
        FlipMessages.commandSent("/viewauction auction-b");
        FlipMessages.containerOpened(view, true);

        assertNull(FlipMessages.forScreen(view, "item-b", 1));
    }

    @Test
    void requestedScreenKeepsItsFlipThroughPlaceholderItemsReinitAndConfirm() {
        FlipData flip = flip("auction-a");
        Object view = new Object(), confirm = new Object();
        FlipMessages.opened(flip, 0);
        FlipMessages.containerOpened(view, true);

        assertSame(flip, FlipMessages.forScreen(view, null, 1), "placeholder without uuid");
        FlipMessages.containerOpened(view, true);
        assertSame(flip, FlipMessages.forScreen(view, "item-a", 2), "same screen initialised again");
        FlipMessages.containerOpened(confirm, true);
        assertSame(flip, FlipMessages.forScreen(confirm, "item-a", 3), "confirm screen");
    }

    @Test
    void reopenedFlipOfTheSameItemShowsTheNewAuction() {
        open("auction-a", "item-a", new Object(), 0);
        FlipData relisted = open("auction-b", "item-a", new Object(), 10);

        assertSame(relisted, FlipMessages.forScreen(new Object(), "item-a", 20));
    }

    private static FlipData open(String auctionId, String itemUuid, Object menu, long now) {
        FlipData flip = flip(auctionId);
        FlipMessages.opened(flip, now);
        FlipMessages.containerOpened(menu, true);
        assertSame(flip, FlipMessages.forScreen(menu, itemUuid, now));
        return flip;
    }

    private static FlipData flip(String auctionId) {
        return new FlipData(new ChatMessageData[]{new ChatMessageData(auctionId, null, null)}, auctionId, 0, null, null);
    }
}
