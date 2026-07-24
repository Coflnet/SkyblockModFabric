package com.coflnet.lore;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoreSaveResponseTest {
    @Test
    void findsTheExactSuccessChatPart() {
        assertEquals(
                LoreSaveResponse.Result.SUCCESS,
                LoreSaveResponse.classify(
                        "chatMessage",
                        JsonParser.parseString("""
                                "[{\\"text\\":\\"[Coflnet]: \\"},{\\"text\\":\\"Imported settings (check above)\\"},{\\"text\\":\\"\\\\n\\"}]"
                                """)));
    }

    @Test
    void doesNotAcceptACombinedOrSimilarSuccessMessage() {
        assertEquals(
                LoreSaveResponse.Result.NONE,
                LoreSaveResponse.classify(
                        "chatMessage",
                        JsonParser.parseString("""
                                "[{\\"text\\":\\"[Coflnet]: Imported settings (check above)\\"}]"
                                """)));
    }

    @Test
    void findsTheBackendRejection() {
        assertEquals(
                LoreSaveResponse.Result.REJECTED,
                LoreSaveResponse.classify(
                        "writeToChat",
                        JsonParser.parseString("""
                                "{\\"text\\":\\"Could not parse the arguments for lore because the value was invalid\\"}"
                                """)));
    }
}
