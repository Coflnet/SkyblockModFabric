package com.coflnet.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TradeChatHoldTest {
    private static final String COMPLETED = "Trade completed with [MVP+] Pv69!";

    @Test
    void completionWaitsForTheLastQueuedUploadNotAnEarlierOne() {
        var hold = new TradeChatHold();
        List<String> sent = new ArrayList<>();

        hold.uploadQueued(1); // own boots
        hold.uploadQueued(2); // the other player's 110M coins, queued while upload 1 is in flight
        assertNotEquals(TradeChatHold.NOT_HELD, hold.hold("§6" + COMPLETED, () -> sent.add("chat")));
        assertEquals(TradeChatHold.NOT_HELD, hold.hold("You have 10 unclaimed auctions", () -> {}),
                "other lines are not held");

        assertTrue(hold.uploadFinished(1).isEmpty(), "the boots-only upload must not release the completion");
        sent.add("coins upload");
        hold.uploadFinished(2).forEach(Runnable::run);
        assertEquals(List.of("coins upload", "chat"), sent);
        assertEquals(TradeChatHold.NOT_HELD, hold.hold(COMPLETED, () -> {}), "nothing left to wait for");
    }

    @Test
    void timeoutReleasesOnlyItsOwnLineAcrossConsecutiveTrades() {
        var hold = new TradeChatHold();
        hold.uploadQueued(1);
        long first = hold.hold(COMPLETED, () -> {});
        hold.uploadFinished(1);
        assertNull(hold.timeout(first), "already forwarded after its upload");

        hold.uploadQueued(2); // the next trade's final window
        Runnable second = () -> {};
        long secondToken = hold.hold(COMPLETED, second);
        assertNull(hold.timeout(first), "the first trade's timeout must not release the next trade");
        assertTrue(hold.isHolding());
        assertSame(second, hold.timeout(secondToken));
        assertTrue(hold.uploadFinished(2).isEmpty(), "a timed-out line is forwarded only once");
    }
}
