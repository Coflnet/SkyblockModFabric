package com.coflnet.core;

import java.util.Objects;

/** Re-upload the tab list when the footer text changed, at most once per debounce window. */
public final class TabFooterUploadGate {
    private final long debounceMs;
    private String lastFooter;
    private long lastUploadAt;
    private boolean uploadedOnce;

    public TabFooterUploadGate(long debounceMs) {
        this.debounceMs = debounceMs;
    }

    /** A rejected change is retried by the next packet, so the final footer value is never lost for good. */
    public synchronized boolean shouldUpload(String footer, long now) {
        if (uploadedOnce && Objects.equals(footer, lastFooter)) return false;
        if (uploadedOnce && now - lastUploadAt < debounceMs) return false;
        uploadedOnce = true;
        lastFooter = footer;
        lastUploadAt = now;
        return true;
    }

    public synchronized void reset() {
        uploadedOnce = false;
        lastFooter = null;
    }
}
