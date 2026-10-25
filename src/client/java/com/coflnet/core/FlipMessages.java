package com.coflnet.core;

import CoflCore.commands.models.FlipData;

/**
 * Remembers the flips the player opened so the purchase overlay of a flip's auction shows the flip message on
 * every screen of that auction. The client never sees an auction id on an auction screen, so the trust is the
 * one the overlay always had: the next auction screen after the flip's {@code /viewauction <id>} request
 * belongs to that flip. Re-creating that screen keeps the message, and the uuid of the item it shows carries
 * it to the screens of the same item (confirm, reopened views). Any other screen shows nothing. A request
 * the server answers with an unrelated auction is attributed to the flip, as before.
 */
public final class FlipMessages {
    static final int MAX_ENTRIES = 16;
    static final long FLIP_TTL_MS = 5 * 60_000L;
    private static final String VIEW_AUCTION = "/viewauction ";

    private record Remembered(FlipData flip, long openedAt) {}

    private static final BoundedLruMap<String, Remembered> flipByAuction = new BoundedLruMap<>(MAX_ENTRIES);
    private static final BoundedLruMap<String, String> auctionByItem = new BoundedLruMap<>(MAX_ENTRIES);
    /** Auction requested with {@code /viewauction} whose screen has not opened yet. */
    private static String requestedAuction;
    /** Menu of the container screen opened last; the server reuses container ids, so the instance is the key. */
    private static Object screenMenu;
    /** Auction of the screen showing {@link #screenMenu}, {@code null} when it was not requested. */
    private static String screenAuction;

    private FlipMessages() {}

    /** Called when a flip's auction is opened with {@code /viewauction}. */
    public static synchronized void opened(FlipData flip, long now) {
        if (flip == null || flip.Id == null) return;
        flipByAuction.put(flip.Id, new Remembered(flip, now));
        requestedAuction = flip.Id;
    }

    /** Called for every command the player sends; a {@code /viewauction} replaces the requested auction. */
    public static synchronized void commandSent(String command) {
        if (command != null && command.startsWith(VIEW_AUCTION)) {
            requestedAuction = command.substring(VIEW_AUCTION.length()).trim();
        }
    }

    /**
     * Called when a container screen opens. The first one after a request answers it: an auction screen
     * belongs to the requested auction, any other screen means the request opened nothing.
     * @param menu the screen's menu instance; a new container from the server is a new instance
     */
    public static synchronized void containerOpened(Object menu, boolean auctionScreen) {
        if (menu == null || menu == screenMenu) return; // the same screen initialised again
        screenMenu = menu;
        screenAuction = auctionScreen ? requestedAuction : null;
        requestedAuction = null;
    }

    /**
     * Returns the flip of the auction shown in the container, without consuming it, or {@code null} when
     * the auction is unknown.
     * @param itemUuid uuid of the auctioned item, {@code null} when it has none
     */
    public static synchronized FlipData forScreen(Object menu, String itemUuid, long now) {
        String auctionId = menu != null && menu == screenMenu ? screenAuction : null;
        if (auctionId != null && itemUuid != null) auctionByItem.put(itemUuid, auctionId);
        if (auctionId == null && itemUuid != null) auctionId = auctionByItem.get(itemUuid);
        if (auctionId == null) return null;
        Remembered remembered = flipByAuction.get(auctionId);
        if (remembered == null || now - remembered.openedAt() > FLIP_TTL_MS) return null;
        return remembered.flip();
    }

    static synchronized int size() {
        return Math.max(flipByAuction.size(), auctionByItem.size());
    }

    static synchronized void clear() {
        flipByAuction.clear();
        auctionByItem.clear();
        requestedAuction = null;
        screenMenu = null;
        screenAuction = null;
    }
}
