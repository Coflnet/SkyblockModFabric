package com.coflnet.gui.hud;

import com.coflnet.core.InfoDisplayExpiry;
import com.coflnet.core.InfoDisplayPayload;
import com.coflnet.gui.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Holds the current content of the 1-3 permanent HUD "info displays" pushed
 * from the backend. Updates can arrive from the websocket thread (via
 * {@link com.coflnet.EventSubscribers#onReceiveCommand}) or the render/client
 * thread (via the {@code /cofl display} command); reads happen every frame on
 * the render thread. Each display's content is stored as a single immutable
 * {@link Snapshot} swapped atomically so a reader never observes a half-built
 * update and the render path never needs to rebuild components or measure
 * text - that all happens once, here, when a payload is applied.
 * <p>
 * {@code apply}/{@code clear}/{@code clearAll}/{@code demo} all self-hop onto
 * the render thread (via {@code Minecraft.execute}) before touching anything.
 * That's required, not just polite: {@link #build} calls {@code Font.width},
 * and Minecraft's {@code Font}/{@code StringSplitter}/glyph caches are NOT
 * thread-safe, so measuring text from the websocket thread could corrupt them.
 * Hopping also keeps a burst of calls from the same off-thread caller (e.g. a
 * {@code clear} right after an {@code apply}) applied in the order they were
 * issued, since they all funnel through the same render-thread task queue.
 */
public final class InfoDisplayManager {
    public static final int DISPLAY_COUNT = 3;

    // Index 0 is unused so display ids (1-3) can be used directly as the index.
    private static final AtomicReferenceArray<Snapshot> SNAPSHOTS = new AtomicReferenceArray<>(DISPLAY_COUNT + 1);

    private InfoDisplayManager() {
    }

    /** Render-ready, immutable content for one display. */
    public static final class Snapshot {
        public final int id;
        public final Component title;
        public final List<Component> lines;
        public final int titleWidth;
        public final int maxWidth;
        public final int contentHeight;
        public final int lineHeight;
        public final long expiresAt;

        private Snapshot(int id, Component title, List<Component> lines, int titleWidth, int maxWidth,
                          int contentHeight, int lineHeight, long expiresAt) {
            this.id = id;
            this.title = title;
            this.lines = lines;
            this.titleWidth = titleWidth;
            this.maxWidth = maxWidth;
            this.contentHeight = contentHeight;
            this.lineHeight = lineHeight;
            this.expiresAt = expiresAt;
        }
    }

    /**
     * Applies a parsed payload, replacing that display's content (or clearing
     * it, if {@link InfoDisplayPayload#clear}). Self-hops onto the render
     * thread if called from elsewhere (see the class Javadoc) - the payload is
     * already an immutable parsed object, so re-dispatching it is safe.
     */
    public static void apply(InfoDisplayPayload payload) {
        if (payload == null || payload.id < 1 || payload.id > DISPLAY_COUNT) {
            return;
        }
        if (!hopToRenderThread(() -> apply(payload))) {
            return;
        }
        if (payload.clear) {
            clear(payload.id);
            return;
        }
        SNAPSHOTS.set(payload.id, build(payload));
    }

    /** Self-hops onto the render thread; see the class Javadoc. */
    public static void clear(int id) {
        if (id < 1 || id > DISPLAY_COUNT) {
            return;
        }
        if (!hopToRenderThread(() -> clear(id))) {
            return;
        }
        SNAPSHOTS.set(id, null);
    }

    /** Self-hops onto the render thread; see the class Javadoc. */
    public static void clearAll() {
        if (!hopToRenderThread(InfoDisplayManager::clearAll)) {
            return;
        }
        for (int id = 1; id <= DISPLAY_COUNT; id++) {
            SNAPSHOTS.set(id, null);
        }
    }

    /**
     * If not already on the render thread, re-dispatches {@code retry} there via
     * {@code Minecraft.execute} and returns false so the caller stops (the
     * re-dispatched call will do the actual work once it runs). Returns true
     * when it's safe to proceed immediately (already on the render thread, or
     * no client instance exists yet to hop through).
     */
    private static boolean hopToRenderThread(Runnable retry) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.isSameThread()) {
            return true;
        }
        mc.execute(retry);
        return false;
    }

    /** Returns the current snapshot for a display, or null if empty/expired/never set. */
    public static Snapshot snapshot(int id) {
        if (id < 1 || id > DISPLAY_COUNT) {
            return null;
        }
        Snapshot snap = SNAPSHOTS.get(id);
        if (snap == null) {
            return null;
        }
        if (InfoDisplayExpiry.isExpired(System.currentTimeMillis(), snap.expiresAt)) {
            // Lazily drop expired content so later reads stay a cheap null check.
            SNAPSHOTS.compareAndSet(id, snap, null);
            return null;
        }
        return snap;
    }

    /**
     * Fills all three displays with sample multi-line content, for testing/positioning via /cofl displays.
     * Self-hops onto the render thread; see the class Javadoc.
     */
    public static void demo() {
        if (!hopToRenderThread(InfoDisplayManager::demo)) {
            return;
        }
        apply(new InfoDisplayPayload(1, "§6§lFlips", List.of(
                new InfoDisplayPayload.InfoDisplayLine("§a[BIN] §fHyperion §7-> §a1.2b profit", null, null),
                new InfoDisplayPayload.InfoDisplayLine("§a[BIN] §fNecron's Handle §7-> §a45m profit", null, null)
        ), 0, false));
        apply(new InfoDisplayPayload(2, "§b§lBazaar", List.of(
                new InfoDisplayPayload.InfoDisplayLine("§fEnchanted Diamond §7- §a+3.2% §7margin", null, null),
                new InfoDisplayPayload.InfoDisplayLine("§fEnchanted Lapis §7- §a+1.8% §7margin", null, null)
        ), 0, false));
        apply(new InfoDisplayPayload(3, "§d§lStatus", List.of(
                new InfoDisplayPayload.InfoDisplayLine("§7Connected §a●", "§7sky.coflnet.com", null)
        ), 0, false));
    }

    private static Snapshot build(InfoDisplayPayload payload) {
        Font font = font();

        Component title = (payload.title == null || payload.title.isEmpty()) ? null : Component.literal(payload.title);
        int titleWidth = (title != null && font != null) ? font.width(title) : 0;

        List<Component> lines = new ArrayList<>(payload.lines.size());
        int maxWidth = titleWidth;
        for (InfoDisplayPayload.InfoDisplayLine line : payload.lines) {
            Component comp = toComponent(line);
            lines.add(comp);
            int width = font != null ? font.width(comp) : 0;
            if (width > maxWidth) {
                maxWidth = width;
            }
        }

        int lineHeight = font != null ? font.lineHeight : 9;
        int contentHeight = (title != null ? lineHeight + 2 : 0) + lines.size() * lineHeight;
        long expiresAt = InfoDisplayExpiry.computeExpiresAt(System.currentTimeMillis(), payload.ttlSeconds);

        return new Snapshot(payload.id, title, List.copyOf(lines), titleWidth, maxWidth, contentHeight, lineHeight, expiresAt);
    }

    private static Font font() {
        if (RenderUtils.textRenderer != null) {
            return RenderUtils.textRenderer;
        }
        Minecraft client = Minecraft.getInstance();
        return client != null ? client.font : null;
    }

    /** Mirrors {@code com.coflnet.Utils.ChatComponent}'s onClick/hover style handling for display lines. */
    private static Component toComponent(InfoDisplayPayload.InfoDisplayLine line) {
        MutableComponent comp = Component.literal(line.text);

        String onClick = line.onClick;
        if (onClick != null && !onClick.isBlank()) {
            if (onClick.startsWith("http")) {
                try {
                    URI uri = URI.create(onClick);
                    comp.withStyle(style -> style.withClickEvent(new ClickEvent.OpenUrl(uri)));
                } catch (IllegalArgumentException ignored) {
                    // Malformed URL in a pushed payload; leave the line non-clickable rather than fail the update.
                }
            } else if (onClick.startsWith("suggest:")) {
                String suggestion = onClick.substring("suggest:".length());
                comp.withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand(suggestion)));
            } else if (onClick.startsWith("copy:")) {
                String copyText = onClick.substring("copy:".length());
                comp.withStyle(style -> style.withClickEvent(new ClickEvent.CopyToClipboard(copyText)));
            } else {
                comp.withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(onClick)));
            }
        }

        if (line.hover != null && !line.hover.isBlank()) {
            String hover = line.hover;
            comp.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
        }

        return comp;
    }
}
