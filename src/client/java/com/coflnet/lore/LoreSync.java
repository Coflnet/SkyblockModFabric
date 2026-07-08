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

    private static volatile boolean verifyPending = false;
    private static volatile int verifyGeneration = 0;     // invalidates stale watchdogs

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
     * this is not used to verify a save the backend caches the json read for up
     * to a minute and the write does not refresh it so a post write read returns
     * stale data. During a pending save we still refresh {@link #current} for the
     * next edit but never confirm fail from it.
     */
    public static void onBackendJson(String json) {
        DescriptionSettings parsed = DescriptionSettings.parse(json);
        if (parsed == null) {
            System.out.println("[Lore] ignoring unparseable loreSettings payload");
            return;
        }
        current = parsed;
        received = true;

        List<List<String>> layout = parsed.getFields();
        LoreManager.setSyncedLayout(layout);

        // adopt synced styling only on a genuine non verify read during our own
        // verify the modules already hold what we just wrote.
        if (!verifyPending) {
            String customFormat = parsed.getCustomFormat();
            if (customFormat != null) {
                LoreStyleCodec.applyToModules(customFormat, LoreManager.getModules());
                LoreManager.saveModules(LoreManager.getModules());
            }
            com.coflnet.gui.cofl.LoreConfigScreen.onLayoutCaptured(layout);
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

        // mutate only the two members we own on the last received object. every
        // other setting is carried through untouched proven by tests so the
        // atomic replace on the backend cannot wipe an unrelated setting.
        base.setFields(layout);
        base.setCustomFormat(LoreStyleCodec.fromModules(modules));
        String json = base.toJson();
        current = base;

        verifyPending = true;
        final int gen = ++verifyGeneration;

        try {
            // processcommand sendcommandtoserver gson encodes the arg to a quoted
            // json string the backend recognises the leading quote brace and
            // deserialises the whole descriptionsetting in one atomic update then
            // echoes imported settings check above .
            CoflSkyCommand.processCommand(new String[]{"lore", json}, user);
        } catch (Throwable t) {
            System.out.println("[Lore] save send failed: " + t);
            verifyPending = false;
            verifyGeneration++;
            failVerify("I could not send your lore to the server (" + t + "). It was not saved.");
            return;
        }

        // watchdog if the backend never echoes acceptance or a rejection within
        // the timeout warn we could not confirm the save.
        Thread verifier = new Thread(() -> {
            try {
                Thread.sleep(VERIFY_TIMEOUT_MS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
            if (gen == verifyGeneration && verifyPending) {
                verifyPending = false;
                verifyGeneration++;
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
        if (!verifyPending || plain == null) {
            return;
        }
        String lower = plain.toLowerCase(Locale.ROOT);
        if (lower.contains("imported settings")) {
            verifyPending = false;
            verifyGeneration++;
            msg("\u00A7aLore settings saved and confirmed on the server.");
            return;
        }
        if (lower.contains("could not parse the arguments")
                || lower.contains("invalid_arguments")
                || lower.contains("invalid arguments")) {
            verifyPending = false;
            verifyGeneration++;
            failVerify("the server rejected your lore save: \u00A7f" + stripCodes(plain)
                    + "\u00A7c. Nothing was changed.");
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
