package matlabmaster.multiplayer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModPlugin;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import matlabmaster.multiplayer.UI.NetworkWindow;
import matlabmaster.multiplayer.utils.CreateSystem;
import matlabmaster.multiplayer.utils.SectorCleanup;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Objects;

import static com.fs.starfarer.api.Global.getSettings;

public class Client implements MessageSender, MessageReceiver {
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean isRunning;
    private Thread listenerThread;
    private MessageReceiver messageHandler;
    private static NetworkWindow networkWindow;

    public Client(String serverIp, int serverPort, MessageReceiver handler) {
        this.messageHandler = handler;
        this.networkWindow = MultiplayerModPlugin.getNetworkWindow();
        try {
            socket = new Socket(serverIp, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            isRunning = true;
            startListener();
            LOGGER.log(Level.INFO, "Client connected to " + serverIp + ":" + serverPort);
        } catch (IOException e) {
            LOGGER.log(Level.ERROR, "Failed to connect to server " + serverIp + ":" + serverPort + ": " + e.getMessage());
            stop();
        }
    }

    private void startListener() {
        listenerThread = new Thread(() -> {
            try {
                String response;
                while (isRunning && (response = in.readLine()) != null) {
                    LOGGER.log(Level.DEBUG, "Received from server: " + response);
                    if (messageHandler != null) {
                        messageHandler.onMessageReceived(response); // Queue the message for processing
                    }
                }
                LOGGER.log(Level.INFO, "Server closed connection gracefully");
            } catch (IOException e) {
                if (isRunning) {
                    LOGGER.log(Level.ERROR, "Error reading from server: " + e.getMessage());
                }
            } finally {
                stop();
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.setName("ClientListener");
        listenerThread.start();
    }

    @Override
    public void sendMessage(String message) {
        if (!isRunning || out == null) {
            LOGGER.log(Level.WARN, "Client not connected, cannot send message: " + message);
            return;
        }
        out.println(message);
        LOGGER.log(Level.DEBUG, "Client sent: " + message);
    }

    @Override
    public void onMessageReceived(String message) {
        if (messageHandler != null) {
            messageHandler.onMessageReceived(message);
        }
    }

    @Override
    public boolean isActive() {
        return isRunning && socket != null && !socket.isClosed() && socket.isConnected();
    }

    public void stop() {
        if (!isRunning) return; // Prevent multiple calls to stop
        isRunning = false;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (listenerThread != null) {
                listenerThread.interrupt();
                listenerThread.join(1000); // Wait up to 1 second for thread to finish
                if (listenerThread.isAlive()) {
                    LOGGER.log(Level.WARN, "Listener thread did not terminate cleanly");
                }
            }
            LOGGER.log(Level.INFO, "Client stopped");
            SwingUtilities.invokeLater(() -> {
                if (networkWindow != null) {
                    networkWindow.updateStatus(false, "Disconnected due to network issue");
                }
            });
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.ERROR, "Error stopping client: " + e.getMessage());
            SwingUtilities.invokeLater(() -> {
                if (networkWindow != null) {
                    networkWindow.updateStatus(false, "Disconnected with error: " + e.getMessage());
                }
            });
        }
    }

    public static void initiateHandShake(MessageSender sender) {
        if (sender != null && sender.isActive()) {
            try {
                JSONObject message = new JSONObject();
                message.put("command", 0);
                message.put("playerId", MultiplayerModPlugin.GetPlayerId());
                String seed = Global.getSector().getSeedString();
                // Put seed even if null
                message.put("seed", seed != null ? seed : "none");
                message.put("gameVersion", getSettings().getVersionString());
                JSONArray modList = new JSONArray();
                for(ModPlugin mod : Global.getSettings().getModManager().getEnabledModPlugins()){
                    modList.put(mod.getClass().getName());
                }
                message.put("modList",modList);
                networkWindow.getMessageField().append("Checking seed: " + seed + "\n");
                LOGGER.log(Level.INFO, "Initiating handshake with seed: " + seed);

                sender.sendMessage(message.toString());
            } catch (JSONException e) {
                LOGGER.log(Level.ERROR, "Failed to initiate handshake: " + e.getMessage());
                throw new RuntimeException(e);
            }
        } else {
            LOGGER.log(Level.WARN, "Cannot initiate handshake: MessageSender is null or not active");
        }
    }

    public static void requestOrbitingBodiesUpdate(String currentLocation) throws JSONException {
        MessageSender sender = MultiplayerModPlugin.getMessageSender();
        if (sender != null && sender.isActive()) {
            try {
                JSONObject message = new JSONObject();
                message.put("command", 6);
                message.put("playerId", MultiplayerModPlugin.GetPlayerId());
                message.put("system", currentLocation);
                sender.sendMessage(message.toString());
            } catch (JSONException e) {
                LOGGER.log(Level.ERROR, "Failed to construct JSON message: " + e.getMessage());
            }
        }
    }

    public static void handleOrbitingBodiesUpdate(JSONObject data) {
        try {
            JSONArray toSync = data.getJSONArray("toSync");
            for (int i = 0; i < toSync.length(); i++) {
                JSONObject objectData = toSync.getJSONObject(i);
                String id = objectData.getString("id");
                float angle = (float) objectData.getDouble("a");
                if (Global.getSector().getEntityById(id) != null) {
                    SectorEntityToken token = Global.getSector().getEntityById(id);
                    token.setCircularOrbitAngle(angle);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Error handling orbiting bodies update: " + e.getMessage());
        }
    }

    public static void requestStarscapeUpdate() throws JSONException {
        MessageSender sender = MultiplayerModPlugin.getMessageSender();
        if (sender != null && sender.isActive()) {
            try {
                JSONObject message = new JSONObject();
                message.put("command", 4);
                message.put("playerId", MultiplayerModPlugin.GetPlayerId());
                sender.sendMessage(message.toString());
            } catch (JSONException e) {
                LOGGER.log(Level.ERROR, "Failed to construct JSON message: " + e.getMessage());
            }
        }
    }

    public static void handleStarscapeUpdate(@NotNull JSONObject data) throws JSONException {
        SectorAPI sector = Global.getSector();
        JSONArray serverSideSystemList = data.getJSONArray("systems");
        SectorCleanup.cleanupSector(data);
        for (int i = 0; i <= serverSideSystemList.length() - 1; i++) {
            CreateSystem.createSystem(serverSideSystemList.getJSONObject(i));
        }
    }

    public static void handleHandshake(JSONObject data) throws JSONException {
        String serverSeed = data.optString("seed", null);  // Use optString to handle null safely
        if (serverSeed == null) {
            networkWindow.getMessageField().append("Server has not started the game yet\n");
            networkWindow.setServerSeed("Not available");  // New method we'll add
        } else if (!Objects.equals(serverSeed, Global.getSector().getSeedString())) {
            networkWindow.getMessageField().append("Save seeds are different, please create a new game with following seed then attempt to reconnect: " + serverSeed + "\n");
            networkWindow.setServerSeed(serverSeed);  // New method we'll add
        } else {
            networkWindow.getMessageField().append("Seed match, continue with handshake\n");
            networkWindow.setServerSeed(serverSeed);  // New method we'll add
        }
    }

    public static void handleDisconnect(JSONObject data) throws JSONException {
        String reason = data.getString("reason");
        LOGGER.log(Level.INFO, "Received disconnect from server: " + reason);
        networkWindow.getMessageField().append("Server kicked, client disconnected, reason: " + reason + "\n");
        SwingUtilities.invokeLater(() -> {
            if (networkWindow != null) {
                networkWindow.updateStatus(false, "Disconnected: " + reason);
            }
        });
    }
}