package com.coflnet.core;

import com.coflnet.core.BazaarOrderLore.Side;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BazaarBookTest {
    /** Shape of /api/bazaar/{TAG}/snapshot: "buyOrders" rises from the cheapest, so it holds the sell offers. */
    static final String SNAPSHOT = """
            {"productId":"BOOSTER_COOKIE","buyMovingWeek":168000,"sellMovingWeek":84000,
             "buyOrders":[{"amount":5,"pricePerUnit":100.5,"orders":1},{"amount":12,"pricePerUnit":100.7,"orders":2}],
             "sellOrders":[{"amount":7,"pricePerUnit":99.4,"orders":1},{"amount":3,"pricePerUnit":99.1,"orders":1}],
             "timeStamp":"2026-10-18T21:01:02.793"}""";

    @Test void snapshotListsAreAssignedBySideNotByName() {
        BazaarBook book = BazaarBook.parse(SNAPSHOT);
        assertEquals(994, book.levels(Side.BUY).get(0).priceTenths(), "best buy order is the highest bid");
        assertEquals(1005, book.levels(Side.SELL).get(0).priceTenths(), "best sell offer is the lowest ask");
        assertEquals(500, book.fillsPerHour(Side.BUY), 1e-9, "insta-sells (sellMovingWeek) fill buy orders");
        assertEquals(1000, book.fillsPerHour(Side.SELL), 1e-9, "insta-buys (buyMovingWeek) fill sell offers");
    }

    @Test void correctlyNamedListsAreDetectedByPriceToo() {
        String swapped = SNAPSHOT.replace("\"buyOrders\"", "\"tmp\"").replace("\"sellOrders\"", "\"buyOrders\"")
                .replace("\"tmp\"", "\"sellOrders\"")
                .replace("buyMovingWeek", "tmpWeek").replace("sellMovingWeek", "buyMovingWeek").replace("tmpWeek", "sellMovingWeek");
        BazaarBook book = BazaarBook.parse(swapped);
        assertEquals(994, book.levels(Side.BUY).get(0).priceTenths());
        assertEquals(1005, book.levels(Side.SELL).get(0).priceTenths());
        assertEquals(500, book.fillsPerHour(Side.BUY), 1e-9, "the volume moves with its list");
    }

    @Test void oneSidedSnapshotTrustsTheDocumentedNaming() {
        BazaarBook book = BazaarBook.parse("{\"buyOrders\":[],\"sellOrders\":[{\"amount\":2,\"pricePerUnit\":5}]}");
        assertEquals(50, book.levels(Side.BUY).get(0).priceTenths());
        assertTrue(book.levels(Side.SELL).isEmpty());
    }

    @Test void malformedOrEmptySnapshotIsNull() {
        assertNull(BazaarBook.parse(""));
        assertNull(BazaarBook.parse("{\"buyOrders\":[],\"sellOrders\":[]}"));
        assertNull(BazaarBook.parse("[1,2]"));
    }

    @Test void searchPicksTheBazaarEntry() {
        String cookie = "[{\"name\":\"Booster Cookie §8(Untradable)\",\"id\":\"REFUND_COOKIE\"},"
                + "{\"name\":\"Booster Cookie - bazaar\",\"id\":\"BOOSTER_COOKIE\"},"
                + "{\"name\":\"Booster Cookie Jar - bazaar\",\"id\":\"COOKIE_JAR\"}]";
        assertEquals("BOOSTER_COOKIE", BazaarBook.tagFromSearch(cookie, "Booster Cookie"));
        assertNull(BazaarBook.tagFromSearch("[{\"name\":\"Enchanted Diamond - bazaar\",\"id\":\"ENCHANTED_DIAMOND\"}]",
                "Enchanted Diamond Block"), "a single unrelated result stays unknown");
        assertNull(BazaarBook.tagFromSearch("[{\"name\":\"vampirism 6 enchant - bazaar\",\"id\":\"ENCHANTMENT_VAMPIRISM_6\"}]",
                "Vampirism VI"), "a differently named entry is not matched");
        assertNull(BazaarBook.tagFromSearch("[{\"name\":\"Hyperion\",\"id\":\"HYPERION\"}]", "Hyperion"));
        assertNull(BazaarBook.tagFromSearch("not json", "x"));
    }

    @Test void unchangedRefreshEqualsTheCachedBook() {
        assertEquals(BazaarBook.parse(SNAPSHOT), BazaarBook.parse(SNAPSHOT));
        assertNotEquals(BazaarBook.parse(SNAPSHOT), BazaarBook.parse(SNAPSHOT.replace("\"amount\":5,", "\"amount\":6,")));
        assertNotEquals(BazaarBook.parse(SNAPSHOT), BazaarBook.parse(SNAPSHOT.replace("168000", "168001")));
    }
}
