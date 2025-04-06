package matlabmaster.multiplayer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.campaign.*;
import com.fs.starfarer.combat.entities.terrain.Planet;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server implements MessageSender, MessageReceiver {
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private volatile boolean isRunning;
    private List<ClientConnection> clientConnections; // Updated to store full connection info
    private MessageReceiver messageHandler;

    // Inner class to store client connection details
    private static class ClientConnection {
        PrintWriter writer;
        Socket socket;
        BufferedReader reader;

        ClientConnection(PrintWriter writer, Socket socket, BufferedReader reader) {
            this.writer = writer;
            this.socket = socket;
            this.reader = reader;
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
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            ClientConnection connection = new ClientConnection(out, clientSocket, in);
            synchronized (clientConnections) {
                clientConnections.add(connection);
            }

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                LOGGER.log(Level.DEBUG, "Received from client " + clientSocket.getInetAddress() + ": " + inputLine);
                if (messageHandler != null) {
                    messageHandler.onMessageReceived(inputLine); // Immediate callback
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.ERROR, "Client connection error: " + e.getMessage());
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
            messageHandler.onMessageReceived(message); // Delegate to handler
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

    // Method to disconnect a specific client
    private void disconnectClient(ClientConnection connection) {
        synchronized (clientConnections) {
            if (clientConnections.contains(connection)) {
                try {
                    connection.writer.println("Disconnected: Seed mismatch");
                    closeQuietly(connection.writer);
                    closeQuietly(connection.reader);
                    connection.socket.close();
                    clientConnections.remove(connection);
                    LOGGER.log(Level.INFO, "Disconnected client " + connection.socket.getInetAddress() + " due to seed mismatch");
                } catch (IOException e) {
                    LOGGER.log(Level.ERROR, "Error disconnecting client: " + e.getMessage());
                }
            }
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
        // ... (unchanged from your version)
        MessageSender sender = MultiplayerModPlugin.getMessageSender();
        if (sender != null && sender.isActive()) {
            try {
                JSONObject message = new JSONObject();
                message.put("playerId", "server");
                message.put("command", 4);
                JSONObject systemsObject = new JSONObject();
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
        MessageSender sender = MultiplayerModPlugin.getMessageSender();
        message.put("command", 0);
        message.put("playerId", "server");
        message.put("seed", Global.getSector().getSeedString());

        String clientSeed = data.getString("seed");
        Server serverInstance = (Server) sender; // Cast to access instance methods

        if (!Objects.equals(clientSeed, Global.getSector().getSeedString())) {
            // Find and disconnect the client that sent this handshake
            synchronized (serverInstance.clientConnections) {
                for (ClientConnection conn : serverInstance.clientConnections) {
                    // We need a way to identify the client; assuming handshake is first message
                    // In a real scenario, you'd need client ID or socket info in the JSON
                    conn.writer.println(message.toString()); // Send seed info first
                    serverInstance.disconnectClient(conn);
                    break; // Assuming only one client sends handshake at a time
                }
            }
        } else {
            sender.sendMessage(message.toString()); // Normal handshake response
        }
    }
}