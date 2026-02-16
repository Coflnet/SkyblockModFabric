package com.coflnet.gui.cofl;

import CoflCore.CoflCore;
import CoflCore.CoflSkyCommand;
import CoflCore.classes.Settings;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CoflSettingsScreen {

    public static Screen create(Screen parent) {
        List<Settings> known = CoflCore.config.knownSettings == null ? List.of() : CoflCore.config.knownSettings;
        Map<String, List<Settings>> byCategory = new LinkedHashMap<>();

        for (Settings setting : known) {
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

    private static void sendSet(String key, String value) {
        if (MinecraftClient.getInstance() == null || MinecraftClient.getInstance().getSession() == null) {
            return;
        }
        CoflSkyCommand.processCommand(new String[]{"set", key, value}, MinecraftClient.getInstance().getSession().getUsername());
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
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
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