package matlabmaster.multiplayer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Client implements MessageSender, MessageReceiver {
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean isRunning;
    private Thread listenerThread;
    private MessageReceiver messageHandler;

    public Client(String serverIp, int serverPort, MessageReceiver handler) {
        this.messageHandler = handler;
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
                        messageHandler.onMessageReceived(response);
                    }
                }
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
        return isRunning && socket != null && !socket.isClosed();
    }

    public void stop() {
        isRunning = false;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
            if (listenerThread != null) listenerThread.interrupt();
            LOGGER.log(Level.INFO, "Client stopped");
        } catch (IOException e) {
            LOGGER.log(Level.ERROR, "Error stopping client: " + e.getMessage());
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
                    Global.getSector().getEntityById(id).setCircularOrbitAngle(angle);
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
        // Extract the StarscapeData from the message
        JSONObject starscapeData = data.getJSONObject("StarscapeData");

        SectorAPI sector = Global.getSector();
        List<StarSystemAPI> currentSystems = sector.getStarSystems();
        List<String> jsonSystemIds = new ArrayList<>();

        // Collect all system IDs from JSON data
        Iterator<String> jsonKeys = starscapeData.keys();
        while (jsonKeys.hasNext()) {
            String systemName = jsonKeys.next();
            JSONObject systemData = starscapeData.getJSONObject(systemName);
            String systemId = systemData.getString("id");
            jsonSystemIds.add(systemId);
        }

        // Step 1: Remove systems that exist in the game but not in JSON
        Iterator<StarSystemAPI> systemIterator = currentSystems.iterator();
        while (systemIterator.hasNext()) {
            StarSystemAPI system = systemIterator.next();
            if (!jsonSystemIds.contains(system.getId())) {
                sector.removeStarSystem(system);
                systemIterator.remove(); // Safely remove from the list
                Global.getLogger(Client.class).info("Removed system: " + system.getId());
            }
        }

        // Step 2 & 3: Add missing systems from JSON and skip existing ones
        jsonKeys = starscapeData.keys(); // Reset iterator
        while (jsonKeys.hasNext()) {
            String systemName = jsonKeys.next();
            JSONObject systemData = starscapeData.getJSONObject(systemName);
            String systemId = systemData.getString("id");

            // Check if the system already exists
            boolean systemExists = false;
            for (StarSystemAPI system : currentSystems) {
                if (system.getId().equals(systemId)) {
                    systemExists = true;
                    break;
                }
            }

            if (!systemExists) {
                // Create new system
                StarSystemAPI newSystem = sector.createStarSystem(systemId);
                newSystem.setName(systemName);

                // Set hyperspace coordinates
                JSONObject hyperspaceCoords = systemData.getJSONObject("hyperspaceCoordinates");
                newSystem.getLocation().set((float) hyperspaceCoords.getDouble("x"), (float) hyperspaceCoords.getDouble("y"));

                // Add planets (including stars)
                JSONArray planetsArray = systemData.getJSONArray("planets");
                PlanetAPI star = null; // Track the star for orbits
                for (int i = 0; i < planetsArray.length(); i++) {
                    JSONObject planetData = planetsArray.getJSONObject(i);
                    String planetId = planetData.getString("id");
                    String type = planetData.getString("type");
                    float size = (float) planetData.getDouble("size");
                    float orbitRadius = (float) planetData.getDouble("orbitRadius");
                    float orbitAngle = (float) planetData.getDouble("orbitAngle");
                    float orbitPeriod = (float) planetData.getDouble("orbitPeriod");
                    boolean isStar = planetData.getBoolean("isStar");

                    PlanetAPI planet;
                    if (isStar) {
                        planet = newSystem.initStar(planetId, type, size, orbitRadius);
                        star = planet; // Save star for orbiting planets
                    } else {
                        planet = newSystem.addPlanet(planetId, star, planetData.getString("name"), type, orbitAngle, size, orbitRadius, orbitPeriod);
                    }
                }

                // Add jump points
                //JSONArray jumpPointsArray = systemData.getJSONArray("jumpPoints");
                //for (int i = 0; i < jumpPointsArray.length(); i++) {
                //    JSONObject jumpPointData = jumpPointsArray.getJSONObject(i);
                //    String jumpPointId = jumpPointData.getString("id"); // Fixed typo "id?"
                //    String name = jumpPointData.getString("name");
                //    float x = (float) jumpPointData.getDouble("locationInSystemX");
                //    float y = (float) jumpPointData.getDouble("locationInSystemY");
//
                //    // Create jump point using addCustomEntity
                //    SectorEntityToken jumpPointEntity = newSystem.addCustomEntity(
                //            jumpPointId,           // ID
                //            name,                  // Name
                //            "jump_point",          // Entity type
                //            "neutral"                   // Faction
                //    );
                //    jumpPointEntity.getLocation().set(x, y);
//
                //    // Cast to JumpPointAPI and link destinations
                //    if (jumpPointEntity instanceof JumpPointAPI) {
                //        JumpPointAPI jumpPoint = (JumpPointAPI) jumpPointEntity;
                //        String destinationId = jumpPointData.getString("destinationSystemId");
                //        if (!destinationId.equals("none")) {
                //            for (StarSystemAPI destSystem : sector.getStarSystems()) {
                //                if (destSystem.getId().equals(destinationId)) {
                //                    SectorEntityToken destinationEntity = destSystem.getStar() != null ? destSystem.getStar() : destSystem.getCenter();
                //                    jumpPoint.addDestination(new JumpPointAPI.JumpDestination(destinationEntity, "Jump to " + destSystem.getName()));
                //                    break;
                //                }
                //            }
                //        }
                //    }
                //}

                Global.getLogger(Client.class).info("Added system: " + systemId);
            }
        }
    }
}