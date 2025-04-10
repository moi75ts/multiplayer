package matlabmaster.multiplayer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModPlugin;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.campaign.*;
import com.fs.starfarer.combat.entities.terrain.Planet;
import matlabmaster.multiplayer.commands.ServerInit;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;

import org.lazywizard.console.Console;

import static com.fs.starfarer.api.Global.getSettings;
import static matlabmaster.multiplayer.MultiplayerModPlugin.networkWindow;

public class Server implements MessageSender, MessageReceiver {
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private volatile boolean isRunning;
    private List<ClientConnection> clientConnections;
    private MessageReceiver messageHandler;

    private static class ClientConnection {
        PrintWriter writer;
        Socket socket;
        BufferedReader reader;
        String playerId;

        ClientConnection(PrintWriter writer, Socket socket, BufferedReader reader, String playerId) {
            this.writer = writer;
            this.socket = socket;
            this.reader = reader;
            this.playerId = playerId;
        }
    }

    public Server(int port, MessageReceiver handler) {
        this.messageHandler = handler;
        try {
            serverSocket = new ServerSocket(port);
            executor = Executors.newCachedThreadPool();
            clientConnections = new ArrayList<>();
            isRunning = true;
            LOGGER.log(Level.INFO, "Server initialized on port " + port);
        } catch (IOException e) {
            LOGGER.log(Level.ERROR, "Failed to start server on port " + port + ": " + e.getMessage());
            throw new RuntimeException(e);
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
        synchronized (clientConnections) {
            for (ClientConnection conn : clientConnections) {
                if (!Objects.equals(conn.playerId, excludedPlayerId)) {
                    conn.writer.println(message);
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
        synchronized (clientConnections) {
            for (ClientConnection conn : clientConnections) {
                if (Objects.equals(conn.playerId, targetPlayerId)) {
                    conn.writer.println(message);
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
        synchronized (clientConnections) {
            for (ClientConnection conn : clientConnections) {
                if (targetPlayerIds.contains(conn.playerId)) {
                    conn.writer.println(message);
                }
            }
        }
        LOGGER.log(Level.DEBUG, "Server sent to players " + targetPlayerIds + ": " + message);
    }

    public void start() {
        LOGGER.log(Level.INFO, "Server started on port " + serverSocket.getLocalPort());
        try {
            while (isRunning && !serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                LOGGER.log(Level.INFO, "New client connected: " + clientSocket.getInetAddress());
                executor.execute(() -> handleClientConnection(clientSocket));
            }
        } catch (IOException e) {
            if (isRunning) {
                LOGGER.log(Level.ERROR, "Server error: " + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    private void handleClientConnection(Socket clientSocket) {
        BufferedReader in = null;
        PrintWriter out = null;
        String clientPlayerId = null;
        ClientConnection connection = null;

        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            String firstMessage = in.readLine();
            if (firstMessage != null) {
                JSONObject data = new JSONObject(firstMessage);
                clientPlayerId = data.getString("playerId");
                LOGGER.log(Level.DEBUG, "Received initial message from client " + clientSocket.getInetAddress() + ": " + firstMessage);
                if (messageHandler != null) {
                    messageHandler.onMessageReceived(firstMessage);
                }
            }

            connection = new ClientConnection(out, clientSocket, in, clientPlayerId);
            synchronized (clientConnections) {
                clientConnections.add(connection);
            }

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                LOGGER.log(Level.DEBUG, "Received from client " + clientSocket.getInetAddress() + " (PlayerID: " + clientPlayerId + "): " + inputLine);
                if (messageHandler != null) {
                    messageHandler.onMessageReceived(inputLine);
                }
            }
            LOGGER.log(Level.INFO, "Client " + clientPlayerId + " disconnected gracefully");
            onLeave(connection);
        } catch (IOException | JSONException e) {
            LOGGER.log(Level.ERROR, "Client " + clientPlayerId + " connection error: " + e.getMessage());
            if (connection != null) {
                onLeave(connection);
            }
        } finally {
            if (out != null) {
                synchronized (clientConnections) {
                    PrintWriter finalOut = out;
                    clientConnections.removeIf(conn -> conn.writer == finalOut);
                }
            }
            closeQuietly(in);
            closeQuietly(out);
            try {
                clientSocket.close();
            } catch (IOException e) {
                LOGGER.log(Level.ERROR, "Error closing client socket: " + e.getMessage());
            }
        }
    }

    @Override
    public void sendMessage(String message) {
        if (!isRunning) {
            LOGGER.log(Level.WARN, "Server not running, cannot send message: " + message);
            return;
        }
        synchronized (clientConnections) {
            for (ClientConnection conn : clientConnections) {
                conn.writer.println(message);
            }
        }
        LOGGER.log(Level.DEBUG, "Server broadcasted: " + message);
    }

    @Override
    public void onMessageReceived(String message) {
        if (messageHandler != null) {
            messageHandler.onMessageReceived(message);
        }
    }

    @Override
    public boolean isActive() {
        return isRunning && !serverSocket.isClosed();
    }

    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (executor != null) {
                executor.shutdown();
            }
            synchronized (clientConnections) {
                for (ClientConnection conn : clientConnections) {
                    onLeave(conn);  // Call onLeave for each connection during shutdown
                    closeQuietly(conn.writer);
                    closeQuietly(conn.reader);
                    conn.socket.close();
                }
                clientConnections.clear();
            }
            LOGGER.log(Level.INFO, "Server stopped");
        } catch (IOException e) {
            LOGGER.log(Level.ERROR, "Error stopping server: " + e.getMessage());
        }
    }

    private void closeQuietly(Closeable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    private void disconnectClient(ClientConnection connection, String reason, String seed) {
        synchronized (clientConnections) {
            if (connection != null && clientConnections.contains(connection)) {
                try {
                    JSONObject disconnectMsg = new JSONObject();
                    disconnectMsg.put("command", 1);
                    disconnectMsg.put("reason", reason);
                    disconnectMsg.put("playerId", "server");
                    disconnectMsg.put("seed", seed);
                    connection.writer.println(disconnectMsg.toString());
                    closeQuietly(connection.writer);
                    closeQuietly(connection.reader);
                    connection.socket.close();
                    clientConnections.remove(connection);
                    LOGGER.log(Level.INFO, "Disconnected client " + connection.playerId + " (" +
                            connection.socket.getInetAddress() + ") due to " + reason);
                    onLeave(connection);  // Call onLeave when explicitly disconnecting
                } catch (IOException | JSONException e) {
                    LOGGER.log(Level.ERROR, "Error disconnecting client " + connection.playerId + ": " + e.getMessage());
                }
            }
        }
    }

    // New onLeave method
    private void onLeave(ClientConnection connection) {
        if (connection != null && connection.playerId != null) {
            LOGGER.log(Level.INFO, "Player " + connection.playerId + " has left the server");
            if (networkWindow != null && networkWindow.getMessageField() != null) {
                networkWindow.getMessageField().append("Player " + connection.playerId + " has left\n");
            }
            removePlayerFleet(connection.playerId);  // Example usage: remove the fleet
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

    public static void sendOrbitingBodiesUpdate(String systemName) throws JSONException {
        MessageSender sender = MultiplayerModPlugin.getMessageSender();
        if (sender != null && sender.isActive()) {
            try {
                JSONObject message = new JSONObject();
                message.put("command", 6);
                message.put("playerId", "server");
                LocationAPI system = Global.getSector().getStarSystem(systemName);
                List<SectorEntityToken> stableLocations = system.getAllEntities();
                JSONArray toSync = new JSONArray();
                for (SectorEntityToken entity : stableLocations) {
                    if (entity.getCustomEntityType() == "orbital_junk" || entity.getCustomEntityType() == "null") {
                        // don't sync useless / not important
                    } else {
                        JSONObject thing = new JSONObject();
                        thing.put("id", entity.getId());
                        thing.put("a", entity.getCircularOrbitAngle());
                        toSync.put(thing);
                    }
                }
                message.put("toSync", toSync);
                sender.sendMessage(message.toString());
            } catch (JSONException e) {
                LOGGER.log(Level.ERROR, "Failed to construct JSON message: " + e.getMessage());
            }
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
        for(ModPlugin mod : Global.getSettings().getModManager().getEnabledModPlugins()){
            modList.put(mod.getClass().getName());
        }

        if (serverInstance == null || !serverInstance.isActive()) {
            LOGGER.log(Level.WARN, "Server not active, cannot handle handshake");
            return;
        }

        ClientConnection targetConnection = null;
        synchronized (serverInstance.clientConnections) {
            for (ClientConnection conn : serverInstance.clientConnections) {
                if (Objects.equals(conn.playerId, clientPlayerId)) {
                    targetConnection = conn;
                    clientIp = conn.socket.getInetAddress().getHostAddress();
                    break;
                }
            }
        }

        if(!Objects.equals(clientModlist.toString(), modList.toString())){
            //TODO find a way to check if mod versions are identical
            serverInstance.disconnectClient(targetConnection, "mod mismatch, please install following mods then reconnect : " + modList, Global.getSector().getSeedString());
            networkWindow.getMessageField().append("Client " + clientPlayerId + " mod mismatch, kicking\n");
            return;
        }
        networkWindow.getMessageField().append("Client " + clientPlayerId + " mod matching, continue\n");

        if(!Objects.equals(clientGameVersion, getSettings().getVersionString())){
            serverInstance.disconnectClient(targetConnection, "game Version mismatch: " + getSettings().getVersionString(), getSettings().getVersionString());
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

    public static void handleMarketUpdateRequest(String playerId) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("playerId","server");
        message.put("command",2);
        EconomyAPI economy = Global.getSector().getEconomy();
        JSONArray markets = new JSONArray();
        List<LocationAPI> systemsWithMarkets = economy.getLocationsWithMarkets();
        for (LocationAPI systemWithMarkets : systemsWithMarkets){
            for (MarketAPI market: economy.getMarkets(systemWithMarkets)){
                JSONObject marketJson = new JSONObject();
                //Stability cannot be set since it is calculated from various conditions
                if(market.isPlayerOwned()){
                    marketJson.put("ownerFactionId","player");
                }else{
                    marketJson.put("ownerFactionId",market.getFactionId());
                }
                marketJson.put("marketId", market.getId());
                marketJson.put("name", market.getName());
                marketJson.put("marketSize",market.getSize());
                marketJson.put("isFreePort", market.isFreePort());
                marketJson.put("hasSpaceport", market.hasSpaceport());
                marketJson.put("hasWaystation", market.hasWaystation());
                marketJson.put("primaryEntity",market.getPrimaryEntity().getId());
                marketJson.put("marketSystem",market.getStarSystem().getId());

                JSONArray connectedEntities = new JSONArray();
                Set<SectorEntityToken> connectedClientEntities = market.getConnectedEntities();
                for(SectorEntityToken entity : connectedClientEntities){
                    JSONObject entityObject = new JSONObject();
                    entityObject.put("EntityID",entity.getId());
                    entityObject.put("locationx",entity.getLocation().x);
                    entityObject.put("locationy",entity.getLocation().y);
                    entityObject.put("entityName",entity.getName());
                    entityObject.put("orbitAngle",entity.getCircularOrbitAngle());
                    entityObject.put("orbitPeriod",entity.getCircularOrbitPeriod());
                    entityObject.put("orbitRadius",entity.getCircularOrbitRadius());
                    connectedEntities.put(entityObject);
                }
                marketJson.put("connectedEntities",connectedEntities);


                JSONArray conditions = new JSONArray();
                List<MarketConditionAPI> conditionsList = market.getConditions();
                for(MarketConditionAPI condition : conditionsList){
                    if(!Objects.equals(condition.getId(), "pather_cells") && !Objects.equals(condition.getId(), "pirate_activity")){                //todo fix pather cells && pirate activities
                        conditions.put(condition.getId());                                                                                                //both pirate and pather activity require pirate / pather intel object, which seems like a pain to implement
                    }                                                                                                                                     //so since I cannot be bothered they get removed client, side then the client readds them if needed, i you try to push these 2 conditions the client will crash

                }
                marketJson.put("conditions",conditions);

                JSONArray industries = new JSONArray();
                List<Industry> industriesList = market.getIndustries();
                for(Industry industry : industriesList){
                    industries.put(industry.getId());
                }
                marketJson.put("industries",industries);

                //todo submarkets

                //
                //todo commodities

                //
                markets.put(marketJson);
            }
        }
         message.put("markets",markets);
        Server serverInstance = (Server) MultiplayerModPlugin.getMessageSender();
        serverInstance.sendMessage(message.toString());
        serverInstance.replyTo(message.toString(), playerId);
    }
}