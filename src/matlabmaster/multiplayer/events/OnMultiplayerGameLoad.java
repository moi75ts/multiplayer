package matlabmaster.multiplayer.events;

import matlabmaster.multiplayer.Client;
import matlabmaster.multiplayer.MultiplayerModPlugin;
import org.json.JSONException;

import java.util.Objects;

public class OnMultiplayerGameLoad {
    public static void onGameLoad() throws JSONException { // Added a method name and made it static for clarity
        if (Objects.equals(MultiplayerModPlugin.getMode(), "client") && MultiplayerModPlugin.getMessageSender() != null) {
            Client.requestStarscapeUpdate();
        }
    }
}