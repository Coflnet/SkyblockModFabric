package com.coflnet.CoflCore;

import com.coflnet.CoflCore.misc.SessionManager;
import net.fabricmc.loader.api.FabricLoader;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

public class CoflCore {
    public static void setSocket(Socket socket) {
        CoflCore.socket = socket;
    }

    private static Socket socket;

    public void setupSocket(String username, Path pathToConfig) throws IOException, URISyntaxException {

        SessionManager.setMainPath(pathToConfig);
        String uri = "wss://sky.coflnet.com/modsocket?player=" + username + "&version=1.5.2-Alpha";
        SessionManager.UpdateCoflSessions();
        String coflSessionID = SessionManager.GetCoflSession(username).SessionUUID;
        uri += "&SId=" + coflSessionID;
        socket = new Socket(new URI(uri));
        socket.connect();
    }

    public void registerEventFile(Object target) {
        EventBus.getDefault().register(target);
    }

    public void unregisterEventFile(Object target) {
        EventBus.getDefault().unregister(target);
    }

    public void sendCommand(String command) {
        if(socket == null) throw new NullPointerException("Socket is not setup");
        socket.send(command);
    }
}
