package matlabmaster.multiplayer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModPlugin;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.*;
import matlabmaster.multiplayer.utils.CargoHelper;
import matlabmaster.multiplayer.utils.FleetHelper;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.fs.starfarer.api.Global.getSettings;
import static matlabmaster.multiplayer.MultiplayerModPlugin.networkWindow;

public class Server implements MessageSender, MessageReceiver {
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");
    private final int port;
    private ServerSocket serverSocket;
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private boolean isRunning = false;
    private MessageReceiver messageHandler;

    public Server(int port, MessageReceiver handler) {
        this.port = port;
        this.messageHandler = handler;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;
            System.out.println("Server started on port " + port + " with id " + User.getUserId());
            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                executorService.execute(clientHandler);
            }
        } catch (IOException e) {
            if (isRunning) {
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            executorService.shutdown();
            for (ClientHandler client : clients.values()) {
                client.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void sendMessage(String message) {
        broadcast(message);
    }

    public void broadcast(String message) {
        for (ClientHandler client : clients.values()) {
            client.sendMessage(message);
        }
    }

    public void sendTo(String userId, String message) {
        ClientHandler client = clients.get(userId);
        if (client != null) {
            client.sendMessage(message);
        }
    }

    public void sendToEveryoneBut(String userId, String message) {
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            if (!entry.getKey().equals(userId)) {
                entry.getValue().sendMessage(message);
            }
        }
    }

    @Override
    public boolean isActive() {
        return isRunning;
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private final PrintWriter out;
        private final BufferedReader in;
        private String userId;

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }

        @Override
        public void run() {
            try {
                userId = in.readLine();
                if (userId != null) {
                    clients.put(userId, this);
                    System.out.println("Client connected: " + userId);
                }

                String message;
                while ((message = in.readLine()) != null) {
                    messageHandler.onMessageReceived(message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                close();
            }
        }

        public void sendMessage(String message) {
            out.println(message);
        }

        public void close() {
            try {
                if (userId != null) {
                    clients.remove(userId);
                    System.out.println("Client disconnected: " + userId);
                }
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void broadcastMessage(String message) {
        sendMessage(message);
    }

    public void sendToAllExcept(String message, String excludedPlayerId) {
        if (!isRunning) {
            LOGGER.log(Level.WARN, "Server not running, cannot send message: " + message);
            return;
        }
        synchronized (clients) {
            for (ClientHandler conn : clients.values()) {
                if (!Objects.equals(conn.userId, excludedPlayerId)) {
                    conn.sendMessage(message);
                }
            }
        }
        LOGGER.log(Level.DEBUG, "Server sent to all except " + excludedPlayerId + ": " + message);
    }

    public void replyTo(String message, String targetPlayerId) {
        if (!isRunning) {
            LOGGER.log(Level.WARN, "Server not running, cannot send message: " + message);
            return;
        }
        synchronized (clients) {
            for (ClientHandler conn : clients.values()) {
                if (Objects.equals(conn.userId, targetPlayerId)) {
                    conn.sendMessage(message);
                    LOGGER.log(Level.DEBUG, "Server replied to " + targetPlayerId + ": " + message);
                    return;
                }
            }
        }
        LOGGER.log(Level.WARN, "Could not find player " + targetPlayerId + " to reply to");
    }

    public void sendToPlayers(String message, List<String> targetPlayerIds) {
        if (!isRunning) {
            LOGGER.log(Level.WARN, "Server not running, cannot send message: " + message);
            return;
        }
        synchronized (clients) {
            for (ClientHandler conn : clients.values()) {
                if (targetPlayerIds.contains(conn.userId)) {
                    conn.sendMessage(message);
                }
            }
        }
        LOGGER.log(Level.DEBUG, "Server sent to players " + targetPlayerIds + ": " + message);
    }

    @Override
    public void onMessageReceived(String message) {
        if (messageHandler != null) {
            messageHandler.onMessageReceived(message);
        }
    }

    public static void sendOrbitingBodiesUpdate(JSONObject data) throws JSONException {
        try {
            JSONObject message = new JSONObject();
            message.put("command", 6);
            message.put("playerId", "server");
            LocationAPI system = Global.getSector().getStarSystem(data.getString("system"));
            List<SectorEntityToken> stableLocations = system.getAllEntities();
            JSONArray toSync = new JSONArray();
            for (SectorEntityToken entity : stableLocations) {
                if (entity.getCustomEntityType() == "orbital_junk" || entity.getCustomEntityType() == "null") {
                } else {
                    JSONObject thing = new JSONObject();
                    thing.put("id", entity.getId());
                    thing.put("a", entity.getCircularOrbitAngle());
                    toSync.put(thing);
                }
            }
            message.put("toSync", toSync);
            Server serverInstance = (Server) MultiplayerModPlugin.getMessageSender();
            serverInstance.sendTo(data.getString("playerId"), message.toString());
        } catch (JSONException e) {
            LOGGER.log(Level.ERROR, "Failed to construct JSON message: " + e.getMessage());
        }
    }

    public static void sendStarscapeUpdate() throws JSONException {
        MessageSender sender = MultiplayerModPlugin.getMessageSender();
        if (sender != null && sender.isActive()) {
            try {
                JSONObject message = new JSONObject();
                message.put("playerId", "server");
                message.put("command", 4);
                SectorAPI sector = Global.getSector();
                List<StarSystemAPI> rawSystemData = sector.getStarSystems();
                JSONArray systemList = new JSONArray();
                for (StarSystemAPI system : rawSystemData) {
                    if (Objects.equals(system.getId(), "deep space")) {
                        continue;
                    }
                    JSONObject systemData = new JSONObject();
                    systemData.put("systemID", system.getId());
                    systemData.put("type", system.getType());
                    systemData.put("age", system.getAge());
                    try {
                        systemData.put("constellation", system.getConstellation().getName());
                        systemData.put("constellationType", system.getConstellation().getType().toString());
                    } catch (NullPointerException e) {
                    }
                    systemData.put("locationx", system.getLocation().x);
                    systemData.put("locationy", system.getLocation().y);
                    systemData.put("centerid", system.getCenter().getId());
                    if (system.getStar() != null) {
                        systemData.put("firstStar", system.getStar().getId());
                    }
                    if (system.getSecondary() != null) {
                        systemData.put("secondStar", system.getSecondary().getId());
                    }
                    if (system.getTertiary() != null) {
                        systemData.put("thirdStar", system.getTertiary().getId());
                    }
                    JSONArray planetList = new JSONArray();
                    List<PlanetAPI> rawPlanetsData = system.getPlanets();
                    for (PlanetAPI planet : rawPlanetsData) {
                        JSONObject planetData = new JSONObject();
                        planetData.put("planetid", planet.getId());
                        planetData.put("type", planet.getTypeId());
                        planetData.put("name", planet.getName());
                        planetData.put("locationx", planet.getLocation().x);
                        planetData.put("locationy", planet.getLocation().y);
                        planetData.put("orbitAngle", planet.getCircularOrbitAngle());
                        planetData.put("orbitPeriod", planet.getCircularOrbitPeriod());
                        planetData.put("orbitRadius", planet.getCircularOrbitRadius());
                        try {
                            planetData.put("orbitFocusId", planet.getOrbitFocus().getId());
                        } catch (Exception e) {
                        }
                        planetData.put("radius", planet.getRadius());
                        planetData.put("isStar", planet.isStar());
                        if (planet.isStar()) {
                            planetData.put("hyperspaceLocationX", planet.getLocationInHyperspace().x);
                            planetData.put("hyperspaceLocationY", planet.getLocationInHyperspace().y);
                            planetData.put("coronaSize", planet.getSpec().getCoronaSize());
                        }
                        planetList.put(planetData);
                    }
                    systemData.put("planets", planetList);
                    systemList.put(systemData);
                }
                message.put("systems", systemList);
                sender.sendMessage(message.toString());
            } catch (JSONException e) {
                LOGGER.log(Level.ERROR, "sendStarscapeUpdate : Failed to construct JSON message: " + e.getMessage());
            }
        }
    }

    public static void handleHandshake(JSONObject data) throws JSONException {
        JSONObject message = new JSONObject();
        Server serverInstance = (Server) MultiplayerModPlugin.getMessageSender();
        message.put("command", 0);
        message.put("playerId", "server");
        message.put("seed", Global.getSector().getSeedString());
        String clientSeed = data.getString("seed");
        String clientPlayerId = data.getString("playerId");
        String clientIp = "unknown";
        JSONArray clientModlist = data.getJSONArray("modList");
        String clientGameVersion = data.getString("gameVersion");
        JSONArray modList = new JSONArray();
        for (ModPlugin mod : Global.getSettings().getModManager().getEnabledModPlugins()) {
            modList.put(mod.getClass().getName());
        }

        if (serverInstance == null || !serverInstance.isActive()) {
            LOGGER.log(Level.WARN, "Server not active, cannot handle handshake");
            return;
        }

        ClientHandler targetConnection = serverInstance.clients.get(clientPlayerId);
        if (targetConnection != null) {
            clientIp = targetConnection.socket.getInetAddress().getHostAddress();
        }

        if (!Objects.equals(clientModlist.toString(), modList.toString())) {
            if (targetConnection != null) {
                serverInstance.disconnectClient(targetConnection, "mod mismatch, please install following mods then reconnect : " + modList, Global.getSector().getSeedString());
            }
            networkWindow.getMessageField().append("Client " + clientPlayerId + " mod mismatch, kicking\n");
            return;
        }
        networkWindow.getMessageField().append("Client " + clientPlayerId + " mod matching, continue\n");

        if (!Objects.equals(clientGameVersion, getSettings().getVersionString())) {
            if (targetConnection != null) {
                serverInstance.disconnectClient(targetConnection, "game Version mismatch: " + getSettings().getVersionString(), getSettings().getVersionString());
            }
            networkWindow.getMessageField().append("Client " + clientPlayerId + " version mismatch, kicking\n");
            return;
        }
        networkWindow.getMessageField().append("Client " + clientPlayerId + " game version identical, continue\n");

        if (networkWindow != null && networkWindow.getMessageField() != null) {
            networkWindow.getMessageField().append("Client " + clientPlayerId + " connected with IP: " +
                    clientIp + ", with seed: " + clientSeed + "\n");
        }

        if (!Objects.equals(clientSeed, Global.getSector().getSeedString())) {
            if (networkWindow != null && networkWindow.getMessageField() != null) {
                networkWindow.getMessageField().append("Client " + clientPlayerId + " seed mismatch, kicking\n");
            }
            if (targetConnection != null) {
                serverInstance.disconnectClient(targetConnection, "seed mismatch, create new save with seed : " +
                        Global.getSector().getSeedString() + " then reconnect", Global.getSector().getSeedString());
            }
        } else {
            if (networkWindow != null && networkWindow.getMessageField() != null) {
                networkWindow.getMessageField().append("Client " + clientPlayerId + " seed match\nClient connected");
            }
            serverInstance.replyTo(message.toString(), clientPlayerId);
        }
    }

    private void disconnectClient(ClientHandler connection, String reason, String seed) {
        synchronized (clients) {
            if (connection != null && clients.containsValue(connection)) {
                try {
                    JSONObject disconnectMsg = new JSONObject();
                    disconnectMsg.put("command", 1);
                    disconnectMsg.put("reason", reason);
                    disconnectMsg.put("playerId", "server");
                    disconnectMsg.put("seed", seed);
                    connection.sendMessage(disconnectMsg.toString());
                    connection.close();
                    LOGGER.log(Level.INFO, "Disconnected client " + connection.userId + " (" +
                            connection.socket.getInetAddress() + ") due to " + reason);
                    onLeave(connection);
                } catch (JSONException e) {
                    LOGGER.log(Level.ERROR, "Error disconnecting client " + connection.userId + ": " + e.getMessage());
                }
            }
        }
    }

    private void onLeave(ClientHandler connection) {
        if (connection != null && connection.userId != null) {
            LOGGER.log(Level.INFO, "Player " + connection.userId + " has left the server");
            if (networkWindow != null && networkWindow.getMessageField() != null) {
                networkWindow.getMessageField().append("Player " + connection.userId + " has left\n");
            }
            removePlayerFleet(connection.userId);
        }
    }

    private void removePlayerFleet(String playerId) {
        if (playerId == null) return;

        SectorEntityToken entity = Global.getSector().getEntityById(playerId);
        if (entity instanceof CampaignFleetAPI) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) entity;
            if (fleet.getContainingLocation() != null) {
                fleet.getContainingLocation().removeEntity(fleet);
            }
            fleet.despawn();
            LOGGER.log(Level.INFO, "Removed fleet for player " + playerId);
            if (networkWindow != null && networkWindow.getMessageField() != null) {
                networkWindow.getMessageField().append("Removed fleet for player " + playerId + "\n");
            }
        } else {
            LOGGER.log(Level.WARN, "No fleet found with ID " + playerId);
        }
    }
}