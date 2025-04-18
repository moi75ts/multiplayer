package matlabmaster.multiplayer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModPlugin;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import matlabmaster.multiplayer.UI.NetworkWindow;
import matlabmaster.multiplayer.utils.CargoHelper;
import matlabmaster.multiplayer.utils.FleetHelper;
import matlabmaster.multiplayer.utils.MarketUpdateHelper;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;

import static com.fs.starfarer.api.Global.getSettings;

public class Client implements MessageSender, MessageReceiver {
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");
    private final String serverIp;
    private final int serverPort;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private boolean isConnected = false;
    private final MessageReceiver messageHandler;
    private static NetworkWindow networkWindow;

    public Client(String serverIp, int serverPort, MessageReceiver messageHandler) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.messageHandler = messageHandler;
        networkWindow = MultiplayerModPlugin.getNetworkWindow();
    }

    public void connect() {
        try {
            socket = new Socket(serverIp, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream())); // Fixed: Use getInputStream()
            out.println(User.getUserId());
            isConnected = true;
            executorService.execute(this::listenForMessages);
            System.out.println("Connected to server at " + serverIp + ":" + serverPort);
        } catch (IOException e) {
            e.printStackTrace();
            close();
        }
    }

    private void listenForMessages() {
        try {
            String message;
            while (isConnected && (message = in.readLine()) != null) {
                if (messageHandler != null) {
                    messageHandler.onMessageReceived(message);
                }
            }
        } catch (IOException e) {
            if (isConnected) {
                e.printStackTrace();
            }
        } finally {
            close();
        }
    }

    @Override
    public void sendMessage(String message) {
        if (isConnected) {
            out.println(message);
        }
    }

    @Override
    public void onMessageReceived(String message) {
        if (messageHandler != null) {
            messageHandler.onMessageReceived(message);
        }
    }

    public void close() {
        isConnected = false;
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
            executorService.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        close();
    }

    @Override
    public boolean isActive() {
        return isConnected;
    }

    public String getUserId() {
        return User.getUserId();
    }

    public static void initiateHandShake(MessageSender sender) {
        if (sender != null && sender.isActive()) {
            try {
                JSONObject message = new JSONObject();
                message.put("command", 0);
                message.put("playerId", User.getUserId());
                String seed = Global.getSector().getSeedString();
                message.put("seed", seed != null ? seed : "none");
                message.put("gameVersion", getSettings().getVersionString());
                JSONArray modList = new JSONArray();
                for (ModPlugin mod : Global.getSettings().getModManager().getEnabledModPlugins()) {
                    modList.put(mod.getClass().getName());
                }
                message.put("modList", modList);
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
                message.put("playerId", User.getUserId());
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
                    if (!token.isPlayerFleet()) {
                        token.setCircularOrbitAngle(angle);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Error handling orbiting bodies update: " + e.getMessage());
        }
    }

    public static void requestStarscapeUpdate() throws JSONException {
        // MessageSender sender = MultiplayerModPlugin.getMessageSender();
        // if (sender != null && sender.isActive()) {
        //     try {
        //         JSONObject message = new JSONObject();
        //         message.put("command", 4);
        //         message.put("playerId", User.getUserId());
        //         sender.sendMessage(message.toString());
        //     } catch (JSONException e) {
        //         LOGGER.log(Level.ERROR, "Failed to construct JSON message: " + e.getMessage());
        //     }
        // }
    }

    public static void handleStarscapeUpdate(@NotNull JSONObject data) throws JSONException {
        // SectorAPI sector = Global.getSector();
        // JSONArray serverSideSystemList = data.getJSONArray("systems");
        // SectorCleanup.cleanupSector(data);
        // for (int i = 0; i <= serverSideSystemList.length() - 1; i++) {
        //     CreateSystem.createSystem(serverSideSystemList.getJSONObject(i));
        // }
    }

    public static void handleHandshake(JSONObject data) throws JSONException {
        String serverSeed = data.optString("seed", null);
        if (serverSeed == null) {
            networkWindow.getMessageField().append("Server has not started the game yet\n");
            networkWindow.setServerSeed("Not available");
        } else if (!Objects.equals(serverSeed, Global.getSector().getSeedString())) {
            networkWindow.getMessageField().append("Save seeds are different, please create a new game with following seed then attempt to reconnect: " + serverSeed + "\n");
            networkWindow.setServerSeed(serverSeed);
        } else {
            networkWindow.getMessageField().append("Seed match, continue with handshake\n");
            networkWindow.setServerSeed(serverSeed);
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

    public static void sendMarketUpdate(List<MarketAPI> markets) throws JSONException {
        MarketUpdateHelper.sendMarketUpdate(markets);
    }
}