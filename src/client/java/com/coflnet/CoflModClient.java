package com.coflnet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

import CoflCore.classes.Position;
import CoflCore.classes.Settings;
import CoflCore.commands.models.HotkeyRegister;
import CoflCore.configuration.GUIType;
import com.coflnet.gui.BinGUI;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.MinecraftVersion;
import net.minecraft.block.entity.*;
import net.minecraft.client.gui.screen.PopupScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.tooltip.TooltipType;
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
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;

public class CoflModClient implements ClientModInitializer {
    public static final String targetVersion = "1.21.11";
    public static final int InventorysizeWithOffHand = 5 * 9 + 1;
    public static Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
    private static boolean keyPressed = false;
    private static int counter = 0;
    private static final KeyBinding.Category SKYCOFL_CATEGORY = KeyBinding.Category.create(Identifier.of("coflnet", "skycofl"));
    public static KeyBinding bestflipsKeyBinding;
    public static KeyBinding uploadItemKeyBinding;
    public static List<KeyBinding> additionalKeyBindings = new ArrayList<KeyBinding>();
    public static Map<KeyBinding, HotkeyRegister> keybindingsToHotkeys = new HashMap<KeyBinding, HotkeyRegister>();
    public static ArrayList<String> knownIds = new ArrayList<>();
    public static Pair<String, String> lastScoreboardUploaded = new Pair<>("","0");

    private String username = "";
    private static String lastNbtRequest = "";
    private boolean uploadedScoreboard = false;
    private static boolean popupShown = false;
    public static Position posToUpload = null;
    public static CoflModClient instance;
    public static SignBlockEntity sign = null;
    public static String pendingBazaarSearch = null;
    
    // Staggered refresh tracking: inventory name -> last request time
    private static final Map<String, Long> lastRefreshTimePerInventory = new HashMap<>();
    private static final long REFRESH_THROTTLE_MS = 500; // 0.5 seconds minimum between requests
    
    // Maps new UUIDs to original UUID when items update with new UUIDs but same title
    // This allows finding descriptions loaded for the original UUID when hovering an item with updated UUID
    public static final Map<String, String> uuidToOriginalUuid = new HashMap<>();

    public class TooltipMessage implements  Message{
        private final String text;

        public TooltipMessage(String text) {
            this.text = text != null ? text : "";
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
                SKYCOFL_CATEGORY));
        uploadItemKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "keybinding.coflmod.uploaditem",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                SKYCOFL_CATEGORY));

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

            if (additionalKeyBindings == null) return;
            try{
                for (KeyBinding additionalKeyBinding : additionalKeyBindings) {
                    if(additionalKeyBinding.wasPressed()){
                        String keyName = keybindingsToHotkeys.get(additionalKeyBinding).Name;
                        String toAppend = getContextToAppend(client.player.getInventory().getStack(client.player.getInventory().getSelectedSlot()));

                        System.out.println("Exec hotkey "+ keyName + toAppend);
                        CoflSkyCommand.processCommand(new String[]{"hotkey", keyName+toAppend},
                                MinecraftClient.getInstance().getSession().getUsername());
                    }
                }
            } catch (ConcurrentModificationException e) {
                System.out.println("Additional Keybindings currently in use somewhere else, retrying...");
            }
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
            // reset cached data for different island
            DescriptionHandler.emptyTooltipData();
            uploadedScoreboard = false;
            EventSubscribers.positions = null;
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
                .executes(context -> {
                    // /fc with no arguments (toggles the chat server side)
                    CoflSkyCommand.processCommand(new String[]{"chat"}, username);
                    return 1;
                })
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
                    })
                )
            );
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
            if (screen instanceof HandledScreen<?> hs) {
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
            String stackId = getIdFromStack(stack);
            
            // Check if this UUID maps to an original UUID that has descriptions
            String lookupId = uuidToOriginalUuid.getOrDefault(stackId, stackId);
            
            if (!knownIds.contains(stackId) && !knownIds.contains(lookupId)
                    && MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?> hs) {
                        
                if(!stack.isEmpty() && !stackId.equals("Go Back;1"))
                    loadDescriptionsForInv(hs);
                knownIds.add(stackId);
                System.out.println("Missing descriptions for " + stackId);
                return;
            }

            // Try to get descriptions using the original UUID if mapped
            DescriptionHandler.DescModification[] tooltips = DescriptionHandler.getTooltipData(lookupId);
            if(tooltips == null && !lookupId.equals(stackId))
                tooltips = DescriptionHandler.getTooltipData(stackId); // fallback to current UUID
            if(tooltips == null)
                return;

            var text = stack.get(DataComponentTypes.LORE);
            List<Text> ogLoreLines = text == null ? new ArrayList<>() : text.lines();

            for (DescriptionHandler.DescModification tooltip : tooltips) {
                switch (tooltip.type) {
                    case "APPEND":
                        lines.add(Text.of(tooltip.value + " "));
                        break;
                    case "REPLACE":
                        if (tooltip.line < 0 || tooltip.line >= lines.size() || tooltip.line >= ogLoreLines.size()) {
                            System.out.println("Invalid line index: " + tooltip.line + " for tooltip: " + tooltip.value);
                            continue; // Skip if the line index is invalid
                        }
                        int targetLine = tooltip.line;
                        if(targetLine != 0 && Formatting.strip(ogLoreLines.get(targetLine).toString()).equals(Formatting.strip(lines.get(targetLine).toString()))) {
                            System.out.println("lines differ `" + Formatting.strip(ogLoreLines.get(targetLine +1).toString()) + "` to `" + Formatting.strip(lines.get(targetLine).toString())  + "`");
                            targetLine++; // assume another mod added a line and move this down
                        }
                        lines.remove(targetLine);
                        lines.add(targetLine, Text.of(tooltip.value));
                        break;
                    case "INSERT":
                        lines.add(tooltip.line, Text.of(tooltip.value));
                        break;
                    case "DELETE":
                        lines.remove(tooltip.line);
                        break;
                    case "HIGHLIGHT":
                        // handled in mixin
                        break;
                    default:
                        System.out.println("Unknown type: " + tooltip.type);
                }
            }

            // Add sell protection warnings to tooltips
            addSellProtectionTooltip(stack, lines);
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (EventSubscribers.showCountdown && EventSubscribers.countdownData != null
                    && (MinecraftClient.getInstance().currentScreen == null
                            || MinecraftClient.getInstance().currentScreen instanceof ChatScreen)) {
                int heightPercentage = EventSubscribers.countdownData.getHeightPercentage();
                int widthPercentage = EventSubscribers.countdownData.getWidthPercentage();
                int screenWidth = drawContext.getScaledWindowWidth();
                int screenHeight = drawContext.getScaledWindowHeight();

                int x = (screenWidth * widthPercentage) / 100;
                int y = (screenHeight * heightPercentage) / 100;

                RenderUtils.drawStringWithShadow(
                        drawContext,
                        EventSubscribers.countdownData.getPrefix()
                                + getStringFromDouble(EventSubscribers.getCountdown(), EventSubscribers.countdownData.getMaxPrecision()),
                        x,
                        y,
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
            if(EventRegistry.shouldBlockChatMessage(message.getString()))
                return false;

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

            return ActionResult.PASS;
        });

        ScreenEvents.AFTER_INIT.register((minecraftClient, screen, i, i1) -> {

        });
    }

    private static String getStringFromDouble(double seconds, int currentPrecision) {
        String render;

        if (seconds > 100) {
            render = String.valueOf((int) seconds);
        } else {
            render = String.format(Locale.US, "%.3f", seconds).substring(0, currentPrecision);
            if(render.charAt(render.length() - 1) == '.')
                render = render.substring(0, currentPrecision -1);
        }

        return render + "s";
    }

    private void registerDefaultCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, String name) {
        dispatcher.register(ClientCommandManager.literal(name)
                .then(ClientCommandManager.argument("args", StringArgumentType.greedyString())
                .suggests((context, builder) -> {
                    String input = context.getInput();
                    String[] inputArgs = input.split(" ");;
                    String currentWord = inputArgs.length > 0 ? inputArgs[inputArgs.length - 1] : "";

                    // Check if the command is "s" or "set" and suggest specific subcommands
                    if (inputArgs.length == 2 && (input.contains("set ") || input.equals("s "))
                        || inputArgs.length == 3 && (inputArgs[1].equals("s") || inputArgs[1].equals("set"))) {
                        if("sellprotectionenabled".contains(currentWord.toLowerCase()) || currentWord.equals("set"))
                            builder.suggest("set sellProtectionEnabled", new Message() {
                                @Override
                                public String getString() {
                                    return "Enable or disable sell protection (true/false)";
                                }
                            });
                        if("sellprotectionthreshold".contains(currentWord.toLowerCase()) || currentWord.equals("set"))
                            builder.suggest("set sellProtectionThreshold", new Message() {
                                @Override
                                public String getString() {
                                    return "Set max coin amount before sell protection blocks sells (e.g. 1000, 2k, 3m)";
                                }
                            });
                        if("angrycoopprotectionenabled".contains(currentWord.toLowerCase()) || currentWord.equals("set"))
                            builder.suggest("set angryCoopProtectionEnabled", new Message() {
                                @Override
                                public String getString() {
                                    return "Enable or disable angry co-op protection (true/false)";
                                }
                            });
                        for (Settings suggestion : CoflCore.config.knownSettings) {
                            if (suggestion.getSettingKey().toLowerCase().contains(currentWord.toLowerCase()) || currentWord.equals("set") || currentWord.equals("s")) {
                                String settingInfo = suggestion.getSettingInfo();
                                if (settingInfo == null) {
                                    builder.suggest("set " + suggestion.getSettingKey());
                                } else {
                                    builder.suggest("set " + suggestion.getSettingKey(), new Message() {
                                        @Override
                                        public String getString() {
                                            return settingInfo;
                                        }
                                    });
                                }
                            }
                        }
                    } else if(inputArgs.length > 3)
                        return builder.buildFuture();
                    else {                        
                        if(CoflCore.config.knownCommands == null)
                        {
                            System.out.println("No known commands loaded yet, cannot suggest");
                            return builder.buildFuture();
                        }
                        for (String suggestion : CoflCore.config.knownCommands.keySet()) {
                            if (suggestion.toLowerCase().startsWith(currentWord.toLowerCase())
                                    || inputArgs.length == 1 // just /cofl should show all
                            ){
                                String messageText = CoflCore.config.knownCommands.get(suggestion);
                                if(messageText == null)
                                    builder.suggest(suggestion);
                                else
                                    builder.suggest(suggestion, new Message() {
                                        @Override
                                        public String getString() {
                                            // Replace line breaks with spaces for single-line display
                                            String[] parts = messageText.split("\n");
                                            return parts.length > 0 ? parts[0] : "";
                                        }
                                    });
                            }
                        }
                    }
                    return builder.buildFuture();
                })
                .executes(context -> {
                    String[] args = context.getArgument("args", String.class).split(" ");
                    
                    // Handle sell protection commands locally
                    if (args.length >= 2 && args[0].equals("set")) {
                        if (args[1].equals("sellProtectionEnabled")) {
                            if (args.length >= 3) {
                                boolean enabled = args[2].equalsIgnoreCase("true") || args[2].equals("1");
                                com.coflnet.config.SellProtectionManager.setEnabled(enabled);
                                sendChatMessage("§aSell Protection " + (enabled ? "enabled" : "disabled"));
                                return 1;
                            } else {
                                sendChatMessage("§cUsage: /cofl set sellProtectionEnabled <true/false>");
                                return 1;
                            }
                        } else if (args[1].equals("sellProtectionThreshold")) {
                            if (args.length >= 3) {
                                try {
                                    long amount = parseAmountString(args[2]);
                                    com.coflnet.config.SellProtectionManager.setMaxAmount(amount);
                                    sendChatMessage("§aSell Protection max amount set to " + formatCoins(amount) + " coins");
                                    return 1;
                                } catch (NumberFormatException e) {
                                    sendChatMessage("§cInvalid number: " + args[2] + ". Use formats like: 1000, 2k, 3m, 1.5b");
                                    return 1;
                                }
                            } else {
                                sendChatMessage("§cUsage: /cofl set sellProtectionThreshold <amount>");
                                sendChatMessage("§7Examples: 1000, 2k, 3m, 1.5b");
                                return 1;
                            }
                        } else if (args[1].equals("angryCoopProtectionEnabled")) {
                            if (args.length >= 3) {
                                boolean enabled = args[2].equalsIgnoreCase("true") || args[2].equals("1");
                                com.coflnet.config.AngryCoopProtectionManager.setEnabled(enabled);
                                sendChatMessage("§aAngry Co-op Protection " + (enabled ? "enabled" : "disabled"));
                                return 1;
                            } else {
                                sendChatMessage("§cUsage: /cofl set angryCoopProtectionEnabled <true/false>");
                                return 1;
                            }
                        }
                    } else if (args.length >= 1 && args[0].equals("sellprotection")) {
                        if (args.length == 1) {
                            // Show current settings
                            com.coflnet.config.CoflModConfig config = com.coflnet.config.SellProtectionManager.getConfig();
                            sendChatMessage("§6=== Sell Protection Settings ===");
                            sendChatMessage("§7Enabled: " + (config.sellProtectionEnabled ? "§aYes" : "§cNo"));
                            sendChatMessage("§7Max Amount: §6" + formatCoins(config.sellProtectionThreshold) + " coins");
                            sendChatMessage("§7Usage: §e/cofl set sellProtectionEnabled <true/false>");
                            sendChatMessage("§7Usage: §e/cofl set sellProtectionThreshold <amount>");
                            sendChatMessage("§7Examples: §e1000§7, §e2k§7, §e3m§7, §e1.5b");
                            return 1;
                        }
                    } else if (args.length >= 1 && args[0].equals("angrycoop")) {
                        if (args.length == 1) {
                            com.coflnet.config.CoflModConfig config = com.coflnet.config.AngryCoopProtectionManager.getConfig();
                            sendChatMessage("§6=== Angry Co-op Protection Settings ===");
                            sendChatMessage("§7Enabled: " + (config.angryCoopProtectionEnabled ? "§aYes" : "§cNo"));
                            sendChatMessage("§7Usage: §e/cofl set angryCoopProtectionEnabled <true/false>");
                            return 1;
                        }
                    }
                    
                    // Special internal-only commands
                    if (args.length >= 1 && args[0].equalsIgnoreCase("bazaarsearch")) {
                        if (args.length >= 2) {
                            // Join remaining args as the search term
                            String[] part = new String[args.length - 1];
                            System.arraycopy(args, 1, part, 0, part.length);
                            String searchTerm = String.join(" ", part);
                            searchInBazaarSmart(searchTerm);
                            return 1;
                        } else {
                            sendChatMessage("§cUsage: /cofl bazaarsearch <item>");
                            return 1;
                        }
                    }

                    // Pass to CoflSkyCommand for other commands
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
        if (CoflCore.Wrapper != null)
            CoflCore.Wrapper.SendMessage(data);
    }

    private static void uploadScoreboard() {
        String[] scores = CoflModClient.getScoreboard().toArray(new String[0]);
        lastScoreboardUploaded = getRelevantLinesFromScoreboard(scores);
        Command<String[]> data = new Command<>(CommandType.uploadScoreboard, scores);
        if(CoflCore.Wrapper != null)
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
        if(itemName.contains("BUY") || itemName.contains("SELL"))
        {
            // bazaar order, separate by price per unit as well
            for (Text line : stack.get(DataComponentTypes.LORE).lines()) {
                if(line.getString().contains("Price per unit"))
                {
                    return itemName + line.getString();
                }
            }
        }
        if (stackJson == null)
            return itemName + ";" + stack.getCount();

        JsonElement uuid = stackJson.get("uuid");
        if (uuid != null)
            return uuid.getAsString();
        // If "id" is not present, use the item's name
        return itemName + ";" + stack.getCount();
    }

    public void loadDescriptionsForInv(HandledScreen screen) {
        String menuSlot = MinecraftClient.getInstance().player.getInventory().getStack(8).getComponents().toString();
        if (!menuSlot.contains("minecraft:custom_data=>{id:\"SKYBLOCK_MENU\"}")
            && !menuSlot.contains("Scaffolding") && !menuSlot.contains("Quiver")
            && !menuSlot.contains("Your Score Summary") // dungeon completion
            )
            return;
        Thread.startVirtualThread(() -> {
            DefaultedList<ItemStack> itemStacks = screen.getScreenHandler().getStacks();
            String title = screen.getTitle().getString();
            try {
                Thread.sleep(100);
                for (int i = 0; i < 20; i++) {
                    if(itemStacks.size() <= InventorysizeWithOffHand || !itemStacks.get(itemStacks.size() - InventorysizeWithOffHand).isEmpty())
                        break;
                    Thread.sleep(50); // wait for the screen to load
                    System.out.println("Waiting for itemStacks to load, current size: " + getIdFromStack(itemStacks.get(itemStacks.size() - InventorysizeWithOffHand)));
                    itemStacks = screen.getScreenHandler().getStacks();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                HandledScreen currentScreen = MinecraftClient.getInstance().currentScreen instanceof HandledScreen ? (HandledScreen) MinecraftClient.getInstance().currentScreen : null;
                if (currentScreen == null || currentScreen.getScreenHandler() != screen.getScreenHandler()){
                    System.out.println("Inventory changed already, not refreshing descriptions");
                    return; // inventory changed, don't refresh
                }
                String[] visibleItems = getItemIdsFromInventory(itemStacks);
                loadDescriptionsForItems(title, itemStacks);
                boolean refresh = false;
                if(title.contains("Auctions"))
                {
                    for (ItemStack itemStack : itemStacks) {
                        if(itemStack.get(DataComponentTypes.LORE) == null)
                            continue;
                        for (Text line : itemStack.get(DataComponentTypes.LORE).lines()) {
                            if(line.getString().contains("Refreshing..."))
                            {
                                refresh = true;
                                break;
                            }
                        }
                    }
                    if(refresh)
                        Thread.sleep(500); // wait extra for names to load
                }
                Thread.sleep(1000);
                // check all items in the inventory for descriptions
                String[] itemIds = getItemIdsFromInventory(screen.getScreenHandler().getStacks());
                List<String> visibleList = Arrays.asList(visibleItems);
                for (String itemId : itemIds) {
                    if (!visibleList.contains(itemId) && !itemId.startsWith("EMPTY_SLOT_")) {
                        refresh = true;
                        break;
                    }
                }
                if (refresh) {
                    currentScreen = MinecraftClient.getInstance().currentScreen instanceof HandledScreen ? (HandledScreen) MinecraftClient.getInstance().currentScreen : null;
                    if (currentScreen == null || currentScreen.getScreenHandler() != screen.getScreenHandler()){
                        System.out.println("Inventory changed, not refreshing descriptions");
                        return; // inventory changed, don't refresh 
                        }
                    if(!title.equals(screen.getTitle().getString()))
                    {
                        System.out.println("Title changed, not refreshing descriptions");
                        return;
                    }
                    System.out.println("Refreshing descriptions for inventory: " + title);
                    loadDescriptionsForItems(title, itemStacks);
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
        String userName = MinecraftClient.getInstance().getSession().getUsername();
        String nbtString = inventoryToNBT(items);
        if(nbtString.equals(lastNbtRequest)) {
            return;
        }
        lastNbtRequest = nbtString;
        
        // Check if we should throttle this request
        long currentTime = System.currentTimeMillis();
        Long lastRefreshTime = lastRefreshTimePerInventory.get(title);
        
        if (lastRefreshTime != null && (currentTime - lastRefreshTime) < REFRESH_THROTTLE_MS) {
            // Too soon since last refresh, schedule the request to be made after the throttle period
            long delayMs = REFRESH_THROTTLE_MS - (currentTime - lastRefreshTime);
            System.out.println("Throttling refresh for inventory: " + title + " (wait " + delayMs + "ms)");
            Thread.startVirtualThread(() -> {
                try {
                    Thread.sleep(delayMs);
                    // Request with current inventory state (in case it updated)
                    DefaultedList<ItemStack> currentItems = DefaultedList.of();
                    HandledScreen currentScreen = MinecraftClient.getInstance().currentScreen instanceof HandledScreen 
                        ? (HandledScreen) MinecraftClient.getInstance().currentScreen 
                        : null;
                    if (currentScreen != null && currentScreen.getTitle().getString().equals(title)) {
                        currentItems.addAll(currentScreen.getScreenHandler().getStacks());
                    } else {
                        // Inventory changed, use the items we have
                        currentItems = items;
                    }
                    String[] visibleItems = getItemIdsFromInventory(currentItems);
                    DescriptionHandler.loadDescriptionForInventory(
                            visibleItems,
                            title,
                            inventoryToNBT(currentItems),
                            userName,
                            posToUpload
                    );
                    lastRefreshTimePerInventory.put(title, System.currentTimeMillis());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
            return;
        }
        
        // Update last refresh time and make the request
        lastRefreshTimePerInventory.put(title, currentTime);
        String[] visibleItems = getItemIdsFromInventory(items);
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

        Scoreboard scoreboard = MinecraftClient.getInstance().world.getScoreboard();
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
                        String playerName = playerListEntry.getProfile().name();
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

    /**
     * Determines if the given screen is the bazaar.
     * @param gcs instance of {@link GenericContainerScreen}
     * @return {@code true} if the screen is the bazaar, otherwise {@code false}
     */
    public static boolean isBazaar(GenericContainerScreen gcs) {
        String title = gcs.getTitle().getString();
        return title.contains("Bazaar") && gcs.getScreenHandler().getInventory().size() == 9 * 6;
    }

    /**
     * Automatically searches for an item in the bazaar.
     * This function clicks on the "Search" item, fills in the search content, and closes the sign.
     * 
     * Usage examples:
     * - searchInBazaar("Iron Ingot") - searches for iron ingots
     * - searchInBazaar("Enchanted Diamond") - searches for enchanted diamonds
     * 
     * The function will:
     * 1. Verify the player is currently on the bazaar
     * 2. Find the search item in the GUI (typically a name tag or paper)
     * 3. Click on the search item to open the sign editor
     * 4. Fill in the search term automatically 
     * 5. Close the sign to execute the search
     * 
     * @param searchTerm the item name to search for
     */
    public static void searchInBazaar(String searchTerm) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof GenericContainerScreen gcs)) {
            System.out.println("Current screen is not a container screen");
            return;
        }

        if (!isBazaar(gcs)) {
            System.out.println("Current screen is not the bazaar");
            return;
        }

        // Find the "Search" item in the bazaar GUI
        int searchSlot = findSearchSlotInBazaar(gcs);
        if (searchSlot == -1) {
            System.out.println("Could not find Search item in bazaar");
            return;
        }

        System.out.println("Found Search item at slot: " + searchSlot + ", searching for: " + searchTerm);

        // Store the search term for the sign editing
        pendingBazaarSearch = searchTerm;

        // Click on the search item
        clickSlotInContainer(gcs, searchSlot);
    }

    /**
     * Enhanced bazaar search that also handles item name cleaning.
     * @param rawItemName the raw item name that may need cleaning
     */
    public static void searchInBazaarSmart(String rawItemName) {
        // Clean the item name (remove formatting codes, etc.)
        String cleanedName = cleanItemNameForSearch(rawItemName);
        searchInBazaar(cleanedName);
    }

    /**
     * Cleans an item name for bazaar search by removing Minecraft formatting codes and other artifacts.
     * @param rawName the raw item name
     * @return cleaned item name suitable for bazaar search
     */
    private static String cleanItemNameForSearch(String rawName) {
        if (rawName == null) return "";
        
        // Remove Minecraft formatting codes (§ followed by any character)
        String cleaned = rawName.replaceAll("§.", "");
        
        // Remove common prefixes/suffixes that might interfere with search
        cleaned = cleaned.replaceAll("\\[.*?\\]", ""); // Remove brackets
        cleaned = cleaned.replaceAll("\\(.*?\\)", ""); // Remove parentheses
        
        // Trim whitespace
        cleaned = cleaned.trim();
        
        return cleaned;
    }

    /**
     * Finds the slot containing the "Search" item in the bazaar.
     * @param gcs the bazaar container screen
     * @return the slot index of the search item, or -1 if not found
     */
    private static int findSearchSlotInBazaar(GenericContainerScreen gcs) {
        for (int i = 0; i < gcs.getScreenHandler().getInventory().size(); i++) {
            ItemStack stack = gcs.getScreenHandler().getInventory().getStack(i);
            
            // Skip empty slots
            if (stack.isEmpty()) {
                continue;
            }
            
            // Check for common search item types
            if (stack.getItem() == Items.NAME_TAG || 
                stack.getItem() == Items.PAPER || 
                stack.getItem() == Items.WRITABLE_BOOK ||
                stack.getItem() == Items.COMPASS) {
                
                // Check if the item has a custom name containing "Search"
                if (stack.getCustomName() != null) {
                    String customName = stack.getCustomName().getString().toLowerCase();
                    if (customName.contains("search")) {
                        return i;
                    }
                }
                
                // Check lore for search functionality
                if (stack.get(DataComponentTypes.LORE) != null) {
                    for (Text line : stack.get(DataComponentTypes.LORE).lines()) {
                        String loreText = line.getString().toLowerCase();
                        if (loreText.contains("search") || loreText.contains("find") || loreText.contains("look for")) {
                            return i;
                        }
                    }
                }
            }
        }
        
        // Fallback: look for any item with "search" in the name or lore
        for (int i = 0; i < gcs.getScreenHandler().getInventory().size(); i++) {
            ItemStack stack = gcs.getScreenHandler().getInventory().getStack(i);
            
            if (stack.isEmpty()) continue;
            
            if (stack.getCustomName() != null) {
                String customName = stack.getCustomName().getString().toLowerCase();
                if (customName.contains("search")) {
                    return i;
                }
            }
            
            if (stack.get(DataComponentTypes.LORE) != null) {
                for (Text line : stack.get(DataComponentTypes.LORE).lines()) {
                    String loreText = line.getString().toLowerCase();
                    if (loreText.contains("search") && (loreText.contains("click") || loreText.contains("use"))) {
                        return i;
                    }
                }
            }
        }
        
        return -1;
    }

    /**
     * Clicks a slot in a container screen.
     * @param gcs the container screen
     * @param slotId the slot index to click
     */
    private static void clickSlotInContainer(GenericContainerScreen gcs, int slotId) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;

        client.interactionManager.clickSlot(
                gcs.getScreenHandler().syncId,
                slotId,
                0,
                SlotActionType.PICKUP,
                player
        );
    }

    private boolean checkVersionCompability() {
        try {
            String v = net.minecraft.SharedConstants.getGameVersion().id();
            System.out.println("Detected Minecraft version:" + v);
            boolean b = v.compareTo(targetVersion) == 0;

            return b;
        } catch (NoSuchMethodError e){
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

    public static void setHotKeys(HotkeyRegister[] keys) {
        additionalKeyBindings.clear();
        keybindingsToHotkeys.clear();
        for (int i = 0; i < keys.length; i++) {
            int keyIndex = getKeyIndex(keys[i].DefaultKey.toUpperCase());

            HotkeyRegister hotkey = keys[i];
            KeyBinding.Category unchangeableCategory = KeyBinding.Category.create(Identifier.of("coflnet", "skycofl_unchangeable"));
            KeyBinding keyBinding = new KeyBinding(hotkey.Name, keyIndex, unchangeableCategory);
            additionalKeyBindings.add(keyBinding);
            keybindingsToHotkeys.put(keyBinding, hotkey);
            System.out.println("Registered Key: " + hotkey.Name + " with key " + hotkey.DefaultKey.toUpperCase() + " (" +keyIndex+")");
            //KeyBindingHelper.registerKeyBinding(additionalKeyBindings.get(i));
        }
    }

    public static int getKeyIndex(String name){
        int result = -1;
        String prefix = "GLFW_KEY_";
        for (Field f : GLFW.class.getDeclaredFields()) {
            if (f.getName().startsWith(prefix) && f.getName().substring(prefix.length()).equals(name)) {
                try {
                    result = (int) f.get(int.class);
                } catch (IllegalAccessException e) {
                    System.out.println("Key inaccessible. This shouldn't happen");
                }
                break;
            }
        }

        return result;
    }

    /**
     * Adds sell protection warnings to item tooltips
     */
    private static void addSellProtectionTooltip(ItemStack stack, List<Text> lines) {
        try {
            // Check if sell protection is enabled
            if (!com.coflnet.config.SellProtectionManager.isEnabled()) {
                return;
            }

            // Check if we're in a screen with "➜" in title
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen instanceof HandledScreen<?> screen) {
                String screenTitle = screen.getTitle().getString();
                if (!screenTitle.contains("➜")) {
                    return;
                }
            } else {
                return;
            }

            String itemName = "";
            if (stack.getCustomName() != null) {
                itemName = stack.getCustomName().getString();
            } else {
                itemName = stack.getItem().getDefaultStack().getName().getString();
            }

            // Get threshold for comparison
            long threshold = com.coflnet.config.SellProtectionManager.getMaxAmount();
            String formattedThreshold = formatCoins(threshold);

            // Extract coin amount and only show warning if over threshold
            long sellAmount = 0;
            boolean shouldShowWarning = false;

            if (itemName.contains("Sell Instantly")) {
                sellAmount = com.coflnet.utils.SellAmountParser.extractSellInstantlyAmountFromTooltip(lines);
                // Only show warning if we successfully parsed an amount and it's over threshold
                // Don't show warning for the default protection amount (Long.MAX_VALUE)
                shouldShowWarning = sellAmount > threshold && sellAmount != com.coflnet.utils.SellAmountParser.getDefaultProtectionAmount();
                
                if (shouldShowWarning) {
                    lines.add(Text.literal(""));
                    lines.add(Text.literal("§c⚠ §lSell Protection §c⚠"));
                    lines.add(Text.literal("§7Left clicks blocked if > §6" + formattedThreshold + " coins"));
                    lines.add(Text.literal("§bHold Ctrl§7 to override."));
                    lines.add(Text.literal("§8/cofl set sellProtectionThreshold <amount>"));
                }
            } else if (itemName.contains("Sell Sacks Now") || itemName.contains("Sell Inventory Now")) {
                sellAmount = com.coflnet.utils.SellAmountParser.extractInventorySackAmountFromTooltip(lines);
                // Only show warning if we successfully parsed an amount and it's over threshold
                // Don't show warning for the default protection amount (Long.MAX_VALUE)
                shouldShowWarning = sellAmount > threshold && sellAmount != com.coflnet.utils.SellAmountParser.getDefaultProtectionAmount();
                
                if (shouldShowWarning) {
                    lines.add(Text.literal(""));
                    lines.add(Text.literal("§c⚠ §lSell Protection §c⚠"));
                    lines.add(Text.literal("§7All clicks blocked if > §6" + formattedThreshold + " coins"));
                    lines.add(Text.literal("§bHold Ctrl§7 to override."));
                    lines.add(Text.literal("§8/cofl set sellProtectionThreshold <amount>"));
                }
            }
        } catch (Exception e) {
            System.out.println("[CoflModClient] addSellProtectionTooltip failed: " + e.getMessage());
        }
    }

    /**
     * Parses amount strings with k/m/b suffixes (e.g., "2k", "3m", "1.5b")
     * @param amountStr the string to parse (e.g., "1000", "2k", "3m", "1.5b")
     * @return the parsed amount as a long
     * @throws NumberFormatException if the string cannot be parsed
     */
    private static long parseAmountString(String amountStr) throws NumberFormatException {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            throw new NumberFormatException("Empty amount string");
        }
        
        String input = amountStr.trim().toLowerCase();
        
        // Handle plain numbers first
        if (input.matches("^[0-9]+$")) {
            return Long.parseLong(input);
        }
        
        // Handle numbers with decimal points (e.g., "1.5")
        if (input.matches("^[0-9]+\\.[0-9]+$")) {
            return (long) (Double.parseDouble(input));
        }
        
        // Handle suffixed numbers
        if (input.matches("^[0-9]+\\.?[0-9]*[kmb]$")) {
            char suffix = input.charAt(input.length() - 1);
            String numberPart = input.substring(0, input.length() - 1);
            
            double value;
            try {
                value = Double.parseDouble(numberPart);
            } catch (NumberFormatException e) {
                throw new NumberFormatException("Invalid number part: " + numberPart);
            }
            
            switch (suffix) {
                case 'k':
                    return (long) (value * 1_000);
                case 'm':
                    return (long) (value * 1_000_000);
                case 'b':
                    return (long) (value * 1_000_000_000);
                default:
                    throw new NumberFormatException("Invalid suffix: " + suffix);
            }
        }
        
        // If we get here, the format is not recognized
        throw new NumberFormatException("Invalid format: " + amountStr + ". Use formats like: 1000, 2k, 3m, 1.5b");
    }

    private static String formatCoins(long coins) {
        if (coins >= 1000000000) {
            return String.format(java.util.Locale.US, "%.1fB", coins / 1000000000.0);
        } else if (coins >= 1000000) {
            return String.format(java.util.Locale.US, "%.1fM", coins / 1000000.0);
        } else if (coins >= 1000) {
            return String.format(java.util.Locale.US, "%.1fK", coins / 1000.0);
        } else {
            return String.valueOf(coins);
        }
    }

    private static void sendChatMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }
}
