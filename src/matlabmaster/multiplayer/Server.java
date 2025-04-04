package matlabmaster.multiplayer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server implements MessageSender, MessageReceiver {
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private volatile boolean isRunning;
    private List<PrintWriter> clientWriters;
    private MessageReceiver messageHandler;

    public Server(int port, MessageReceiver handler) {
        this.messageHandler = handler;
        try {
            serverSocket = new ServerSocket(port);
            executor = Executors.newCachedThreadPool();
            clientWriters = new ArrayList<>();
            isRunning = true;
            LOGGER.log(org.apache.log4j.Level.INFO, "Server initialized on port " + port);
        } catch (IOException e) {
            LOGGER.log(org.apache.log4j.Level.ERROR, "Failed to start server on port " + port + ": " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void start() {
        LOGGER.log(org.apache.log4j.Level.INFO, "Server started on port " + serverSocket.getLocalPort());
        try {
            while (isRunning && !serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                LOGGER.log(org.apache.log4j.Level.INFO, "New client connected: " + clientSocket.getInetAddress());
                executor.execute(() -> handleClientConnection(clientSocket));
            }
        } catch (IOException e) {
            if (isRunning) {
                LOGGER.log(org.apache.log4j.Level.ERROR, "Server error: " + e.getMessage());
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

            synchronized (clientWriters) {
                clientWriters.add(out);
            }

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                LOGGER.log(org.apache.log4j.Level.DEBUG, "Received from client: " + inputLine);
                if (messageHandler != null) {
                    messageHandler.onMessageReceived(inputLine); // Immediate callback
                }
            }
        } catch (IOException e) {
            LOGGER.log(org.apache.log4j.Level.ERROR, "Client connection error: " + e.getMessage());
        } finally {
            if (out != null) {
                synchronized (clientWriters) {
                    clientWriters.remove(out);
                }
            }
            closeQuietly(in);
            closeQuietly(out);
            try {
                clientSocket.close();
            } catch (IOException e) {
                LOGGER.log(org.apache.log4j.Level.ERROR, "Error closing client socket: " + e.getMessage());
            }
        }
    }

    @Override
    public void sendMessage(String message) {
        if (!isRunning) {
            LOGGER.log(org.apache.log4j.Level.WARN, "Server not running, cannot send message: " + message);
            return;
        }
        synchronized (clientWriters) {
            for (PrintWriter writer : clientWriters) {
                writer.println(message);
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
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters) {
                    writer.close();
                }
                clientWriters.clear();
            }
            LOGGER.log(org.apache.log4j.Level.INFO, "Server stopped");
        } catch (IOException e) {
            LOGGER.log(org.apache.log4j.Level.ERROR, "Error stopping server: " + e.getMessage());
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

    public static void sendOrbitingBodiesUpdate(String systemName) throws JSONException {
        //client method
        MessageSender sender = MultiplayerModPlugin.getMessageSender();
        if (sender != null && sender.isActive()) {
            try{
                JSONObject message = new JSONObject();
                message.put("command", 6);
                message.put("playerId","server");
                LocationAPI system = Global.getSector().getPlayerFleet().getStarSystem();
                List<SectorEntityToken> stableLocations = system.getAllEntities();
                JSONArray toSync = new JSONArray();
                for(SectorEntityToken entity : stableLocations){
                    if(entity.getCustomEntityType() == "orbital_junk" || entity.getCustomEntityType() == "null") {
                        //don't sync useless / not important
                    }else{
                        JSONObject thing = new JSONObject();
                        thing.put("id", entity.getId());
                        thing.put("a",entity.getCircularOrbitAngle());
                        toSync.put(thing);
                    }
                }
                message.put("toSync",toSync);
                sender.sendMessage(message.toString());
            } catch (JSONException e) {
                LOGGER.log(Level.ERROR, "Failed to construct JSON message: " + e.getMessage());
            }
        }
    }
    public static void sendStarscapeUpdate(){
        MessageSender sender = MultiplayerModPlugin.getMessageSender();
        if (sender != null && sender.isActive()) {
            try{
                //Command boilerplate
                JSONObject message = new JSONObject();
                message.put("command", 4);
                message.put("playerId","server");

                //Actual Systems data
                JSONObject starscapeData = new JSONObject();

                // Get all star systems
                List<StarSystemAPI> systems = Global.getSector().getStarSystems();

                for (StarSystemAPI system : systems) {
                    JSONObject systemData = new JSONObject();

                    // System ID
                    systemData.put("id", system.getId());
                    systemData.put("coordx", system.getLocation().x);
                    systemData.put("coordy", system.getLocation().y);

                    // Planets (including stars)
                    JSONArray planetsArray = new JSONArray();
                    for (SectorEntityToken entity : system.getAllEntities()) {
                        if (entity instanceof PlanetAPI) {
                            PlanetAPI planet = (PlanetAPI) entity;
                            JSONObject planetData = new JSONObject();
                            planetData.put("name", planet.getName());
                            planetData.put("id", planet.getId());
                            planetData.put("size", planet.getRadius());
                            planetData.put("type", planet.getTypeId());
                            planetData.put("texture", planet.getCustomEntityType() != null ? planet.getCustomEntityType() : planet.getSpec().getTexture());
                            planetData.put("isStar", planet.isStar());
                            planetData.put("orbitPeriod", planet.getCircularOrbitPeriod());
                            planetData.put("orbitAngle", planet.getCircularOrbitAngle());
                            planetData.put("orbitRadius", planet.getCircularOrbitRadius());
                            planetsArray.put(planetData);
                        }
                    }
                    systemData.put("planets", planetsArray);

                    // Jump Points (Gravity Wells)
                    JSONArray jumpPointsArray = new JSONArray();
                    for (SectorEntityToken entity : system.getJumpPoints()) {
                        if (entity instanceof JumpPointAPI) {
                            JumpPointAPI jumpPoint = (JumpPointAPI) entity;
                            JSONObject jumpPointData = new JSONObject();
                            jumpPointData.put("name", jumpPoint.getName());
                            jumpPointData.put("id", jumpPoint.getId());
                            jumpPointData.put("destinationSystemId", jumpPoint.getDestinations().isEmpty() ? "none" : jumpPoint.getDestinationStarSystem());
                            jumpPointData.put("locationInSystemX", jumpPoint.getLocation().x);
                            jumpPointData.put("locationInSystemY", jumpPoint.getLocation().y);
                            jumpPointsArray.put(jumpPointData);
                        }
                    }
                    systemData.put("jumpPoints", jumpPointsArray);

                    // Hyperspace Coordinates
                    JSONObject hyperspaceCoords = new JSONObject();
                    hyperspaceCoords.put("x", system.getLocation().x);
                    hyperspaceCoords.put("y", system.getLocation().y);
                    systemData.put("hyperspaceCoordinates", hyperspaceCoords);

                    // Add system to root JSON object
                    starscapeData.put(system.getName(), systemData);

                    message.put("StarscapeData", starscapeData);

                }
                sender.sendMessage(message.toString());
            }catch (JSONException e) {
                LOGGER.log(Level.ERROR, "Failed to construct JSON message: " + e.getMessage());
            }
        }
    }
}