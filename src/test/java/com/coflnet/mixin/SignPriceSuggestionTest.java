package com.coflnet.mixin;

import CoflCore.handlers.DescriptionHandler;
import com.google.gson.Gson;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.input.CharacterEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Runs the sign mixin's init callbacks with vanilla's real input helper, without a GL client. */
class SignPriceSuggestionTest {
    private Field infoDisplay;
    private Object previousInfoDisplay;

    @BeforeEach
    void suggestPrice() throws Exception {
        infoDisplay = DescriptionHandler.class.getDeclaredField("infoDisplay");
        infoDisplay.setAccessible(true);
        previousInfoDisplay = infoDisplay.get(null);
        infoDisplay.set(null, new Gson().fromJson(
                "[{\"type\":\"SUGGEST\",\"value\":\"Enter price: 1,234\"}]",
                DescriptionHandler.DescModification[].class));
    }

    @AfterEach
    void restoreSuggestions() throws Exception {
        infoDisplay.set(null, previousInfoDisplay);
    }

    @Test
    void firstDigitReplacesSuggestionAndLaterDigitsAppend() throws Exception {
        Editor editor = new Editor("1,234", "Enter price");
        editor.init();
        editor.type('5');
        assertEquals("5", editor.messages[0]);
        editor.type('6');
        assertEquals("56", editor.messages[0]);
    }

    @Test
    void acceptingSuggestionDoesNotChangeItsValue() throws Exception {
        Editor editor = new Editor("1,234", "Enter price");
        editor.init();
        assertEquals("1,234", editor.messages[0]);
    }

    @Test
    void unrelatedSignsAndDifferentValuesKeepNormalTyping() throws Exception {
        for (Editor editor : new Editor[] {
                new Editor("1,234", "Search"), new Editor("99", "Enter price")}) {
            String original = editor.messages[0];
            editor.init();
            editor.type('5');
            assertEquals(original + "5", editor.messages[0]);
        }
    }

    @Test
    void absentSuggestionKeepsNormalTyping() throws Exception {
        infoDisplay.set(null, new DescriptionHandler.DescModification[0]);
        Editor editor = new Editor("1,234", "Enter price");
        editor.init();
        editor.type('5');
        assertEquals("1,2345", editor.messages[0]);
    }

    @Test
    void cursorMovementAllowsEditingAndResizeDoesNotReselect() throws Exception {
        Editor editor = new Editor("1,234", "Enter price");
        editor.init();
        editor.input.setCursorToEnd();
        editor.init();
        assertFalse(editor.input.isSelecting());
        editor.type('5');
        assertEquals("1,2345", editor.messages[0]);
    }

    @Test
    void nextSignGetsItsOwnSelection() throws Exception {
        Editor first = new Editor("1,234", "Enter price");
        first.init();
        first.type('5');
        first.init();
        first.type('6');
        assertEquals("56", first.messages[0]);
        Editor second = new Editor("1,234", "Enter price");
        second.init();
        second.type('7');
        assertEquals("7", second.messages[0]);
    }

    private static final class Editor {
        final String[] messages;
        final TextFieldHelper input;
        final SignEditScreenMixin mixin = new SignEditScreenMixin();

        Editor(String price, String prompt) throws Exception {
            messages = new String[] {price, "", "", prompt};
            input = new TextFieldHelper(() -> messages[0], value -> messages[0] = value,
                    () -> "", value -> {}, value -> true);
            input.setCursorToEnd();
            bindShadow("messages", messages);
            bindShadow("signField", input);
        }

        private void bindShadow(String name, Object value) throws Exception {
            // The base mixin has no input shadows or init callback. It still runs vanilla typing,
            // so comparison fails on the resulting price, not on a missing newly introduced API.
            for (Field field : SignEditScreenMixin.class.getDeclaredFields()) {
                if (field.getName().equals(name)) {
                    field.setAccessible(true);
                    field.set(mixin, value);
                }
            }
        }

        void init() throws Exception {
            for (var method : SignEditScreenMixin.class.getDeclaredMethods()) {
                Inject injection = method.getAnnotation(Inject.class);
                if (injection != null && Arrays.asList(injection.method()).contains("init")) {
                    method.setAccessible(true);
                    method.invoke(mixin, new CallbackInfo("init", false));
                }
            }
        }

        void type(char digit) {
            input.charTyped(new CharacterEvent(digit));
        }
    }
}
