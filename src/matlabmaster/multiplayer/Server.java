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
import org.lwjgl.Sys;

import java.io.*;
import java.lang.String;
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
        //todo sync warning beacons
        //todo sync corona
        MessageSender sender = MultiplayerModPlugin.getMessageSender();
        if (sender != null && sender.isActive()) {
            try {
                JSONObject message = new JSONObject();
                message.put("playerId", "server");
                message.put("command", 4);
                JSONObject systemsObject = new JSONObject();
                SectorAPI sector = Global.getSector();
                //step 1 get systems
                List<StarSystemAPI>rawSystemData = sector.getStarSystems();
                JSONArray systemList = new JSONArray();
                //create system
                for (StarSystemAPI system : rawSystemData){
                    //deep space is gate hauler system and appears to work differently than other systems
                    if(Objects.equals(system.getId(), "deep space")){
                        continue;
                    }
                    JSONObject systemData = new JSONObject();
                    systemData.put("systemID", system.getId());
                    systemData.put("type", system.getType());
                    systemData.put("age", system.getAge());
                    //not all systems are in a constellation
                    try{
                        systemData.put("constellation", system.getConstellation().getName());
                        systemData.put("constellationType", system.getConstellation().getType().toString());
                    }catch (NullPointerException e){
                    }
                    systemData.put("locationx", system.getLocation().x);
                    systemData.put("locationy", system.getLocation().y);
                    //WARNING for center, must create planets in system before assigning centers
                    //if the center is not a planet then create a newBaseLocation with at coords 0 0 (always)
                    //else just set the center with the planetID
                    systemData.put("centerid", system.getCenter().getId());
                    if(system.getStar() != null){
                        systemData.put("firstStar", system.getStar().getId());
                    }
                    if(system.getSecondary() != null){
                        systemData.put("secondStar", system.getSecondary().getId());
                    }
                    if(system.getTertiary() != null){
                        systemData.put("thirdStar", system.getTertiary().getId());
                    }
                    //now we have all the data for creating a sector object

                    //time to add the planets
                    JSONArray planetList = new JSONArray();
                    List<PlanetAPI> rawPlanetsData = system.getPlanets();
                    for(PlanetAPI planet : rawPlanetsData){
                        JSONObject planetData = new JSONObject();
                        planetData.put("planetid", planet.getId());
                        planetData.put("type", planet.getTypeId());
                        planetData.put("name",planet.getName());
                        planetData.put("locationx",planet.getLocation().x);
                        planetData.put("locationy",planet.getLocation().y);
                        planetData.put("orbitAngle",planet.getCircularOrbitAngle());
                        planetData.put("orbitPeriod",planet.getCircularOrbitPeriod());
                        planetData.put("orbitRadius",planet.getCircularOrbitRadius());
                        try {
                            planetData.put("orbitFocusId",planet.getOrbitFocus().getId());
                        }catch (Exception e){
                            //no orbit focus
                        }
                        planetData.put("radius",planet.getRadius());
                        planetData.put("isStar",planet.isStar());
                        if(planet.isStar()){
                            planetData.put("hyperspaceLocationX", planet.getLocationInHyperspace().x);
                            planetData.put("hyperspaceLocationY", planet.getLocationInHyperspace().y);
                            planetData.put("coronaSize",planet.getSpec().getCoronaSize());
                        }
                        planetList.put(planetData);
                    }
                    systemData.put("planets",planetList);
                    systemList.put(systemData);

                }


                //sync hyperspace
                JSONArray gravityWells = new JSONArray();
                JSONArray locationTokens = new JSONArray(); // system anchors ???? i don't know what thoses do
                JSONArray jumpPoints = new JSONArray();
                JSONArray customCampaignEntities = new JSONArray();//things like warning beacon
                JSONArray campaignTerrains = new JSONArray();//slipstream
                List<SectorEntityToken> hyperspaceEntities = sector.getHyperspace().getAllEntities();
                for(SectorEntityToken entity : hyperspaceEntities){
                    if(entity.getClass() == JumpPoint.class){
                       JSONObject jumpPoint = new JSONObject();
                       jumpPoint.put("destination", ((JumpPoint) entity).getDestinations().get(0));

                    } else if (entity.getClass() == NascentGravityWellAPI.class) {
                        
                    } else if (entity.getClass() == BaseLocation.LocationToken.class) {
                        
                    } else if (entity.getClass() == CustomCampaignEntity.class) {
                        
                    } else if (entity.getClass() == CampaignTerrain.class) {

                    }
                }
                message.put("systems",systemList);
                //System.out.println(message.toString(4));
                sender.sendMessage(message.toString());

            } catch (JSONException e) {
                LOGGER.log(Level.ERROR, "sendStarscapeUpdate : Failed to construct JSON message: " + e.getMessage());
            }
        }
    }
}