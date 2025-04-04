package matlabmaster.multiplayer;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import matlabmaster.multiplayer.events.HyperspaceEntryScript;
import matlabmaster.multiplayer.events.OnMultiplayerGameLoad;
import matlabmaster.multiplayer.events.SystemEntryScript;
import matlabmaster.multiplayer.events.UnpauseScript;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;
import java.util.UUID;

public class MultiplayerModPlugin extends BaseModPlugin {
    public static JSONObject settings;
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");
    private static Server server;
    private static Client client;
    private static MessageSender messageSender;
    private static MessageHandler messageHandler; // Added for message receiving
    private Thread serverThread;
    public static String playerId = UUID.randomUUID().toString(); // Static playerId as in your version
    public static String mode;
    @Override
    public void onApplicationLoad() throws Exception {
        super.onApplicationLoad();
        LOGGER.log(Level.INFO, "Multiplayer mod is alive");
        LOGGER.log(Level.INFO, "Player id is " + playerId);

        settings = Global.getSettings().loadJSON("data/config/settings.json", "matlabmaster_multiplayer");
        LOGGER.log(Level.INFO, "Configuration loaded : " + settings);

        // Initialize the message handler with the playerId
        messageHandler = new MessageHandler(playerId);

        try {
            JSONObject gameSettings = settings.getJSONObject("gameSettings");
            mode = gameSettings.getString("mode");
            LOGGER.log(Level.INFO, "Mode: " + mode);
        } catch (JSONException e) {
            LOGGER.log(Level.ERROR, "Failed to read mode from config, defaulting to server: " + e.getMessage());
            mode = "server";
        }

        if (Objects.equals(mode, "server")) {
            int serverPort;
            try {
                JSONObject serverSettings = settings.getJSONObject("serverSettings");
                serverPort = serverSettings.getInt("port");
                LOGGER.log(Level.INFO, "Server port from config: " + serverPort);
            } catch (JSONException e) {
                LOGGER.log(Level.ERROR, "Failed to read server port from config, defaulting to 4444: " + e.getMessage());
                serverPort = 4444;
            }

            server = new Server(serverPort, messageHandler); // Pass messageHandler
            messageSender = server;
            serverThread = new Thread(() -> server.start());
            serverThread.setDaemon(true);
            serverThread.setName("ServerThread");
            serverThread.start();
        } else if (Objects.equals(mode, "client")) {
            String serverIp;
            int serverPort;
            try {
                JSONObject clientSettings = settings.getJSONObject("clientSettings");
                serverIp = clientSettings.getString("serverIp");
                serverPort = clientSettings.getInt("serverPort");
                LOGGER.log(Level.INFO, "Connecting client to " + serverIp + ":" + serverPort);
            } catch (JSONException e) {
                LOGGER.log(Level.ERROR, "Failed to read client settings, defaulting to 127.0.0.1:4444: " + e.getMessage());
                serverIp = "127.0.0.1";
                serverPort = 4444;
            }

            client = new Client(serverIp, serverPort, messageHandler); // Pass messageHandler
            messageSender = client;
        }
    }

    @Override
    public void onGameLoad(boolean newGame) {
        Global.getSector().addTransientScript(new FastUpdateScript());
        Global.getSector().addTransientScript(new SlowUpdateScript());
        Global.getSector().addTransientScript(new MessageProcessingScript());
        Global.getSector().addTransientScript(new SystemEntryScript());
        Global.getSector().addTransientScript(new HyperspaceEntryScript());
        Global.getSector().addTransientScript(new UnpauseScript());

        try {
            OnMultiplayerGameLoad.onGameLoad();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public static JSONObject getSettings() {
        return settings;
    }

    public static MessageSender getMessageSender() {
        return messageSender;
    }

    public static String GetPlayerId() {
        return playerId;
    }

    public void onApplicationShutdown() {
        if (server != null) {
            server.stop();
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
        if (client != null) {
            client.stop();
        }
    }

    public static String getMode(){
        return mode;
    }
}