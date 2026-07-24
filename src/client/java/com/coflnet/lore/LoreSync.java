package com.coflnet.lore;

import CoflCore.CoflCore;
import CoflCore.CoflSkyCommand;
import CoflCore.commands.RawCommand;
import CoflCore.network.WSClient;
import CoflCore.network.WSClientWrapper;
import com.coflnet.config.LoreManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * reads and writes the whole lore settings through the backend json path
 * replacing the old chat menu scraping and the one command at a time driver.
 *
 * The Coflnet backend exposes its {@code DescriptionSetting} object directly:
 *
 *   <li>READ — sending {@code lore json} makes the server reply with a
 *       {@code loreSettings} socket message carrying the object as JSON. The
 *       reply lands in {@link #onBackendJson(WSClient, String)}.</li>
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
    // first. a completion or rejection releases the slot. an unknown timed out write
    // keeps the slot until the session resets so a later echo cannot confirm a newer save.
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
    private static volatile List<LoreModule> pendingModules = null;
    private static volatile WSClient currentSource = null;
    private static volatile WSClient pendingSource = null;
    private static volatile int pendingSession = 0;
    private static final AtomicInteger session = new AtomicInteger();

    public static boolean hasReceived() {
        return received;
    }

    public static void resetSession() {
        session.incrementAndGet();
        current = null;
        currentSource = null;
        received = false;
        pendingPayload = null;
        pendingModules = null;
        pendingSource = null;
        pendingSession = 0;
        lastSaveMs = 0L;
        activeSave.set(0);
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
            } else {
                msg("§cNot connected to Coflnet. Run §e/cofl start§c, then reopen the lore editor.");
            }
        } catch (RuntimeException exception) {
            System.out.println("[Lore] requestFromBackend failed: " + exception);
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
    public static void onBackendJson(WSClient source, String json) {
        DescriptionSettings parsed = DescriptionSettings.parse(json);
        if (parsed == null || !parsed.isCompleteSnapshot()) {
            System.out.println("[Lore] ignoring incomplete loreSettings payload");
            return;
        }
        int receivedSession = session.get();
        if (!isCurrentSource(source)) {
            return;
        }
        // this lands on the websocket reading thread. marshal everything incl the guard
        // checks onto the client thread so it never races the tooltip render iterating
        // modules and so stale verifying are re evaluated at execution time a save that
        // started between receipt and the deferred run must be seen or a stale read
        // would revert the just saved state on disk.
        Runnable apply = () -> {
            if (session.get() != receivedSession || !isCurrentSource(source)) {
                return;
            }
            boolean verifying = activeSave.get() != 0;
            // a read within the cache window of our own write is stale the backend json
            // cache does not reflect the write yet so ignore it entirely.
            boolean stale = System.currentTimeMillis() - lastSaveMs < BACKEND_CACHE_MS;
            if (stale) {
                return;
            }
            received = true;
            current = parsed;
            currentSource = source;
            List<List<String>> fields = parsed.getFields();
            LoreManager.setSyncedLayout(fields);
            // adopt synced styling only on a genuine non verify read. applytomodules is
            // authoritative for every restylable field present key sets it incl empty to
            // show stock absent or a null all default blob resets it to the default so a
            // reset made on another instance propagates here.
            if (!verifying) {
                LoreStyleCodec.applyToModules(parsed.getCustomFormat(), LoreManager.getModules());
                LoreManager.saveModules(LoreManager.getModules());
                com.coflnet.gui.cofl.LoreConfigScreen.onLayoutCaptured(fields);
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
    public static void save(
            List<List<String>> layout,
            List<LoreModule> modules,
            boolean templatesChanged) {
        DescriptionSettings base = current;
        if (base == null || !received) {
            System.out.println("[Lore] refusing to save: no backend settings received yet");
            msg("\u00A7cCould not read your current lore settings from the server, so the save "
                    + "was skipped to avoid resetting your other settings. Reopen the lore GUI "
                    + "to retry, and if it keeps failing the server json read may not be live yet.");
            return;
        }

        WSClientWrapper wrapper = CoflCore.getWrapper();
        if (wrapper == null || !wrapper.isRunning || wrapper.socket == null
                || wrapper.socket != currentSource) {
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
        DescriptionSettings payload;
        String json;
        try {
            payload = base.copy();
            payload.setFields(layout);
            if (templatesChanged) {
                payload.setCustomFormat(
                        LoreStyleCodec.mergeInto(base.getCustomFormat(), modules));
            }
            json = payload.toJson();
        } catch (RuntimeException exception) {
            System.out.println("[Lore] refusing to save an unsafe settings payload: " + exception);
            msg("§cYour lore settings contain unsupported or oversized styling, so nothing was saved.");
            return;
        }

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
        pendingModules = copyModules(modules);
        pendingSource = wrapper.socket;
        pendingSession = session.get();
        // start the stale read window the backends json read cache wont reflect this
        // write for up to a minute so reads until then must not revert it.
        lastSaveMs = System.currentTimeMillis();

        try {
            // processcommand sendcommandtoserver gson encodes the arg to a quoted
            // json string the backend recognises the leading quote brace and
            // deserialises the whole descriptionsetting in one atomic update then
            // echoes imported settings check above .
            CoflSkyCommand.processCommand(new String[]{"lore", json}, user);
        } catch (RuntimeException exception) {
            System.out.println("[Lore] save send failed: " + exception);
            if (activeSave.compareAndSet(gen, 0)) {
                clearFailedSave();
                failVerify("I could not send your lore to the server (" + exception + "). It was not saved.");
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
            if (activeSave.get() == gen) {
                failVerify("the server never confirmed your lore save. If your lore did not "
                        + "change in game, reconnect before trying again. The result is unknown.");
            }
        }, "cofl-lore-verify");
        verifier.setDaemon(true);
        verifier.start();

    }

    /**
     * called for backend replies while a save is being verified. the backend
     * echoes {@code Imported settings (check above)} on a successful atomic import,
     * or a parse error if it could not read the JSON. This
     * turns those echoes into the user facing save result. no op when no save is
     * in flight.
     */
    public static boolean hasPendingSave() {
        return activeSave.get() != 0;
    }

    public static void observeSaveResponse(
            WSClient source,
            LoreSaveResponse.Result result) {
        int g = activeSave.get();
        if (g <= 0 || result == null || result == LoreSaveResponse.Result.NONE
                || source != pendingSource
                || pendingSession != session.get()
                || !isCurrentSource(source)) {
            return;
        }
        if (result == LoreSaveResponse.Result.REJECTED) {
            if (!activeSave.compareAndSet(g, 0)) {
                return;
            }
            clearFailedSave();
            failVerify("the server rejected your lore save. The server settings were not changed.");
            return;
        }
        if (!activeSave.compareAndSet(g, -g)) {
            return;
        }

        DescriptionSettings accepted = pendingPayload;
        List<LoreModule> acceptedModules = pendingModules;
        int acceptedSession = pendingSession;
        clearPending();
        runOnClient(() -> {
            try {
                if (session.get() != acceptedSession || !isCurrentSource(source)
                        || accepted == null) {
                    return;
                }
                current = accepted;
                currentSource = source;
                LoreManager.commitConfirmed(accepted.getFields(), acceptedModules);
                com.coflnet.CoflModClient.refreshOpenInventoryDescriptions();
                msg("\u00A7aLore settings saved and confirmed on the server.");
            } finally {
                activeSave.compareAndSet(-g, 0);
            }
        });
    }

    private static void failVerify(String reason) {
        System.out.println("[Lore] save verification failed: " + reason);
        msg("\u00A7c" + reason);
    }

    private static void clearFailedSave() {
        clearPending();
        lastSaveMs = 0L;
    }

    private static void clearPending() {
        pendingPayload = null;
        pendingModules = null;
        pendingSource = null;
        pendingSession = 0;
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
            } catch (RuntimeException exception) {
                System.out.println("[Lore] could not post chat message: " + exception);
            }
        });
    }

    private static boolean isCurrentSource(WSClient source) {
        WSClientWrapper wrapper = CoflCore.getWrapper();
        return source != null && wrapper != null && wrapper.isRunning
                && wrapper.socket == source;
    }

    private static void runOnClient(Runnable action) {
        net.minecraft.client.Minecraft mc =
                net.minecraft.client.Minecraft.getInstance();
        if (mc != null) {
            mc.execute(action);
        } else {
            action.run();
        }
    }

    private static List<LoreModule> copyModules(List<LoreModule> source) {
        List<LoreModule> copy = new ArrayList<>();
        if (source != null) {
            for (LoreModule module : source) {
                if (module != null) {
                    copy.add(new LoreModule(
                            module.name,
                            module.match,
                            module.template));
                }
            }
        }
        return copy;
    }
}
