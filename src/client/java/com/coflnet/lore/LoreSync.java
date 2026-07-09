package com.coflnet.lore;

import CoflCore.CoflCore;
import CoflCore.CoflSkyCommand;
import CoflCore.commands.RawCommand;
import CoflCore.network.WSClientWrapper;
import com.coflnet.config.LoreManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * reads and writes the whole lore settings through the backend json path 
 * replacing the old chat menu scraping and the one command at a time driver.
 *  
 * The Coflnet backend exposes its {@code DescriptionSetting} object directly:
 *  
 *   <li>READ — sending {@code lore json} makes the server reply with a
 *       {@code loreSettings} socket message carrying the object as JSON. The
 *       reply lands in {@link #onBackendJson(String)}.</li>
 *   <li>WRITE — running {@code lore} with the JSON object as its argument saves
 *  the whole object at once. one atomic update no re indexing no throttle. 
 *  
 * the write replaces the entire stored object so we always start from the last
 * object the backend sent us ({@link #current}) and only mutate the two members
 * we own — {@code fields} (layout) and {@code customFormat} (our styling blob) —
 * carrying every other setting through untouched. See {@link DescriptionSettings}.
 *  
 *  every save is verified against the server. after the write the backend
 * echoes {@code Imported settings (check above)} once it has accepted and applied
 * the object that echo is the confirmation. if instead the server rejects the
 * json or nothing is heard within a timeout the user is told exactly why in a
 * chat message. a save is never reported as done on faith.
 *  
 * NOTE: the backend's {@code /cofl lore json} READ returns a value cached for up
 * to a minute and the write does not refresh that cache so a read back
 * immediately after a write returns stale data. verification therefore keys on
 * the write echo not a re read. reported to the dev to fix the cache. 
 */
public final class LoreSync {

    private LoreSync() {
    }

    /** the last full settings object the backend sent or null before the first read. */
    private static volatile DescriptionSettings current = null;

    /** true once at least one backend object has been received this session. */
    private static volatile boolean received = false;

    //  verification state a save in flight 

    /** how long to wait for the backends write echo before warning ms . */
    private static final long VERIFY_TIMEOUT_MS = 6000;

    // the backend caches the /cofl lore json read for up to a minute and a write does
    // not refresh that cache documented above so a read landing within this window of
    // our own write reflects pre write state. we ignore such stale reads for layout
    // styling and current so a late open time read cannot revert a save we just made.
    private static final long BACKEND_CACHE_MS = 60000;
    private static volatile long lastSaveMs = 0L;

    // the generation of the in flight save 0 = none. a save claims the slot with
    // compareandset 0 gen so only one is ever verifying at a time the backend echoes
    // are not tagged so a second overlapping save could have its echo credited to the
    // first. a completion the success echo a reject or the watchdog timeout releases
    // the slot with compareandset gen 0 so exactly one of the three reports the result
    // across the three threads no double confirm no lost update.
    private static final java.util.concurrent.atomic.AtomicInteger activeSave =
            new java.util.concurrent.atomic.AtomicInteger(0);
    // hands out a fresh never reused generation for each save. it only ever increments
    // so a stale watchdog from a finished save can never match a later save. the old
    // scheme reset the counter to 0 and re incremented back to 1 which let a stale
    // watchdog clear a newer save by matching the reused number.
    private static final java.util.concurrent.atomic.AtomicInteger genCounter =
            new java.util.concurrent.atomic.AtomicInteger(0);
    // the payload of the in flight save promoted to current only once the backend
    // confirms so a rejected or timed out write can never leave a stale current.
    private static volatile DescriptionSettings pendingPayload = null;

    public static boolean hasReceived() {
        return received;
    }

    /** the layout from the last backend object or an empty layout before any read. */
    public static List<List<String>> getLayout() {
        DescriptionSettings s = current;
        return s == null ? new ArrayList<>() : s.getFields();
    }

    //  read 

    /**
     * Asks the backend for the current settings object via {@code /cofl lore json}.
     *  
     * the backend gates the json reply on the argument being the bare string
     * {@code json}. The normal command helper gson-quotes its argument (yielding
     * {@code "json"}), which misses that check, so we send the raw command
     * directly with an unquoted {@code data} field.
     */
    public static void requestFromBackend() {
        try {
            WSClientWrapper wrapper = CoflCore.getWrapper();
            RawCommand rc = new RawCommand("lore", "json");
            if (wrapper != null && wrapper.isRunning) {
                wrapper.SendMessage(rc);
            } else if (wrapper != null) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                String user = (mc != null && mc.getUser() != null) ? mc.getUser().getName() : "";
                wrapper.startConnection(user);
                wrapper.SendMessage(rc);
            }
        } catch (Throwable t) {
            System.out.println("[Lore] requestFromBackend failed: " + t);
        }
    }

    /**
     * Handles a {@code loreSettings} JSON payload from the backend. Stores the
     * full object mirrors its layout and adopts synced styling. runs on the
     * websocket reading thread.
     *  
     * this is not used to verify a save the backend caches the json read for up to a
     * minute and the write does not refresh it so a post write read returns stale
     * data. a read within that window of our own write is ignored entirely so it
     * cannot revert the layout styling or {@link #current} we just wrote.
     */
    public static void onBackendJson(String json) {
        DescriptionSettings parsed = DescriptionSettings.parse(json);
        if (parsed == null) {
            System.out.println("[Lore] ignoring unparseable loreSettings payload");
            return;
        }
        received = true;
        // this lands on the websocket reading thread. marshal everything incl the guard
        // checks onto the client thread so it never races the tooltip render iterating
        // modules and so stale verifying are re evaluated at execution time a save that
        // started between receipt and the deferred run must be seen or a stale read
        // would revert the just saved state on disk.
        Runnable apply = () -> {
            boolean verifying = activeSave.get() != 0;
            // a read within the cache window of our own write is stale the backend json
            // cache does not reflect the write yet so ignore it entirely.
            boolean stale = System.currentTimeMillis() - lastSaveMs < BACKEND_CACHE_MS;
            if (stale) {
                return;
            }
            current = parsed;
            // only mirror a real fields array a partial or malformed reply that omitted
            // fields must never wipe or revert the layout.
            if (parsed.hasFields()) {
                LoreManager.setSyncedLayout(parsed.getFields());
            }
            // adopt synced styling only on a genuine non verify read. applytomodules is
            // authoritative for every restylable field present key sets it incl empty to
            // show stock absent or a null all default blob resets it to the default so a
            // reset made on another instance propagates here.
            if (!verifying) {
                LoreStyleCodec.applyToModules(parsed.getCustomFormat(), LoreManager.getModules());
                LoreManager.saveModules(LoreManager.getModules());
                // hand the open gui the fresh layout and styling always even an empty
                // layout so it can drop its loading header and re copy the synced
                // templates . onLayoutCaptured guards against clobbering live edits.
                com.coflnet.gui.cofl.LoreConfigScreen.onLayoutCaptured(parsed.getFields());
            }
        };
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null) {
            mc.execute(apply);
        } else {
            apply.run();
        }
    }

    //  write verify 

    /**
     * saves the given layout and module styling to the backend as one whole
     * object preserving every unrelated setting then verifies the write was
     * accepted (via the backend's {@code Imported settings} echo) and refreshes
     * the open inventory. any failure is surfaced to the user with the reason.
     */
    public static void save(List<List<String>> layout, List<LoreModule> modules) {
        DescriptionSettings base = current;
        if (base == null || !received) {
            System.out.println("[Lore] refusing to save: no backend settings received yet");
            msg("\u00A7cCould not read your current lore settings from the server, so the save "
                    + "was skipped to avoid resetting your other settings. Reopen the lore GUI "
                    + "to retry, and if it keeps failing the server json read may not be live yet.");
            return;
        }

        WSClientWrapper wrapper = CoflCore.getWrapper();
        if (wrapper == null || !wrapper.isRunning) {
            System.out.println("[Lore] refusing to save: not connected to Coflnet");
            msg("\u00A7cNot connected to Coflnet, so your lore was not saved. Run \u00A7e/cofl start"
                    + "\u00A7c and try again.");
            return;
        }

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null || mc.getUser() == null) {
            return;
        }
        String user = mc.getUser().getName();

        // build the write payload from a copy of the last confirmed object mutating
        // only the two members we own fields and customformat . every other setting
        // is carried through untouched so the atomic replace on the backend cannot
        // wipe an unrelated setting and because we never touch current itself a
        // rejected or timed out write leaves the last confirmed state intact. this is
        // pure local work so an exception here claims no slot and leaks no state.
        DescriptionSettings payload = base.copy();
        payload.setFields(layout);
        payload.setCustomFormat(LoreStyleCodec.fromModules(modules));
        String json = payload.toJson();

        // claim the single in flight save slot with a fresh never reused generation
        // before publishing pendingpayload. refuse to start a second save while one is
        // still being verified the untagged backend echoes cannot be told apart so an
        // overlap could credit one saves confirmation to the other.
        final int gen = genCounter.incrementAndGet();
        if (!activeSave.compareAndSet(0, gen)) {
            System.out.println("[Lore] a save is already being verified, refusing to overlap");
            msg("§eA lore save is still being confirmed. Give it a moment, then save again.");
            return;
        }
        pendingPayload = payload;
        // start the stale read window the backends json read cache wont reflect this
        // write for up to a minute so reads until then must not revert it.
        lastSaveMs = System.currentTimeMillis();

        try {
            // processcommand sendcommandtoserver gson encodes the arg to a quoted
            // json string the backend recognises the leading quote brace and
            // deserialises the whole descriptionsetting in one atomic update then
            // echoes imported settings check above .
            CoflSkyCommand.processCommand(new String[]{"lore", json}, user);
        } catch (Throwable t) {
            System.out.println("[Lore] save send failed: " + t);
            if (activeSave.compareAndSet(gen, 0)) {
                failVerify("I could not send your lore to the server (" + t + "). It was not saved.");
            }
            return;
        }

        // watchdog if the backend never echoes acceptance or a rejection within the
        // timeout warn we could not confirm. only fires if this generation is still
        // the active save a newer save or a confirmation already cleared it .
        Thread verifier = new Thread(() -> {
            try {
                Thread.sleep(VERIFY_TIMEOUT_MS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
            if (activeSave.compareAndSet(gen, 0)) {
                failVerify("the server never confirmed your lore save. If your lore did not "
                        + "change in game, tell the dev. Your other settings were not touched.");
            }
        }, "cofl-lore-verify");
        verifier.setDaemon(true);
        verifier.start();

        // make the change visible immediately the echo confirms it stuck.
        com.coflnet.CoflModClient.refreshOpenInventoryDescriptions();
    }

    /**
     * called for backend chat lines while a save is being verified. the backend
     * echoes {@code Imported settings (check above)} on a successful atomic import,
     * or an {@code invalid_arguments} error if it could not parse the JSON. This
     * turns those echoes into the user facing save result. no op when no save is
     * in flight.
     */
    public static void observeChat(String plain) {
        if (activeSave.get() == 0 || plain == null) {
            return;
        }
        String lower = plain.toLowerCase(Locale.ROOT);
        // the backend echoes exactly imported settings check above on success.
        // require both phrases so a stray player chat line saying imported settings
        // cannot fake a confirmation during the verify window.
        if (lower.contains("imported settings") && lower.contains("check above")) {
            int g = activeSave.get();
            if (g != 0 && activeSave.compareAndSet(g, 0)) {
                DescriptionSettings p = pendingPayload;
                if (p != null) {
                    current = p;   // promote to confirmed only now the server accepted.
                }
                msg("\u00A7aLore settings saved and confirmed on the server.");
            }
            return;
        }
        // the backend rejects a bad write with a specific code y phrase a player is
        // unlikely to type kept tight to avoid a false rejection.
        if (lower.contains("could not parse the arguments")
                || lower.contains("invalid_arguments")) {
            int g = activeSave.get();
            if (g != 0 && activeSave.compareAndSet(g, 0)) {
                failVerify("the server rejected your lore save: \u00A7f" + stripCodes(plain)
                        + "\u00A7c. Nothing was changed.");
            }
        }
    }

    private static void failVerify(String reason) {
        System.out.println("[Lore] save verification failed: " + reason);
        msg("\u00A7c" + reason);
    }

    //  helpers 

    /** posts a chat message to the local player on the client thread. */
    private static void msg(String text) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        mc.execute(() -> {
            try {
                com.coflnet.CoflModClient.sendChatMessage(text);
            } catch (Throwable t) {
                System.out.println("[Lore] could not post chat message: " + t);
            }
        });
    }

    private static String stripCodes(String s) {
        return s == null ? "" : s.replaceAll("\u00A7.", "").trim();
    }
}
