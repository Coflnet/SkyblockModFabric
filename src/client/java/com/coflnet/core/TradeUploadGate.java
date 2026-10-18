package com.coflnet.core;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;

/**
 * Decides whether the trade window has to be uploaded again. Every change of the offered slots
 * (either side, coins included) is uploaded, an unchanged offer only after a failed upload's retry
 * delay. No Minecraft types, so the decision is unit-testable.
 *
 * @param <T> one offered slot (an item stack in the mod)
 */
public final class TradeUploadGate<T> {
    public static final long RETRY_DELAY_MS = 2_000L;

    private final BiPredicate<T, T> sameSlot;
    private final UnaryOperator<T> snapshot;
    private List<T> lastOffered = List.of();
    private long retryAfter;

    public TradeUploadGate(BiPredicate<T, T> sameSlot, UnaryOperator<T> snapshot) {
        this.sameSlot = sameSlot;
        this.snapshot = snapshot;
    }

    /** @return true if {@code offered} must be uploaded now; it then becomes the last uploaded offer */
    public boolean shouldUpload(List<T> offered, long now) {
        boolean retryDue = retryAfter != 0L && now >= retryAfter;
        if (!retryDue && sameOffer(offered))
            return false;
        lastOffered = offered.stream().map(snapshot).toList();
        retryAfter = 0L;
        return true;
    }

    /** The last upload failed; send the unchanged offer again once the retry delay passed. */
    public void failed(long now) {
        retryAfter = now + RETRY_DELAY_MS;
    }

    public boolean isRetryDue(long now) {
        return retryAfter != 0L && now >= retryAfter;
    }

    /** Copies of the slots of the last uploaded offer. */
    public List<T> lastOffered() {
        return lastOffered;
    }

    public void reset() {
        lastOffered = List.of();
        retryAfter = 0L;
    }

    private boolean sameOffer(List<T> offered) {
        if (offered.size() != lastOffered.size())
            return false;
        for (int i = 0; i < offered.size(); i++) {
            if (!sameSlot.test(offered.get(i), lastOffered.get(i)))
                return false;
        }
        return true;
    }
}
