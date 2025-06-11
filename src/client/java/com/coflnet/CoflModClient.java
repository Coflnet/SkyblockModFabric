package com.coflnet;

import CoflCore.CoflCore;
import CoflCore.classes.*;
import CoflCore.CoflSkyCommand;
import CoflCore.commands.Command;
import CoflCore.commands.CommandType;
import CoflCore.commands.models.FlipData;
import CoflCore.configuration.Config;
import CoflCore.configuration.Configuration;
import CoflCore.configuration.LocalConfig;
import CoflCore.events.OnSettingsReceive;
import CoflCore.handlers.DescriptionHandler;
import CoflCore.handlers.EventRegistry;
import CoflCore.network.QueryServerCommands;
import CoflCore.network.WSClient;
import com.mojang.brigadier.Message;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import com.coflnet.gui.RenderUtils;
import com.coflnet.gui.cofl.CoflBinGUI;
import com.coflnet.gui.tfm.TfmBinGUI;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.minecraft.client.ObjectMapper;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancement.criterion.InventoryChangedCriterion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.tooltip.HoveredTooltipPositioner;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.Component;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.InventoryChangedListener;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.scoreboard.*;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerListener;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CoflModClient implements ClientModInitializer {
    public static Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
    private static boolean keyPressed = false;
    private static int counter = 0;
    public static KeyBinding bestflipsKeyBinding;
    public static ArrayList<String> knownIds = new ArrayList<>();

    private String username = "";
    private boolean uploadedScoreboard = false;

    public class TooltipMessage implements  Message{
        private final String text;

        public TooltipMessage(String text) {
            this.text = text;
        }
        @Override
        public String getString() {
            return text;
        }
    }

    @Override
    public void onInitializeClient() {
        username = MinecraftClient.getInstance().getSession().getUsername();
        Path configDir = FabricLoader.getInstance().getConfigDir();
        CoflCore cofl = new CoflCore();
        cofl.init(configDir);
        cofl.registerEventFile(new EventSubscribers());

        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            RenderUtils.init();
        });

        bestflipsKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "keybinding.coflmod.bestflips",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                ""));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (bestflipsKeyBinding.isPressed()) {
                if (counter == 0) {
                    EventRegistry.onOpenBestFlip(username, true);
                }
                if (counter < 2)
                    counter++;
            } else {
                counter = 0;
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (MinecraftClient.getInstance() != null && MinecraftClient.getInstance().getCurrentServerEntry() != null
                    && MinecraftClient.getInstance().getCurrentServerEntry().address.contains("hypixel.net")) {
                System.out.println("Connected to Hypixel");
                username = MinecraftClient.getInstance().getSession().getUsername();
                if (!CoflCore.Wrapper.isRunning && CoflCore.config.autoStart)
                    CoflSkyCommand.start(username);
            }
            uploadedScoreboard = false;
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("cofl")
                    .then(ClientCommandManager.argument("args", StringArgumentType.greedyString())
                    .suggests((context, builder) -> {
                        String input = context.getInput();
                        String[] inputArgs = input.split(" ");

                        String[] suggestions = {"start", "stop", "report", "online", "delay", "blacklist", "bl", "whitelist", "wl",
                                "mute", "blocked", "chat", "c", "nickname", "nick", "profit", "worstflips", "bestflips",
                                "leaderboard", "lb", "loserboard", "buyspeedboard", "trades", "flips", "set", "s",
                                "purchase", "buy", "transactions", "balance", "help", "h", "logout", "backup", "restore",
                                "captcha", "importtfm", "replayactive", "reminder", "filters", "emoji", "addremindertime",
                                "lore", "fact", "flip", "preapi", "transfercoins", "ping", "setgui", "bazaar", "bz",
                                "switchregion", "craftbreakdown", "cheapattrib", "ca", "attributeupgrade", "au", "ownconfigs",
                                "configs", "config", "licenses", "license", "verify", "unverify", "attributeflip", "forge",
                                "crafts", "craft", "upgradeplan", "updatecurrentconfig", "settimezone", "cheapmuseum", "cm",
                                "replayflips", "lowball", "ahtax", "sethotkey"};


                        // Check if the command is "s" or "set" and suggest specific subcommands
                        if (inputArgs.length >= 2 && (inputArgs[1].equals("s") || inputArgs[1].equals("set"))) {
                            suggestions = new String[] {"lbin", "finders", "onlyBin", "whitelistAftermain", "DisableFlips",
                                    "DebugMode", "blockHighCompetition", "minProfit", "minProfitPercent", "minVolume", "maxCost",
                                    "modjustProfit", "modsoundOnFlip", "modshortNumbers", "modshortNames", "modblockTenSecMsg",
                                    "modformat", "modblockedFormat", "modchat", "modcountdown", "modhideNoBestFlip", "modtimerX",
                                    "modtimerY", "modtimerSeconds", "modtimerScale", "modtimerPrefix", "modtimerPrecision",
                                    "modblockedMsg", "modmaxPercentOfPurse", "modnoBedDelay", "modstreamerMode", "modautoStartFlipper",
                                    "modnormalSoldFlips", "modtempBlacklistSpam", "moddataOnlyMode", "modahListHours", "modquickSell",
                                    "modmaxItemsInInventory", "moddisableSpamProtection", "showcost", "showestProfit", "showlbin",
                                    "showslbin", "showmedPrice", "showseller", "showvolume", "showextraFields", "showprofitPercent",
                                    "showprofit", "showsellerOpenBtn", "showlore", "showhideSold", "showhideManipulated",
                                    "privacyExtendDescriptions", "privacyAutoStart", "loreHighlightFilterMatch",
                                    "loreMinProfitForHighlight", "loreDisableHighlighting"};
                        }
                        for (String suggestion : suggestions) {
                            builder.suggest(suggestion);
                        }
                        return builder.buildFuture();
                    })
                    .executes(context -> {
                        String[] args = context.getArgument("args", String.class).split(" ");
                        CoflSkyCommand.processCommand(args, username);
                        return 1;
                    })));
            dispatcher.register(ClientCommandManager.literal("fc")
                    .then(ClientCommandManager.argument("args", StringArgumentType.greedyString())
                    .suggests((context, builder) -> {

                        String[] suggestions = {":tableflip:", ":sad:", ":smile:", ":grin:", ":heart:", ":skull:", ":airplane:", ":check:", "<3",
                                ":star:", ":yes:", ":no:", ":java:", ":arrow", ":shrug:", "o/", ":123:", ":totem:", ":typing:",
                                ":maths:", ":snail:", ":thinking:", ":gimme:", ":wizard:", ":pvp:", ":peace:", ":oof:", ":puffer:",
                                ":yey:", ":cat:", ":dab:", ":dj:", ":snow:", ":^_^:", ":^-^:", ":sloth:", ":cute:", ":dog:",
                                ":fyou:", ":angwyflip:", ":snipe:", ":preapi:", ":tm:", ":r:", ":c:", ":crown:", ":fire:",
                                ":sword:", ":shield:", ":cross:", ":star1:", ":star2:", ":star3:", ":star4:", ":rich:", ":boop:",
                                ":yay:", ":gg:"};
                        for (String suggestion : suggestions) {
                            builder.suggest(suggestion, new TooltipMessage("Will be replaced with the emoji"));
                        }
                        return builder.buildFuture();
                    })
                    .executes(context -> {
                        String[] args = context.getArgument("args", String.class).split(" ");
                        String[] newArgs = new String[args.length + 1];
                        System.arraycopy(args, 0, newArgs, 1, args.length);
                        newArgs[0] = "chat";
                        CoflSkyCommand.processCommand(newArgs, username);
                        return 1;
                    })));
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof GenericContainerScreen gcs) {
                // System.out.println(gcs.getTitle().getString());
                if (CoflCore.config.purchaseOverlay != null && gcs.getTitle() != null
                        && (gcs.getTitle().getString().contains("BIN Auction View")
                                && gcs.getScreenHandler().getInventory().size() == 9 * 6
                                || gcs.getTitle().getString().contains("Confirm Purchase")
                                        && gcs.getScreenHandler().getInventory().size() == 9 * 3)) {
                    if (!(client.currentScreen instanceof CoflBinGUI || client.currentScreen instanceof TfmBinGUI)) {
                        switch (CoflCore.config.purchaseOverlay) {
                            case COFL:
                                client.setScreen(new CoflBinGUI(gcs));
                                break;
                            case TFM:
                                client.setScreen(new TfmBinGUI(gcs));
                                break;
                            case null:
                            default:
                                break;
                        }
                    }
                }
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof HandledScreen hs) {
                loadDescriptionsForInv(hs);
                if(!uploadedScoreboard)
                {
                    uploadScoreboard();
                    uploadTabList();
                    uploadedScoreboard = true;
                }
            }
        });

        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {
            if (knownIds.indexOf(getIdFromStack(stack)) == -1
                    && MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?> hs) {
                loadDescriptionsForInv(hs);
                return;
            }

            DescriptionHandler.DescModification[] tooltips = DescriptionHandler.getTooltipData(getIdFromStack(stack));
            for (DescriptionHandler.DescModification tooltip : tooltips) {
                switch (tooltip.type) {
                    case "APPEND":
                        lines.add(Text.of(tooltip.value + " "));
                        break;
                    case "REPLACE":
                        lines.remove(tooltip.line);
                        lines.add(tooltip.line, Text.of(tooltip.value));
                        break;
                    case "INSERT":
                        lines.add(tooltip.line, Text.of(tooltip.value));
                        break;
                    case "DELETE":
                        lines.remove(tooltip.line);
                        break;
                    case "HIGHLIGHT":
                        if (MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?> hs) {
                            // hs.getScreenHandler().getSlot(hs.getScreenHandler().getStacks().indexOf(stack));
                        }
                        break;
                    default:
                        System.out.println("Unknown type: " + tooltip.type);
                }
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (EventSubscribers.showCountdown && EventSubscribers.countdownData != null
                    && (MinecraftClient.getInstance().currentScreen == null
                            || MinecraftClient.getInstance().currentScreen instanceof ChatScreen)) {
                RenderUtils.drawStringWithShadow(
                        drawContext,
                        EventSubscribers.countdownData.getPrefix() + "New flips in: "
                                + String.format("%.1f", EventSubscribers.countdown),
                        MinecraftClient.getInstance().getWindow().getWidth()
                                / EventSubscribers.countdownData.getWidthPercentage(),
                        MinecraftClient.getInstance().getWindow().getHeight()
                                / EventSubscribers.countdownData.getHeightPercentage(),
                        0xFFFFFFFF, EventSubscribers.countdownData.getScale());
            }
        });

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            EventRegistry.onChatMessage(message.getString());
            return true;
        });
    }

    private static void uploadTabList() {
        Command<String[]> data = new Command<>(CommandType.uploadTab, CoflModClient.getTabList().toArray(new String[0]));
        CoflCore.Wrapper.SendMessage(data);
    }

    private static void uploadScoreboard() {
        Command<String[]> data = new Command<>(CommandType.uploadScoreboard, CoflModClient.getScoreboard().toArray(new String[0]));
        CoflCore.Wrapper.SendMessage(data);
    }

    public static FlipData popFlipData() {
        FlipData fd = EventSubscribers.flipData;
        EventSubscribers.flipData = null;
        return fd;
    }

    public static SoundEvent findByName(String name) {
        SoundEvent result = SoundEvents.BLOCK_NOTE_BLOCK_BELL.value();

        for (Field f : SoundEvents.class.getDeclaredFields()) {
            if (f.getName().equalsIgnoreCase(name)) {
                try {
                    try {
                        result = (SoundEvent) f.get(SoundEvent.class);
                    } catch (ClassCastException e) {
                        result = (SoundEvent) ((RegistryEntry.Reference) f.get(RegistryEntry.Reference.class)).value();
                    }
                } catch (IllegalAccessException e) {
                    System.out.println("SoundEvent inaccessible. This shouldn't happen");
                }
                break;
            }
        }
        return result;
    }

    public static DefaultedList<ItemStack> inventoryToItemStacks(Inventory inventory) {
        DefaultedList<ItemStack> itemStacks = DefaultedList.of();

        for (int i = 0; i < inventory.size(); i++) {
            itemStacks.add(inventory.getStack(i));
        }

        return itemStacks;
    }

    public static String inventoryToNBT(Inventory inventory) {
        return inventoryToNBT(inventoryToItemStacks(inventory));
    }

    public static String[] getItemIdsFromInventory(Inventory inventory) {
        return getItemIdsFromInventory(inventoryToItemStacks(inventory));
    }

    public static String inventoryToNBT(DefaultedList<ItemStack> itemStacks) {
        NbtCompound nbtCompound = new NbtCompound();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PlayerEntity player = MinecraftClient.getInstance().player;

        try {
            Inventories.writeNbt(nbtCompound, itemStacks, player.getRegistryManager());
            nbtCompound.put("i", nbtCompound.get("Items"));
            nbtCompound.remove("Items");

            // System.out.println(nbtCompound.get("i").asString());

            NbtIo.writeCompressed(nbtCompound, baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());

        } catch (IOException e) {
        }
        return "";
    }

    public static String[] getItemIdsFromInventory(DefaultedList<ItemStack> itemStacks) {
        ArrayList<String> res = new ArrayList<>();
        knownIds.clear();

        for (int i = 0; i < itemStacks.size(); i++) {
            ItemStack stack = itemStacks.get(i);
            if (stack.getItem() != Items.AIR) {
                String id = getIdFromStack(stack);
                knownIds.add(id);
                res.add(id);
            }
        }

        return res.toArray(String[]::new);
    }

    public static String getIdFromStack(ItemStack stack) {
        JsonObject stackJson = null;
        for (ComponentType<?> type : stack.getComponents().getTypes()) {
            if (type.toString().contains("minecraft:custom_data")) {
                stackJson = gson.fromJson(stack.get(type).toString(), JsonObject.class);
            }
        }
        if (stackJson == null)
            return "";

        JsonElement uuid = stackJson.get("uuid");
        if (uuid != null)
            return uuid.getAsString();
        JsonElement idElement = stackJson.get("id");
        if (idElement != null) {
            return idElement.getAsString() + ";" + stack.getCount();
        }
        // If "id" is not present, use the item's name
        return stack.getItem().getName().getString() + ";" + stack.getCount();
    }

    public static void loadDescriptionsForInv(HandledScreen screen) {
        DefaultedList<ItemStack> itemStacks = screen.getScreenHandler().getStacks();
        if (!MinecraftClient.getInstance().player.getInventory().getStack(8).getComponents().toString()
                .contains("minecraft:custom_data=>{id:\"SKYBLOCK_MENU\"}"))
            return;
        DescriptionHandler.emptyTooltipData();

        Thread.startVirtualThread(() -> {
            try {
                DescriptionHandler.loadDescriptionForInventory(
                        getItemIdsFromInventory(itemStacks),
                        screen.getTitle().getString(),
                        inventoryToNBT(itemStacks),
                        MinecraftClient.getInstance().getSession().getUsername());
            } catch (Exception e) {
                System.out.println("Failed to load descriptions for inventory: " + e.getMessage() + " "
                        + inventoryToNBT(itemStacks));
            }
        });
    }

    private static List<String> getScoreboard() {
        ObjectArrayList<String> scoreboardAsText = new ObjectArrayList<>();
        if (MinecraftClient.getInstance() == null || MinecraftClient.getInstance().world == null) {
            System.out.println("MinecraftClient or world is null, cannot get scoreboard.");
            return scoreboardAsText;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            System.out.println("Player is null, cannot get scoreboard.");
            return scoreboardAsText;
        }

        Scoreboard scoreboard = player.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.FROM_ID.apply(1));

        for (ScoreHolder scoreHolder : scoreboard.getKnownScoreHolders()) {
            if (!scoreboard.getScoreHolderObjectives(scoreHolder).containsKey(objective))
                continue;
            Team team = scoreboard.getScoreHolderTeam(scoreHolder.getNameForScoreboard());

            if (team != null) {
                String strLine = team.getPrefix().getString() + team.getSuffix().getString();

                if (!strLine.trim().isEmpty()) {
                    String formatted = Formatting.strip(strLine);
                    scoreboardAsText.add(formatted);
                }
            }
        }

        if (objective != null) {
            scoreboardAsText.add(objective.getDisplayName().getString());
            Collections.reverse(scoreboardAsText);
        }
        return scoreboardAsText;
    }

    private static List<String> getTabList() {
        List<String> tabList = new ArrayList<>();
        if (MinecraftClient.getInstance() == null || MinecraftClient.getInstance().world == null) {
            System.out.println("MinecraftClient or world is null, cannot get tab list.");
            return tabList;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();

        if (networkHandler != null) {
            // Get the collection of player list entries
            for (PlayerListEntry playerListEntry : networkHandler.getPlayerList()) {
                if (playerListEntry != null) {
                    // Get the display name (the text shown in the tab list)
                    if (playerListEntry.getDisplayName() != null) {
                        String displayName = playerListEntry.getDisplayName().getString();
                         System.out.println(displayName);
                        tabList.add(displayName);
                    } else {
                        String playerName = playerListEntry.getProfile().getName();
                        tabList.add(playerName);
                    }
                }
            }
        }
        return tabList;
    }
}
