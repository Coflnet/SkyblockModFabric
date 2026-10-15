package com.coflnet.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConnectHostValidatorTest {
    @Test void extractsAndRestrictsCoflnetHosts() {
        assertEquals("socket.coflnet.com", ConnectHostValidator.extractHost(
                "wss://socket.coflnet.com/modsocket?version=3"));
        assertTrue(ConnectHostValidator.isTrusted("API.COFLNET.COM."));
        assertFalse(ConnectHostValidator.isTrusted("coflnet.com.evil.example"));
        assertFalse(ConnectHostValidator.isTrusted("hypixel.net"));
    }
}
