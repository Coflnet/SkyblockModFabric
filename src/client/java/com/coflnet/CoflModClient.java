package com.coflnet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

import CoflCore.classes.Position;
import CoflCore.configuration.Config;
import CoflCore.configuration.GUIType;
import CoflCore.network.QueryServerCommands;
import CoflCore.network.WSClient;
import com.coflnet.gui.BinGUI;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.MinecraftVersion;
import net.minecraft.block.entity.*;
import net.minecraft.client.gui.screen.PopupScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.HoverEvent;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import com.coflnet.gui.RenderUtils;
import com.coflnet.gui.cofl.CoflBinGUI;
import com.coflnet.gui.tfm.TfmBinGUI;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.StringArgumentType;

import CoflCore.CoflCore;
import CoflCore.CoflSkyCommand;
import CoflCore.commands.Command;
import CoflCore.commands.CommandType;
import CoflCore.commands.RawCommand;
import CoflCore.commands.models.FlipData;
import CoflCore.handlers.DescriptionHandler;
import CoflCore.handlers.EventRegistry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.injection.Desc;

public class CoflModClient implements ClientModInitializer {
    public static final String targetVersion = "1.21.5";
    public static Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
    private static boolean keyPressed = false;
    private static int counter = 0;
    public static KeyBinding bestflipsKeyBinding;
    public static KeyBinding uploadItemKeyBinding;
    public static ArrayList<String> knownIds = new ArrayList<>();
    public static Pair<String, String> lastScoreboardUploaded = new Pair<>("","0");

    private String username = "";
    private static String lastNbtRequest = "";
    private boolean uploadedScoreboard = false;
    private static boolean popupShown = false;
    public static Position posToUpload = null;
    public static CoflModClient instance;
    public static SignBlockEntity sign = null;

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
        instance = this;
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
                "SkyCofl"));
        uploadItemKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "keybinding.coflmod.uploaditem",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                "SkyCofl"));

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
            if(uploadItemKeyBinding.wasPressed())
                handleGetHoveredItem(client);

        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (MinecraftClient.getInstance() != null && MinecraftClient.getInstance().getCurrentServerEntry() != null
                    && MinecraftClient.getInstance().getCurrentServerEntry().address.contains("hypixel.net")) {
                System.out.println("Connected to Hypixel");
                username = MinecraftClient.getInstance().getSession().getUsername();
                if (!CoflCore.Wrapper.isRunning && CoflCore.config.autoStart)
                    CoflSkyCommand.start(username);
                Thread.startVirtualThread(() -> {
                    try {
                        Thread.sleep(5000); // wait 5 seconds for the scoreboard to be populated
                        if(!CoflCore.Wrapper.isRunning)
                            return;
                        uploadScoreboard();
                        uploadTabList();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
            }
            DescriptionHandler.emptyTooltipData();
            uploadedScoreboard = false;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (CoflCore.Wrapper.isRunning) {
                System.out.println("Disconnected from server");
                CoflCore.Wrapper.stop();
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            registerDefaultCommands(dispatcher, "cofl");
            registerDefaultCommands(dispatcher, "cl");
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
                        String input = context.getInput();
                        String[] inputParts = input.split(" ");
                        String currentWord = inputParts.length > 0 ? inputParts[inputParts.length - 1] : "";

                        for (String suggestion : suggestions) {
                            if (suggestion.toLowerCase().startsWith(currentWord.toLowerCase())) {
                                builder.suggest(suggestion, new TooltipMessage("Will be replaced with the emoji"));
                            }
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
            if (screen instanceof GenericContainerScreen gcs && CoflCore.config.purchaseOverlay != null && gcs.getTitle() != null ) {
                // System.out.println(gcs.getTitle().getString());
                if (!(client.currentScreen instanceof BinGUI) && isBINAuction(gcs)) {
                    if (CoflCore.config.purchaseOverlay == GUIType.COFL) client.setScreen(new CoflBinGUI(gcs));
                    if (CoflCore.config.purchaseOverlay == GUIType.TFM) client.setScreen(new TfmBinGUI(gcs));
                }
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof HandledScreen hs) {
                knownIds.clear();
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
                        if (tooltip.line < 0 || tooltip.line >= lines.size()) {
                            System.out.println("Invalid line index: " + tooltip.line + " for tooltip: " + tooltip.value);
                            continue; // Skip if the line index is invalid
                        }
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
            // iterate over all components of the message
            String previousHover = null;
            for (Text component : message.getSiblings()) {
                if(component.getStyle().getHoverEvent() != null
                        && component.getStyle().getHoverEvent() instanceof HoverEvent.ShowText hest) {
                    String text = hest.value().getString();
                    if (text.equals(previousHover))
                        continue; // skip if the text is the same as the previous one, different colored text often has the same hover text
                    previousHover = text;
                    EventRegistry.onChatMessage(hest.value().getString());
                }
            }

            return true;
        });

        ScreenEvents.AFTER_INIT.register((minecraftClient, screen, i, i1) -> {
            if(!(MinecraftClient.getInstance().currentScreen instanceof TitleScreen)) return;
            if (!popupShown && !checkVersionCompability()) {
                popupShown = true;
                Screen currentScreen = MinecraftClient.getInstance().currentScreen;
                MinecraftClient.getInstance().setScreen(
                        new PopupScreen.Builder(currentScreen, Text.of("Warning"))
                                .button(Text.literal("Modrinth"), popupScreen -> {
                                    Util.getOperatingSystem().open("https://modrinth.com/mod/skycofl/versions");
                                })
                                .button(Text.of("Curseforge"), popupScreen -> {
                                    Util.getOperatingSystem().open("https://www.curseforge.com/minecraft/mc-mods/skycofl/files/all?page=1&pageSize=20");
                                })
                                .button(Text.of("dismiss"), popupScreen -> popupScreen.close())
                                .message(Text.of(
                                        "This version of the SkyCofl mod is meant for use in Minecraft "+
                                                targetVersion+" and likely won't work on this version."+
                                                "\nYou can find other versions of SkyCofl here:"
                                ))
                                .build()
                );
            }
        });

        ClientReceiveMessageEvents.GAME.register((text, b) -> {
            String[] scores = getScoreboard().toArray(new String[0]);
            if (scores == null || scores.length < 9) return;
            Pair<String,String> newData = getRelevantLinesFromScoreboard(scores);
            if (newData.getLeft().equals(lastScoreboardUploaded.getLeft()) && newData.getRight().equals(lastScoreboardUploaded.getRight())) return;
            System.out.println("Uploading Scoreboard...");
            uploadScoreboard();
        });

        UseBlockCallback.EVENT.register((playerEntity, world, hand, blockHitResult) -> {
            if(world.getBlockEntity(blockHitResult.getBlockPos()) instanceof LootableContainerBlockEntity lcbe){
                System.out.println("Lootable opened, saving position of lootable Block...");
                BlockPos pos = blockHitResult.getBlockPos();
                posToUpload = new Position(pos.getX(), pos.getY(), pos.getZ());
            }

            return ActionResult.SUCCESS;
        });

        WorldRenderEvents.LAST.register(worldRenderContext -> {
            if (EventSubscribers.positions == null || EventSubscribers.positions.size() == 0) return;
            for (Position position : EventSubscribers.positions) {
                RenderUtils.renderHighlightBox(
                        worldRenderContext,
                        new double[]{
                                position.getX(),
                                (double)position.getY() - 1.6,
                                position.getZ() - 1
                        }, new double[]{
                                position.getX() - 1,
                                (double)position.getY() - 0.6,
                                position.getZ()
                        },  new float[]{0.3f, 1f, 0.1f, 0.5f} // a=0.2f
                );
            }
        });

    }

    private void registerDefaultCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, String name) {
        dispatcher.register(ClientCommandManager.literal(name)
                .then(ClientCommandManager.argument("args", StringArgumentType.greedyString())
                .suggests((context, builder) -> {
                    String input = context.getInput();
                    String[] inputArgs = input.split(" ");;
                    String currentWord = inputArgs.length > 0 ? inputArgs[inputArgs.length - 1] : "";

                    String[] suggestions = {"start", "stop", "vps", "report", "online", "delay", "blacklist", "bl", "whitelist", "wl",
                            "mute", "blocked", "chat", "c", "nickname", "nick", "profit", "worstflips", "bestflips",
                            "leaderboard", "lb", "loserboard", "buyspeedboard", "trades", "flips", "set", "s",
                            "purchase", "buy", "transactions", "balance", "help", "h", "logout", "backup", "restore",
                            "captcha", "importtfm", "replayactive", "reminder", "filters", "emoji", "addremindertime",
                            "lore", "fact", "flip", "preapi", "transfercoins", "ping", "setgui tfm", "setgui cofl", "setgui off", "bazaar", "bz",
                            "switchregion", "craftbreakdown", "cheapattrib", "ca", "ownconfigs",
                            "configs", "config", "licenses", "license", "verify", "unverify", "attributeflip", "forge",
                            "crafts", "craft", "upgradeplan", "updatecurrentconfig", "settimezone", "cheapmuseum", "cm",
                            "replayflips", "lowball", "ahtax", "sethotkey"};

                    System.out.println(inputArgs.length + currentWord);
                    // Check if the command is "s" or "set" and suggest specific subcommands
                    if (inputArgs.length == 3 && (inputArgs[1].equals("s") || inputArgs[1].equals("set"))) {
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

                        for (String suggestion : suggestions) {
                            if (suggestion.toLowerCase().contains(currentWord.toLowerCase()))
                                builder.suggest("set " + suggestion);
                        }
                    } else if(inputArgs.length > 3)
                        return builder.buildFuture();
                    else
                        for (String suggestion : suggestions) {
                            if (suggestion.toLowerCase().startsWith(currentWord.toLowerCase())
                             || inputArgs.length == 1 // just /cofl should show all
                            )
                                builder.suggest(suggestion);
                        }
                    return builder.buildFuture();
                })
                .executes(context -> {
                    String[] args = context.getArgument("args", String.class).split(" ");
                    CoflSkyCommand.processCommand(args, username);
                    return 1;
                })));
    }

    private void handleGetHoveredItem(MinecraftClient client) {
         uploadItem(client.player.getInventory().getStack(client.player.getInventory().getSelectedSlot()));
    }

    public static void uploadItem(ItemStack hoveredStack) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || hoveredStack == null) return;

        RawCommand data = new RawCommand("hotkey", gson.toJson("upload_item" + getContextToAppend(hoveredStack)));
        CoflCore.Wrapper.SendMessage(data);
    }

    private static String getContextToAppend(ItemStack hoveredStack) {
        String toAppend = "";
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null)
            return "";

        DefaultedList<ItemStack> mockList = DefaultedList.of();
        mockList.add(hoveredStack);
        return "|" + inventoryToNBT(mockList);
    }

    private static void uploadTabList() {
        Command<String[]> data = new Command<>(CommandType.uploadTab, CoflModClient.getTabList().toArray(new String[0]));
        CoflCore.Wrapper.SendMessage(data);
    }

    private static void uploadScoreboard() {
        String[] scores = CoflModClient.getScoreboard().toArray(new String[0]);
        lastScoreboardUploaded = getRelevantLinesFromScoreboard(scores);
        Command<String[]> data = new Command<>(CommandType.uploadScoreboard, scores);
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
            nbtCompound = writeNbt(nbtCompound, itemStacks, player.getRegistryManager());
            System.out.println(nbtCompound.get("i").asString());

            NbtIo.writeCompressed(nbtCompound, baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());

        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static NbtCompound writeNbt(NbtCompound nbt, DefaultedList<ItemStack> stacks, RegistryWrapper.WrapperLookup registries) {
        NbtList nbtList = new NbtList();

        for(int i = 0; i < stacks.size(); ++i) {
            ItemStack itemStack = (ItemStack)stacks.get(i);
            if (!itemStack.isEmpty()) {
                NbtCompound nbtCompound = new NbtCompound();
                nbtCompound.putByte("Slot", (byte)i);
                nbtList.add((NbtElement)ItemStack.CODEC.encode(itemStack, registries.getOps(NbtOps.INSTANCE), nbtCompound).getOrThrow());
            } else {
                // If the stack is empty, we can still add an empty NbtCompound to keep the slot index and give the backend an easier time figuring out the structure
                NbtCompound nbtCompound = new NbtCompound();
                nbtList.add(nbtCompound);
            }
        }

        if (!nbtList.isEmpty()) {
            nbt.put("i", nbtList);
        }

        return nbt;
    }

    public static String[] getItemIdsFromInventory(DefaultedList<ItemStack> itemStacks) {
        ArrayList<String> res = new ArrayList<>();

        for (int i = 0; i < itemStacks.size(); i++) {
            ItemStack stack = itemStacks.get(i);
            if (stack.getItem() != Items.AIR) {
                String id = getIdFromStack(stack);
                knownIds.add(id);
                res.add(id);
            } else
                res.add("EMPTY_SLOT_" + i); // Add a placeholder for empty slots
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
        String itemName = stack.getCustomName() == null ? stack.getItem().getName().getString() : stack.getCustomName().getString();
        if (stackJson == null)
            return itemName + ";" + stack.getCount();

        JsonElement uuid = stackJson.get("uuid");
        if (uuid != null)
            return uuid.getAsString();
        // If "id" is not present, use the item's name
        return itemName + ";" + stack.getCount();
    }

    public void loadDescriptionsForInv(HandledScreen screen) {
        if (!MinecraftClient.getInstance().player.getInventory().getStack(8).getComponents().toString()
                .contains("minecraft:custom_data=>{id:\"SKYBLOCK_MENU\"}"))
            return;
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(100); // wait for the screen to load
                // TODO: check if items are already loaded earlier
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            DefaultedList<ItemStack> itemStacks = screen.getScreenHandler().getStacks();
            try {
                knownIds.clear();
                String title = screen.getTitle().getString();
                String[] visibleItems = getItemIdsFromInventory(itemStacks);
                loadDescriptionsForItems(title, itemStacks);
                if(title.contains("Bazaar"))
                {
                    System.out.println("Bazaar data: "
                            + inventoryToNBT(itemStacks));
                }
                Thread.sleep(1000);
                // check all items in the inventory for descriptions
                String[] itemIds = getItemIdsFromInventory(screen.getScreenHandler().getStacks());
                List<String> visibleList = Arrays.asList(visibleItems);
                for (String itemId : itemIds) {
                    if (!visibleList.contains((itemId))) {
                        loadDescriptionsForItems(title, screen.getScreenHandler().getStacks());
                        System.out.println("items changed, descriptions reloaded for " + itemId + " count: "
                        + itemIds.length + " visible: " + visibleItems.length);
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Failed to load descriptions for inventory: " + e + " "
                        + inventoryToNBT(itemStacks));
            }
        });
    }

    public static void loadDescriptionsForItems(String title, DefaultedList<ItemStack> items)
    {
        String[] visibleItems = getItemIdsFromInventory(items);
        String userName = MinecraftClient.getInstance().getSession().getUsername();
        String nbtString = inventoryToNBT(items);
        if(nbtString.equals(lastNbtRequest)) {
            return;
        }
        lastNbtRequest = nbtString;
        DescriptionHandler.loadDescriptionForInventory(
                visibleItems,
                title,
                nbtString,
                userName,
                posToUpload
        );
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
            List<PlayerListEntry> playerList = new ArrayList<>(networkHandler.getPlayerList());
            for (PlayerListEntry playerListEntry : playerList) {
                if (playerListEntry != null) {
                    // Get the display name (the text shown in the tab list)
                    if (playerListEntry.getDisplayName() != null) {
                        String displayName = playerListEntry.getDisplayName().getString();
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

    public static boolean isBINAuction(GenericContainerScreen gcs) {
        return (BinGUI.isAuctionInit(gcs) || BinGUI.isAuctionConfirming(gcs));
    }

    public static boolean isOwnAuction(GenericContainerScreen gcs) {
        ItemStack stack = gcs.getScreenHandler().getInventory().getStack(31);
        return (BinGUI.isAuctionInit(gcs) && (stack.getItem() == Items.GRAY_STAINED_GLASS_PANE || stack.getItem() == Items.GOLD_BLOCK));
    }

    private boolean checkVersionCompability() {
        try {
            Method m = MinecraftVersion.CURRENT.getClass().getDeclaredMethod("getName");
            String v = m.invoke(MinecraftVersion.CURRENT).toString();
            System.out.println("Detected Minecraft version:" + v);
            boolean b = v.compareTo(targetVersion) == 0;

            return b;
        } catch (Exception e) {
            if(targetVersion == "1.21.5")
                return true; // the method is only available in 1.21.6 so we assume this is .5
            return false;
        }
    }

    private static Pair<String, String> getRelevantLinesFromScoreboard(String[] scores){
        Pair<String, String> ids = new Pair<>("","null");

        for (String score : scores) {
            if (score.startsWith("Purse: ") || score.startsWith("Piggy: ")) ids.setLeft(score);
            if (score.startsWith(" ⏣ ")) ids.setRight(score);
        }

        return ids;
    }

    public static String findPriceSuggestion(){
        for (DescriptionHandler.DescModification descMod : getExtraSlotDescMod()) {
            System.out.println(descMod.type+"|"+descMod.value);
            if (descMod.type.compareTo("SUGGEST") == 0) return descMod.value;
        }

        return "";
    }

    public static DescriptionHandler.DescModification[] getExtraSlotDescMod(){
        return DescriptionHandler.getInfoDisplay();
    }
}
