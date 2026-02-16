package com.coflnet.gui.cofl;

import CoflCore.CoflCore;
import CoflCore.CoflSkyCommand;
import CoflCore.classes.Settings;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.LongFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class CoflSettingsScreen {
    private static final Set<String> HIDDEN_SETTING_KEYS = Set.of(
            "privacyDefaultChatRegex",
            "privacyDefaultBlockRegex",
            "privacyDefaultChatBlockRegex",
            "BasedOnLBin",
            "filters",
            "modHotkeys"
    );

    private enum FinderType {
        UNKOWN(0, false, "Unknown/invalid finder state"),
        FLIPPER(1, true, "The classical flip-finding algorithm using the Skyblock AH history database. Searches past auctions for similar items; this reference lookup is thorough but relatively slow."),
        SNIPER(2, true, "A fast sniping algorithm that tracks prices grouped by relevant modifiers and reports opportunities below LBIN and median. Much faster than the flipper but may find fewer flips."),
        SNIPER_MEDIAN(4, true, "Similar to Sniper but triggers on items approximately 5% below the median sell value instead of requiring LBIN."),
        AI(8, true, "Machine-learning assisted finder that estimates item value and reports potential flips based on learned signals."),
        SNIPERS(SNIPER.mask | SNIPER_MEDIAN.mask, false, "Combined sniper presets"),
        FLIPPER_AND_SNIPERS(FLIPPER.mask | SNIPERS.mask, false, "Combined default finder preset"),
        USER(16, true, "Forwards new auctions using the starting bid as the target (0 profit). Use with whitelist/blacklist and filters to craft custom flip rules. Unlike other finders this one does not pre-filter auctions."),
        TFM(32, false, "TFM (TheFlippingMod) integration; flips provided from TFM. Integration currently under development."),
        STONKS(64, true, "Experimental predictor that estimates item value without historical references. Use with caution; it may occasionally overvalue items."),
        EXTERNAL(128, false, "External finder source"),
        BINMASTER(256, false, "Binmaster finder source"),
        LEIKO(512, false, "Leiko finder source"),
        CRAFT_COST(1024, true, "Reports auctions that are at least 5% cheaper than the summed craft cost (clean+modifier). Adjust weights with the CraftCostWeight filter. Does not guarantee sale price, use with caution!"),
        RUST(2048, true, "High-speed Rust-based finder hosted on high-performance infrastructure near Hypixel. This is a third-party, paid finder; use /cofl rust in the mod to learn more."),
        MEDIAN_BASED(SNIPER_MEDIAN.mask | FLIPPER.mask, false, "Median-based preset"),
        ALL_EXCEPT_USER(FLIPPER_AND_SNIPERS.mask | AI.mask | TFM.mask | STONKS.mask | EXTERNAL.mask, false, "Preset that excludes USER finder");

        private final int mask;
        private final boolean selectable;
        private final String description;

        FinderType(int mask, boolean selectable, String description) {
            this.mask = mask;
            this.selectable = selectable;
            this.description = description;
        }

        private static FinderType fromName(String name) {
            for (FinderType value : values()) {
                if (value.name().equalsIgnoreCase(name)) {
                    return value;
                }
            }
            return null;
        }
    }

    public static Screen create(Screen parent) {
        List<Settings> known = CoflCore.config.knownSettings == null ? List.of() : CoflCore.config.knownSettings;
        Map<String, List<Settings>> byCategory = new LinkedHashMap<>();

        for (Settings setting : known) {
            if (shouldHide(setting)) {
                continue;
            }
            String category = setting.getSettingCategory();
            if (category == null || category.isBlank()) {
                category = "General";
            }
            byCategory.computeIfAbsent(category, ignored -> new ArrayList<>()).add(setting);
        }

        YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder()
                .title(Text.of("SkyCofl Settings"));

        byCategory.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> {
                    ConfigCategory.Builder categoryBuilder = ConfigCategory.createBuilder()
                            .name(Text.of(capitalize(entry.getKey())));

                    entry.getValue().stream()
                            .sorted(Comparator.comparing(s -> fallback(s.getSettingName(), s.getSettingKey()), String.CASE_INSENSITIVE_ORDER))
                            .forEach(setting -> {
                                if (isFinderSetting(setting)) {
                                    categoryBuilder.group(buildFinderTypeGroup(setting));
                                    return;
                                }

                                Option<?> option = buildOption(setting);
                                if (option != null) {
                                    categoryBuilder.option(option);
                                }
                            });

                    builder.category(categoryBuilder.build());
                });

        builder.save(() -> {
        });

        return builder.build().generateScreen(parent);
    }

    private static Option<?> buildOption(Settings setting) {
        String settingName = fallback(setting.getSettingName(), setting.getSettingKey());
        String settingInfo = fallback(setting.getSettingInfo(), "No description available.");
        String type = fallback(setting.getSettingType(), "String");

        if (isCollectionSetting(setting)) {
            return buildCollectionOption(setting, settingName, settingInfo);
        }

        return switch (type) {
            case "Boolean" -> buildBooleanOption(setting, settingName, settingInfo);
            case "Int32", "Int16", "Byte", "SByte" -> buildIntegerOption(setting, settingName, settingInfo);
            case "Int64" -> buildLongOption(setting, settingName, settingInfo);
            case "Double" -> buildDoubleOption(setting, settingName, settingInfo);
            case "Single" -> buildFloatOption(setting, settingName, settingInfo);
            default -> buildStringOption(setting, settingName, settingInfo);
        };
    }

    private static Option<Boolean> buildBooleanOption(Settings setting, String name, String info) {
        boolean current = toBoolean(setting.getSettingValue());
        return Option.<Boolean>createBuilder()
                .name(Text.of(name))
                .description(OptionDescription.of(Text.of(info)))
                .binding(current,
                        () -> toBoolean(setting.getSettingValue()),
                        value -> {
                            setting.setSettingValue(value);
                            sendSet(setting.getSettingKey(), Boolean.toString(value));
                        })
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static Option<Integer> buildIntegerOption(Settings setting, String name, String info) {
        int current = toInt(setting.getSettingValue());
        return Option.<Integer>createBuilder()
                .name(Text.of(name))
                .description(OptionDescription.of(Text.of(info)))
                .binding(current,
                        () -> toInt(setting.getSettingValue()),
                        value -> {
                            setting.setSettingValue(value);
                            sendSet(setting.getSettingKey(), Integer.toString(value));
                        })
                .controller(IntegerFieldControllerBuilder::create)
                .build();
    }

    private static Option<Long> buildLongOption(Settings setting, String name, String info) {
        long current = toLong(setting.getSettingValue());
        return Option.<Long>createBuilder()
                .name(Text.of(name))
                .description(OptionDescription.of(Text.of(info)))
                .binding(current,
                        () -> toLong(setting.getSettingValue()),
                        value -> {
                            setting.setSettingValue(value);
                            sendSet(setting.getSettingKey(), Long.toString(value));
                        })
                .controller(LongFieldControllerBuilder::create)
                .build();
    }

    private static Option<Double> buildDoubleOption(Settings setting, String name, String info) {
        double current = toDouble(setting.getSettingValue());
        return Option.<Double>createBuilder()
                .name(Text.of(name))
                .description(OptionDescription.of(Text.of(info)))
                .binding(current,
                        () -> toDouble(setting.getSettingValue()),
                        value -> {
                            setting.setSettingValue(value);
                            sendSet(setting.getSettingKey(), Double.toString(value));
                        })
                .controller(DoubleFieldControllerBuilder::create)
                .build();
    }

    private static Option<Float> buildFloatOption(Settings setting, String name, String info) {
        float current = toFloat(setting.getSettingValue());
        return Option.<Float>createBuilder()
                .name(Text.of(name))
                .description(OptionDescription.of(Text.of(info)))
                .binding(current,
                        () -> toFloat(setting.getSettingValue()),
                        value -> {
                            setting.setSettingValue(value);
                            sendSet(setting.getSettingKey(), Float.toString(value));
                        })
                .controller(FloatFieldControllerBuilder::create)
                .build();
    }

    private static Option<String> buildStringOption(Settings setting, String name, String info) {
        String current = toStringValue(setting.getSettingValue());
        return Option.<String>createBuilder()
                .name(Text.of(name))
                .description(OptionDescription.of(Text.of(info)))
                .binding(current,
                        () -> toStringValue(setting.getSettingValue()),
                        value -> {
                            setting.setSettingValue(value);
                            sendSet(setting.getSettingKey(), value);
                        })
                .controller(StringControllerBuilder::create)
                .build();
    }

    private static Option<String> buildCollectionOption(Settings setting, String name, String info) {
        LinkedHashSet<String> initialSet = toStringSet(setting.getSettingValue());
        AtomicReference<LinkedHashSet<String>> appliedSet = new AtomicReference<>(new LinkedHashSet<>(initialSet));

        return Option.<String>createBuilder()
                .name(Text.of(name))
                .description(OptionDescription.of(Text.of(info)))
                .binding(toCommaSeparated(initialSet),
                        () -> toCommaSeparated(toStringSet(setting.getSettingValue())),
                        value -> {
                            LinkedHashSet<String> next = parseCommaSeparated(value);
                            LinkedHashSet<String> previous = appliedSet.getAndSet(new LinkedHashSet<>(next));

                            for (String added : difference(next, previous)) {
                                sendSet(setting.getSettingKey(), added);
                            }
                            for (String removed : difference(previous, next)) {
                                sendSetRemove(setting.getSettingKey(), removed);
                            }

                            setting.setSettingValue(new ArrayList<>(next));
                        })
                .controller(StringControllerBuilder::create)
                .build();
    }

    private static OptionGroup buildFinderTypeGroup(Settings setting) {
        String name = fallback(setting.getSettingName(), setting.getSettingKey());
        String info = fallback(setting.getSettingInfo(), "Select which finder types should be enabled.");

        AtomicReference<Integer> finderMask = new AtomicReference<>(parseFinderMask(setting.getSettingValue()));

        OptionGroup.Builder groupBuilder = OptionGroup.createBuilder()
                .name(Text.of(name))
                .description(OptionDescription.of(Text.of(info)))
                .collapsed(false);

        for (FinderType finderType : FinderType.values()) {
            if (!finderType.selectable) {
                continue;
            }

            String optionName = formatFinderTypeName(finderType);
            groupBuilder.option(Option.<Boolean>createBuilder()
                    .name(Text.of(optionName))
                    .description(OptionDescription.of(Text.of(finderType.description)))
                    .binding((finderMask.get() & finderType.mask) != 0,
                            () -> (finderMask.get() & finderType.mask) != 0,
                            enabled -> {
                                int currentMask = finderMask.get();
                                int updatedMask = enabled
                                        ? (currentMask | finderType.mask)
                                        : (currentMask & ~finderType.mask);

                                finderMask.set(updatedMask);
                                setting.setSettingValue(updatedMask);
                                sendSet(setting.getSettingKey(), Integer.toString(updatedMask));
                            })
                    .controller(TickBoxControllerBuilder::create)
                    .build());
        }

        return groupBuilder.build();
    }

    private static void sendSet(String key, String value) {
        if (MinecraftClient.getInstance() == null || MinecraftClient.getInstance().getSession() == null) {
            return;
        }
        CoflSkyCommand.processCommand(new String[]{"set", key, value}, MinecraftClient.getInstance().getSession().getUsername());
    }

    private static void sendSetRemove(String key, String value) {
        if (MinecraftClient.getInstance() == null || MinecraftClient.getInstance().getSession() == null) {
            return;
        }
        CoflSkyCommand.processCommand(new String[]{"set", key, "rm", value}, MinecraftClient.getInstance().getSession().getUsername());
    }

    private static boolean shouldHide(Settings setting) {
        return HIDDEN_SETTING_KEYS.contains(setting.getSettingKey());
    }

    private static boolean isFinderSetting(Settings setting) {
        return "finders".equalsIgnoreCase(setting.getSettingKey())
                || "FinderType".equalsIgnoreCase(fallback(setting.getSettingType(), ""));
    }

    private static boolean isCollectionSetting(Settings setting) {
        String type = fallback(setting.getSettingType(), "");
        return type.startsWith("HashSet")
                || type.startsWith("List")
                || type.startsWith("Array")
                || setting.getSettingValue() instanceof Collection<?>;
    }

    private static int parseFinderMask(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value == null) {
            return 0;
        }

        String raw = String.valueOf(value).trim();
        if (raw.isEmpty()) {
            return 0;
        }

        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
        }

        String[] pieces = raw.split("[|,\\s]+");
        int mask = 0;
        for (String piece : pieces) {
            if (piece == null || piece.isBlank()) {
                continue;
            }
            FinderType finderType = FinderType.fromName(piece.trim());
            if (finderType != null) {
                mask |= finderType.mask;
            }
        }
        return mask;
    }

    private static String formatFinderTypeName(FinderType finderType) {
        return switch (finderType) {
            case FLIPPER -> "Flipper";
            case SNIPER -> "Sniper";
            case SNIPER_MEDIAN -> "Sniper Median";
            case AI -> "AI";
            case USER -> "User";
            case TFM -> "TFM";
            case STONKS -> "STONKS";
            case CRAFT_COST -> "Craft Cost";
            case RUST -> "Rust";
            default -> capitalize(finderType.name().toLowerCase());
        };
    }

    private static LinkedHashSet<String> parseCommaSeparated(String value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return result;
        }

        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .forEach(result::add);
        return result;
    }

    private static LinkedHashSet<String> toStringSet(Object value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (value instanceof Collection<?> collection) {
            for (Object element : collection) {
                if (element == null) {
                    continue;
                }
                String stringValue = String.valueOf(element).trim();
                if (!stringValue.isBlank()) {
                    result.add(stringValue);
                }
            }
            return result;
        }

        if (value != null) {
            String stringValue = String.valueOf(value).trim();
            if (!stringValue.isBlank()) {
                result.add(stringValue);
            }
        }

        return result;
    }

    private static String toCommaSeparated(Collection<String> values) {
        return String.join(", ", values);
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static String fallback(String primary, String fallback) {
        if (primary == null || primary.isBlank()) {
            return fallback;
        }
        return primary;
    }

    private static String capitalize(String text) {
        if (text == null || text.isBlank()) {
            return "General";
        }
        String[] words = text.split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.isEmpty() ? "General" : builder.toString();
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return 0D;
        }
    }

    private static float toFloat(Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        try {
            return Float.parseFloat(String.valueOf(value));
        } catch (Exception ignored) {
            return 0F;
        }
    }

    private static String toStringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}